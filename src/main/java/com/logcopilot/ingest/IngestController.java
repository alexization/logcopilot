package com.logcopilot.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.http.IdempotencyKeyValidator;
import com.logcopilot.common.error.TooManyRequestsException;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.ingest.domain.IngestAcceptedResult;
import com.logcopilot.ingest.domain.IngestEventsCommand;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Validator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/v1/ingest")
public class IngestController {

	private final IngestService ingestService;
	private final IdempotencyKeyValidator idempotencyKeyValidator;
	private final Validator validator;
	private final IngestRateLimiter ingestRateLimiter;

	public IngestController(
		IngestService ingestService,
		IdempotencyKeyValidator idempotencyKeyValidator,
		Validator validator,
		IngestRateLimiter ingestRateLimiter
	) {
		this.ingestService = ingestService;
		this.idempotencyKeyValidator = idempotencyKeyValidator;
		this.validator = validator;
		this.ingestRateLimiter = ingestRateLimiter;
	}

	@PostMapping("/events")
	public ResponseEntity<IngestAcceptedResponse> ingestEvents(
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody IngestEventsRequest request,
		Authentication authentication
	) {
		enforceIngestRateLimit(authentication, idempotencyKey);
		String validatedIdempotencyKey = idempotencyKeyValidator.validateRequired(idempotencyKey);
		validateBeanConstraints(request);

		IngestAcceptedResult accepted = ingestService.ingestEvents(
			validatedIdempotencyKey,
			toServiceRequest(request)
		);
		return ResponseEntity.accepted().body(toResponse(accepted));
	}

	@PostMapping(value = "/otlp/logs", consumes = "application/x-protobuf")
	public ResponseEntity<IngestAcceptedResponse> ingestOtlpLogs(
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody(required = false) byte[] payload,
		Authentication authentication
	) {
		enforceIngestRateLimit(authentication, idempotencyKey);
		String validatedIdempotencyKey = idempotencyKeyValidator.validateRequired(idempotencyKey);

		IngestAcceptedResult accepted = ingestService.ingestOtlpLogs(validatedIdempotencyKey, payload);
		return ResponseEntity.accepted().body(toResponse(accepted));
	}

	private IngestEventsCommand toServiceRequest(IngestEventsRequest request) {
		return new IngestEventsCommand(
			request.projectId(),
			request.source(),
			request.batchId(),
			toServiceEvents(request.events())
		);
	}

	private List<CanonicalLogEvent> toServiceEvents(List<CanonicalLogEventRequest> eventRequests) {
		if (eventRequests == null) {
			return null;
		}

		List<CanonicalLogEvent> events = new ArrayList<>();
		for (CanonicalLogEventRequest event : eventRequests) {
			if (event == null) {
				events.add(null);
				continue;
			}

			events.add(new CanonicalLogEvent(
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

	private IngestAcceptedResponse toResponse(IngestAcceptedResult accepted) {
		return new IngestAcceptedResponse(
			new IngestAcceptedData(
				accepted.accepted(),
				accepted.ingestionId(),
				accepted.receivedEvents(),
				accepted.deduplicatedEvents()
			)
		);
	}

	private void validateBeanConstraints(IngestEventsRequest request) {
		Set<ConstraintViolation<IngestEventsRequest>> violations = validator.validate(request);
		if (!violations.isEmpty()) {
			throw new ConstraintViolationException(violations);
		}
	}

	private void enforceIngestRateLimit(Authentication authentication, String idempotencyKey) {
		String token = authentication == null ? null : authentication.getName();
		IngestRateLimiter.AcquireResult acquireResult = ingestRateLimiter.tryAcquire(token, idempotencyKey);
		if (!acquireResult.allowed()) {
			throw new TooManyRequestsException("Ingest rate limit exceeded", acquireResult.retryAfterSeconds());
		}
	}

	public record IngestEventsRequest(
		@NotBlank(message = "project_id is required")
		@JsonProperty("project_id")
		String projectId,
		@NotBlank(message = "source must be one of: loki, otlp, custom")
		@Pattern(regexp = "loki|otlp|custom", message = "source must be one of: loki, otlp, custom")
		String source,
		@NotBlank(message = "batch_id must not be blank")
		@JsonProperty("batch_id")
		String batchId,
		@NotNull(message = "events size must be between 1 and 5000")
		@Size(min = 1, max = 5000, message = "events size must be between 1 and 5000")
		List<@NotNull(message = "events must not contain null items") @Valid CanonicalLogEventRequest> events
	) {
	}

	public record CanonicalLogEventRequest(
		@NotBlank(message = "event_id must not be blank")
		@JsonProperty("event_id")
		String eventId,
		@NotBlank(message = "timestamp must be RFC3339 date-time")
		@Pattern(
			regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:\\d{2})$",
			message = "timestamp must be RFC3339 date-time"
		)
		String timestamp,
		@NotBlank(message = "service must not be blank")
		String service,
		@NotBlank(message = "severity must be one of: debug, info, warn, error, fatal")
		@Pattern(
			regexp = "debug|info|warn|error|fatal",
			message = "severity must be one of: debug, info, warn, error, fatal"
		)
		String severity,
		@NotBlank(message = "message must not be blank")
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
