package com.logcopilot.ingest.domain;

import java.util.Map;

public record CanonicalLogEvent(
	String eventId,
	String timestamp,
	String service,
	String severity,
	String message,
	String traceId,
	String errorCode,
	String stackTrace,
	Map<String, Object> attributes
) {
	public CanonicalLogEvent {
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
	}
}
