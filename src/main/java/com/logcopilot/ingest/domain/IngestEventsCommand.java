package com.logcopilot.ingest.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record IngestEventsCommand(
	String projectId,
	String source,
	String batchId,
	List<CanonicalLogEvent> events
) {
	public IngestEventsCommand {
		if (events == null) {
			events = List.of();
		} else {
			events = Collections.unmodifiableList(new ArrayList<>(events));
		}
	}
}
