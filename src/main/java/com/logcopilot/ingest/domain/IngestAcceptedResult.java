package com.logcopilot.ingest.domain;

public record IngestAcceptedResult(
	boolean accepted,
	String ingestionId,
	int receivedEvents,
	int deduplicatedEvents
) {
	public IngestAcceptedResult {
		if (ingestionId == null || ingestionId.isBlank()) {
			throw new IllegalArgumentException("ingestionId must not be blank");
		}
		if (receivedEvents < 0 || deduplicatedEvents < 0) {
			throw new IllegalArgumentException("Event counts must be non-negative");
		}
		if (deduplicatedEvents > receivedEvents) {
			throw new IllegalArgumentException("deduplicatedEvents cannot exceed receivedEvents");
		}
	}
}
