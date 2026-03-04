package com.logcopilot.ingest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class IngestRateLimiter {

	private static final int DEFAULT_MAX_KEYS = 10_000;
	private static final Duration DEFAULT_ENTRY_TTL = Duration.ofHours(1);

	private final int maxRequests;
	private final Duration window;
	private final int maxKeys;
	private final Duration entryTtl;
	private final Clock clock;
	private final ConcurrentMap<String, RateWindowState> stateByKey = new ConcurrentHashMap<>();

	@Autowired
	public IngestRateLimiter(
		@Value("${logcopilot.ingest.rate-limit.max-requests:120}") int maxRequests,
		@Value("${logcopilot.ingest.rate-limit.window:PT1M}") Duration window,
		@Value("${logcopilot.ingest.rate-limit.max-keys:10000}") int maxKeys,
		@Value("${logcopilot.ingest.rate-limit.entry-ttl:PT1H}") Duration entryTtl
	) {
		this(maxRequests, window, maxKeys, entryTtl, Clock.systemUTC());
	}

	IngestRateLimiter(int maxRequests, Duration window, Clock clock) {
		this(maxRequests, window, DEFAULT_MAX_KEYS, DEFAULT_ENTRY_TTL, clock);
	}

	IngestRateLimiter(
		int maxRequests,
		Duration window,
		int maxKeys,
		Duration entryTtl,
		Clock clock
	) {
		if (maxRequests < 1) {
			throw new IllegalArgumentException("maxRequests must be >= 1");
		}
		if (window == null || window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException("window must be positive");
		}
		if (maxKeys < 1) {
			throw new IllegalArgumentException("maxKeys must be >= 1");
		}
		if (entryTtl == null || entryTtl.isZero() || entryTtl.isNegative()) {
			throw new IllegalArgumentException("entryTtl must be positive");
		}
		this.maxRequests = maxRequests;
		this.window = window;
		this.maxKeys = maxKeys;
		this.entryTtl = entryTtl;
		this.clock = clock;
	}

	public AcquireResult tryAcquire(String key) {
		return tryAcquire(key, null);
	}

	public AcquireResult tryAcquire(String key, String requestKey) {
		Instant now = clock.instant();
		evictExpiredEntries(now);

		String normalizedKey = normalizeKey(key);
		String normalizedRequestKey = normalizeRequestKey(requestKey);
		AtomicReference<AcquireResult> resultRef = new AtomicReference<>(new AcquireResult(true, 0));

		stateByKey.compute(normalizedKey, (ignored, existing) -> {
			RateWindowState state = existing;
			if (state == null || isStateResetRequired(state, now)) {
				state = RateWindowState.empty(now);
			}

			if (normalizedRequestKey != null && state.seenRequestKeys().contains(normalizedRequestKey)) {
				resultRef.set(new AcquireResult(true, 0));
				return state.touch(now);
			}

			if (state.count() >= maxRequests) {
				long retryAfter = retryAfterSeconds(state.windowStart(), now);
				resultRef.set(new AcquireResult(false, retryAfter));
				return state.touch(now);
			}

			Set<String> seenRequestKeys = state.mutableSeenRequestKeys();
			if (normalizedRequestKey != null) {
				seenRequestKeys.add(normalizedRequestKey);
			}
			resultRef.set(new AcquireResult(true, 0));
			return new RateWindowState(state.windowStart(), state.count() + 1, seenRequestKeys, now);
		});

		evictOverflowEntries();
		return resultRef.get();
	}

	private void evictExpiredEntries(Instant now) {
		stateByKey.entrySet().removeIf(entry -> isEntryExpired(entry.getValue(), now));
	}

	private void evictOverflowEntries() {
		if (stateByKey.size() <= maxKeys) {
			return;
		}

		int overflow = stateByKey.size() - maxKeys;
		for (int index = 0; index < overflow; index++) {
			String oldestKey = findOldestKey();
			if (oldestKey == null) {
				return;
			}
			stateByKey.remove(oldestKey);
		}
	}

	private String findOldestKey() {
		String oldestKey = null;
		Instant oldestSeen = null;
		for (var entry : stateByKey.entrySet()) {
			Instant candidateSeen = entry.getValue().lastSeen();
			if (oldestSeen == null || candidateSeen.isBefore(oldestSeen)) {
				oldestSeen = candidateSeen;
				oldestKey = entry.getKey();
			}
		}
		return oldestKey;
	}

	private boolean isStateResetRequired(RateWindowState state, Instant now) {
		return isWindowExpired(state.windowStart(), now) || isEntryExpired(state, now);
	}

	private boolean isEntryExpired(RateWindowState state, Instant now) {
		return !now.isBefore(state.lastSeen().plus(entryTtl));
	}

	private boolean isWindowExpired(Instant windowStart, Instant now) {
		return !now.isBefore(windowStart.plus(window));
	}

	private long retryAfterSeconds(Instant windowStart, Instant now) {
		Instant resetAt = windowStart.plus(window);
		if (!now.isBefore(resetAt)) {
			return 1L;
		}
		long seconds = Duration.between(now, resetAt).toSeconds();
		return Math.max(1L, seconds);
	}

	private String normalizeKey(String key) {
		if (key == null || key.isBlank()) {
			return "ingest:anonymous";
		}
		return key.trim();
	}

	private String normalizeRequestKey(String requestKey) {
		if (requestKey == null || requestKey.isBlank()) {
			return null;
		}
		return requestKey.trim();
	}

	public record AcquireResult(boolean allowed, long retryAfterSeconds) {
	}

	private record RateWindowState(
		Instant windowStart,
		int count,
		Set<String> seenRequestKeys,
		Instant lastSeen
	) {
		private RateWindowState {
			seenRequestKeys = Set.copyOf(seenRequestKeys);
		}

		private static RateWindowState empty(Instant now) {
			return new RateWindowState(now, 0, Set.of(), now);
		}

		private Set<String> mutableSeenRequestKeys() {
			return new HashSet<>(seenRequestKeys);
		}

		private RateWindowState touch(Instant now) {
			return new RateWindowState(windowStart, count, seenRequestKeys, now);
		}
	}
}
