package com.logcopilot.ingest.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
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
		if (attributes == null) {
			attributes = Map.of();
		} else {
			attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
		}
	}
}
