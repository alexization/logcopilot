package com.logcopilot.ingest;

import com.logcopilot.ingest.domain.IngestAcceptedResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class InMemoryIngestIdempotencyStore implements IngestIdempotencyStore {

	private static final int DEFAULT_MAX_SIZE = 10_000;

	private final int maxSize;
	private final Map<String, IngestAcceptedResult> acceptedByIdempotencyKey;

	public InMemoryIngestIdempotencyStore() {
		this(DEFAULT_MAX_SIZE);
	}

	InMemoryIngestIdempotencyStore(int maxSize) {
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
}
