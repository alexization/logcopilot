package com.logcopilot.ingest.domain;

import java.util.List;

public record IngestEventsCommand(
	String projectId,
	String source,
	String batchId,
	List<CanonicalLogEvent> events
) {
}
