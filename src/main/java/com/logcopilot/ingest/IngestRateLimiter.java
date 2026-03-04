package com.logcopilot.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class IngestRateLimiter {

	private final int maxRequests;
	private final Duration window;
	private final Clock clock;
	private final Map<String, RateWindowState> stateByKey = new HashMap<>();

	@Autowired
	public IngestRateLimiter(
		@Value("${logcopilot.ingest.rate-limit.max-requests:120}") int maxRequests,
		@Value("${logcopilot.ingest.rate-limit.window:PT1M}") Duration window
	) {
		this(maxRequests, window, Clock.systemUTC());
	}

	IngestRateLimiter(int maxRequests, Duration window, Clock clock) {
		if (maxRequests < 1) {
			throw new IllegalArgumentException("maxRequests must be >= 1");
		}
		if (window == null || window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException("window must be positive");
		}
		this.maxRequests = maxRequests;
		this.window = window;
		this.clock = clock;
	}

	public synchronized boolean tryAcquire(String key) {
		return tryAcquire(key, null);
	}

	public synchronized boolean tryAcquire(String key, String requestKey) {
		String normalizedKey = normalizeKey(key);
		Instant now = clock.instant();
		RateWindowState state = stateByKey.get(normalizedKey);
		if (state == null || isWindowExpired(state.windowStart(), now)) {
			state = new RateWindowState(now, 0, new HashSet<>());
		}

		String normalizedRequestKey = normalizeRequestKey(requestKey);
		if (normalizedRequestKey != null && state.seenRequestKeys().contains(normalizedRequestKey)) {
			stateByKey.put(normalizedKey, state);
			return true;
		}

		if (state.count() >= maxRequests) {
			stateByKey.put(normalizedKey, state);
			return false;
		}

		Set<String> seenRequestKeys = new HashSet<>(state.seenRequestKeys());
		if (normalizedRequestKey != null) {
			seenRequestKeys.add(normalizedRequestKey);
		}
		RateWindowState updated = new RateWindowState(state.windowStart(), state.count() + 1, seenRequestKeys);
		stateByKey.put(normalizedKey, updated);
		return true;
	}

	private boolean isWindowExpired(Instant windowStart, Instant now) {
		return !now.isBefore(windowStart.plus(window));
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

	private record RateWindowState(Instant windowStart, int count, Set<String> seenRequestKeys) {
	}
}
