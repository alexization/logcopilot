package com.logcopilot.ingest;

import com.logcopilot.common.error.NotImplementedException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.project.ProjectService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class IngestService {

	private static final Set<String> ALLOWED_SOURCES = Set.of("loki", "otlp", "custom");
	private static final Set<String> ALLOWED_SEVERITIES = Set.of("debug", "info", "warn", "error", "fatal");

	private final ProjectService projectService;
	private final Map<String, IngestAcceptedData> acceptedByIdempotencyKey = new HashMap<>();

	public IngestService(ProjectService projectService) {
		this.projectService = projectService;
	}

	public synchronized IngestAcceptedData ingestEvents(String idempotencyKey, IngestEventsRequest request) {
		IngestAcceptedData existing = acceptedByIdempotencyKey.get(idempotencyKey);
		if (existing != null) {
			return existing;
		}

		validateRequest(request);
		int receivedEvents = request.events().size();
		int deduplicatedEvents = receivedEvents - (int) request.events().stream()
			.map(CanonicalLogEvent::eventId)
			.distinct()
			.count();

		IngestAcceptedData accepted = new IngestAcceptedData(
			true,
			UUID.randomUUID().toString(),
			receivedEvents,
			deduplicatedEvents
		);
		acceptedByIdempotencyKey.put(idempotencyKey, accepted);
		return accepted;
	}

	public IngestAcceptedData ingestOtlpLogs(String idempotencyKey, byte[] payload) {
		throw new NotImplementedException("OTLP ingest endpoint is reserved in MVP");
	}

	private void validateRequest(IngestEventsRequest request) {
		if (request.projectId() == null || request.projectId().isBlank() || !projectService.existsById(request.projectId())) {
			throw new ValidationException("project_id must reference an existing project");
		}
		if (!ALLOWED_SOURCES.contains(request.source())) {
			throw new ValidationException("source must be one of: loki, otlp, custom");
		}
		if (request.batchId() == null || request.batchId().isBlank()) {
			throw new ValidationException("batch_id must not be blank");
		}
		if (request.events() == null || request.events().isEmpty() || request.events().size() > 5000) {
			throw new ValidationException("events size must be between 1 and 5000");
		}
		for (CanonicalLogEvent event : request.events()) {
			if (event == null) {
				throw new ValidationException("events must not contain null items");
			}
			validateEvent(event);
		}
	}

	private void validateEvent(CanonicalLogEvent event) {
		if (event.eventId() == null || event.eventId().isBlank()) {
			throw new ValidationException("event_id must not be blank");
		}
		if (event.service() == null || event.service().isBlank()) {
			throw new ValidationException("service must not be blank");
		}
		if (!ALLOWED_SEVERITIES.contains(event.severity())) {
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

	public record IngestEventsRequest(
		String projectId,
		String source,
		String batchId,
		List<CanonicalLogEvent> events
	) {
	}

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
	}

	public record IngestAcceptedData(
		boolean accepted,
		String ingestionId,
		int receivedEvents,
		int deduplicatedEvents
	) {
	}
}
