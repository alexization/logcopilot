package com.logcopilot.ingest;

import com.logcopilot.ingest.domain.IngestAcceptedResult;

import java.util.function.Function;
import java.util.Optional;

public interface IngestIdempotencyStore {

	Optional<IngestAcceptedResult> find(String idempotencyKey);

	void save(String idempotencyKey, IngestAcceptedResult acceptedResult);

	IngestAcceptedResult computeIfAbsent(String idempotencyKey, Function<String, IngestAcceptedResult> mapper);
}
