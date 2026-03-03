package com.logcopilot.ingest.domain;

import com.logcopilot.common.error.ValidationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class IngestRequestValidator {

	public void validate(IngestEventsCommand request, boolean projectExists) {
		if (request == null) {
			throw new ValidationException("ingest request must not be null");
		}
		if (request.projectId() == null || request.projectId().isBlank() || !projectExists) {
			throw new ValidationException("project_id must reference an existing project");
		}
		if (!IngestSource.isSupported(request.source())) {
			throw new ValidationException("source must be one of: loki, otlp, custom");
		}
		if (request.batchId() == null || request.batchId().isBlank()) {
			throw new ValidationException("batch_id must not be blank");
		}
		if (request.events() == null || request.events().isEmpty() || request.events().size() > 5000) {
			throw new ValidationException("events size must be between 1 and 5000");
		}

		for (CanonicalLogEvent event : request.events()) {
			validateEvent(event);
		}
	}

	private void validateEvent(CanonicalLogEvent event) {
		if (event == null) {
			throw new ValidationException("events must not contain null items");
		}
		if (event.eventId() == null || event.eventId().isBlank()) {
			throw new ValidationException("event_id must not be blank");
		}
		if (event.service() == null || event.service().isBlank()) {
			throw new ValidationException("service must not be blank");
		}
		if (!LogSeverity.isSupported(event.severity())) {
			throw new ValidationException("severity must be one of: debug, info, warn, error, fatal");
		}
		if (event.message() == null || event.message().isBlank()) {
			throw new ValidationException("message must not be blank");
		}
		if (event.timestamp() == null || event.timestamp().isBlank()) {
			throw new ValidationException("timestamp must be RFC3339 date-time");
		}

		try {
			Instant.parse(event.timestamp());
		} catch (DateTimeParseException exception) {
			throw new ValidationException("timestamp must be RFC3339 date-time");
		}
	}
}
