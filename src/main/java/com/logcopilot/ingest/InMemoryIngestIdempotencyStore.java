package com.logcopilot.ingest;

import com.logcopilot.ingest.domain.IngestAcceptedResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Component
public class InMemoryIngestIdempotencyStore implements IngestIdempotencyStore {

	private static final int DEFAULT_MAX_SIZE = 10_000;

	private final int maxSize;
	private final Map<String, IngestAcceptedResult> acceptedByIdempotencyKey;

	public InMemoryIngestIdempotencyStore() {
		this(DEFAULT_MAX_SIZE);
	}

	InMemoryIngestIdempotencyStore(int maxSize) {
		if (maxSize < 1) {
			throw new IllegalArgumentException("maxSize must be >= 1");
		}
		this.maxSize = maxSize;
		this.acceptedByIdempotencyKey = new LinkedHashMap<>() {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, IngestAcceptedResult> eldest) {
				return size() > InMemoryIngestIdempotencyStore.this.maxSize;
			}
		};
	}

	@Override
	public synchronized Optional<IngestAcceptedResult> find(String idempotencyKey) {
		return Optional.ofNullable(acceptedByIdempotencyKey.get(idempotencyKey));
	}

	@Override
	public synchronized void save(String idempotencyKey, IngestAcceptedResult acceptedResult) {
		acceptedByIdempotencyKey.put(idempotencyKey, acceptedResult);
	}

	@Override
	public synchronized IngestAcceptedResult computeIfAbsent(
		String idempotencyKey,
		Function<String, IngestAcceptedResult> mapper
	) {
		IngestAcceptedResult existing = acceptedByIdempotencyKey.get(idempotencyKey);
		if (existing != null) {
			return existing;
		}

		IngestAcceptedResult computed = mapper.apply(idempotencyKey);
		acceptedByIdempotencyKey.put(idempotencyKey, computed);
		return computed;
	}
}
