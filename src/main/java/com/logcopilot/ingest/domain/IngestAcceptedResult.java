package com.logcopilot.ingest.domain;

public record IngestAcceptedResult(
	boolean accepted,
	String ingestionId,
	int receivedEvents,
	int deduplicatedEvents
) {
}
