package com.logcopilot.ingest.domain;

import java.util.List;

public record IngestEventsCommand(
	String projectId,
	String source,
	String batchId,
	List<CanonicalLogEvent> events
) {
	public IngestEventsCommand {
		events = events == null ? List.of() : List.copyOf(events);
	}
}
