package com.logcopilot.ingest;

import com.logcopilot.ingest.domain.IngestAcceptedResult;

import java.util.Optional;

public interface IngestIdempotencyStore {

	Optional<IngestAcceptedResult> find(String idempotencyKey);

	void save(String idempotencyKey, IngestAcceptedResult acceptedResult);
}
