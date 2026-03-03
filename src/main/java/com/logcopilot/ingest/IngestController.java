package com.logcopilot.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.error.BadRequestException;
import com.logcopilot.common.error.UnauthorizedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/ingest")
public class IngestController {

	private final IngestService ingestService;

	public IngestController(IngestService ingestService) {
		this.ingestService = ingestService;
	}

	@PostMapping("/events")
	public ResponseEntity<IngestAcceptedResponse> ingestEvents(
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody IngestEventsRequest request
	) {
		validateBearerToken(authorization);
		String validatedIdempotencyKey = validateIdempotencyKey(idempotencyKey);
		validateRequestBody(request);

		IngestService.IngestAcceptedData accepted = ingestService.ingestEvents(
			validatedIdempotencyKey,
			toServiceRequest(request)
		);
		return ResponseEntity.accepted().body(toResponse(accepted));
	}

	@PostMapping(value = "/otlp/logs", consumes = "application/x-protobuf")
	public ResponseEntity<IngestAcceptedResponse> ingestOtlpLogs(
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody(required = false) byte[] payload
	) {
		validateBearerToken(authorization);
		String validatedIdempotencyKey = validateIdempotencyKey(idempotencyKey);

		IngestService.IngestAcceptedData accepted = ingestService.ingestOtlpLogs(validatedIdempotencyKey, payload);
		return ResponseEntity.accepted().body(toResponse(accepted));
	}

	private IngestService.IngestEventsRequest toServiceRequest(IngestEventsRequest request) {
		return new IngestService.IngestEventsRequest(
			request.projectId(),
			request.source(),
			request.batchId(),
			toServiceEvents(request.events())
		);
	}

	private List<IngestService.CanonicalLogEvent> toServiceEvents(List<CanonicalLogEventRequest> eventRequests) {
		if (eventRequests == null) {
			return null;
		}

		List<IngestService.CanonicalLogEvent> events = new ArrayList<>();
		for (CanonicalLogEventRequest event : eventRequests) {
			if (event == null) {
				events.add(null);
				continue;
			}

			events.add(new IngestService.CanonicalLogEvent(
				event.eventId(),
				event.timestamp(),
				event.service(),
				event.severity(),
				event.message(),
				event.traceId(),
				event.errorCode(),
				event.stackTrace(),
				event.attributes()
			));
		}

		return events;
	}

	private IngestAcceptedResponse toResponse(IngestService.IngestAcceptedData accepted) {
		return new IngestAcceptedResponse(
			new IngestAcceptedData(
				accepted.accepted(),
				accepted.ingestionId(),
				accepted.receivedEvents(),
				accepted.deduplicatedEvents()
			)
		);
	}

	private void validateBearerToken(String authorization) {
		if (authorization == null) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		String[] parts = authorization.trim().split("\\s+", 2);
		if (parts.length != 2 || !"bearer".equalsIgnoreCase(parts[0]) || parts[1].isBlank()) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}
	}

	private String validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BadRequestException("Idempotency-Key header is required");
		}
		return idempotencyKey.trim();
	}

	private void validateRequestBody(IngestEventsRequest request) {
		if (request == null) {
			throw new BadRequestException("Malformed JSON request body");
		}
	}

	public record IngestEventsRequest(
		@JsonProperty("project_id")
		String projectId,
		String source,
		@JsonProperty("batch_id")
		String batchId,
		List<CanonicalLogEventRequest> events
	) {
	}

	public record CanonicalLogEventRequest(
		@JsonProperty("event_id")
		String eventId,
		String timestamp,
		String service,
		String severity,
		String message,
		@JsonProperty("trace_id")
		String traceId,
		@JsonProperty("error_code")
		String errorCode,
		@JsonProperty("stack_trace")
		String stackTrace,
		Map<String, Object> attributes
	) {
	}

	public record IngestAcceptedResponse(IngestAcceptedData data) {
	}

	public record IngestAcceptedData(
		boolean accepted,
		@JsonProperty("ingestion_id")
		String ingestionId,
		@JsonProperty("received_events")
		int receivedEvents,
		@JsonProperty("deduplicated_events")
		int deduplicatedEvents
	) {
	}
}
