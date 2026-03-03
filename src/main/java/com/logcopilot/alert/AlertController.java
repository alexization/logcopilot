package com.logcopilot.alert;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.api.ApiMeta;
import com.logcopilot.common.auth.BearerTokenValidator;
import com.logcopilot.common.error.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/projects/{project_id}")
public class AlertController {

	private final AlertService alertService;
	private final BearerTokenValidator bearerTokenValidator;

	public AlertController(
		AlertService alertService,
		BearerTokenValidator bearerTokenValidator
	) {
		this.alertService = alertService;
		this.bearerTokenValidator = bearerTokenValidator;
	}

	@PostMapping("/alerts/slack")
	public ResponseEntity<AlertChannelResponse> configureSlack(
		@PathVariable("project_id") String projectId,
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestBody(required = false) SlackAlertRequest request
	) {
		String actorToken = bearerTokenValidator.validate(authorization);
		if (request == null) {
			throw new ValidationException("Request body must not be null");
		}

		AlertService.ConfigureResult result = alertService.configureSlack(
			projectId,
			actorToken,
			new AlertService.ConfigureSlackCommand(
				request.webhookUrl(),
				request.channel(),
				request.minConfidence()
			)
		);
		return toResponse(result);
	}

	@PostMapping("/alerts/email")
	public ResponseEntity<AlertChannelResponse> configureEmail(
		@PathVariable("project_id") String projectId,
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestBody(required = false) EmailAlertRequest request
	) {
		String actorToken = bearerTokenValidator.validate(authorization);
		if (request == null) {
			throw new ValidationException("Request body must not be null");
		}

		AlertService.ConfigureResult result = alertService.configureEmail(
			projectId,
			actorToken,
			new AlertService.ConfigureEmailCommand(
				request.from(),
				request.recipients(),
				request.smtp() == null
					? null
					: new AlertService.SmtpCommand(
						request.smtp().host(),
						request.smtp().port(),
						request.smtp().username(),
						request.smtp().password(),
						request.smtp().starttls()
					),
				request.minConfidence()
			)
		);
		return toResponse(result);
	}

	@GetMapping("/audit-logs")
	public AuditLogListResponse listAuditLogs(
		@PathVariable("project_id") String projectId,
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestParam(value = "action", required = false) String action,
		@RequestParam(value = "actor", required = false) String actor,
		@RequestParam(value = "cursor", required = false) String cursor,
		@RequestParam(value = "limit", required = false) Integer limit
	) {
		bearerTokenValidator.validate(authorization);

		AlertService.AuditLogListResult result = alertService.listAuditLogs(
			projectId,
			new AlertService.AuditLogQuery(action, actor, cursor, limit)
		);
		List<AuditLogData> data = result.data().stream()
			.map(log -> new AuditLogData(
				log.id(),
				log.actor(),
				log.action(),
				log.resourceType(),
				log.resourceId(),
				log.createdAt(),
				log.metadata()
			))
			.toList();
		return new AuditLogListResponse(data, new ApiMeta(result.requestId(), result.nextCursor()));
	}

	private ResponseEntity<AlertChannelResponse> toResponse(AlertService.ConfigureResult result) {
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		AlertService.AlertChannel channel = result.channel();
		return ResponseEntity.status(status).body(new AlertChannelResponse(new AlertChannelData(
			channel.id(),
			channel.type(),
			channel.enabled(),
			channel.updatedAt()
		)));
	}

	public record SlackAlertRequest(
		@JsonProperty("webhook_url")
		String webhookUrl,
		String channel,
		@JsonProperty("min_confidence")
		Double minConfidence
	) {
	}

	public record EmailAlertRequest(
		String from,
		List<String> recipients,
		SmtpRequest smtp,
		@JsonProperty("min_confidence")
		Double minConfidence
	) {
	}

	public record SmtpRequest(
		String host,
		Integer port,
		String username,
		String password,
		Boolean starttls
	) {
	}

	public record AlertChannelResponse(AlertChannelData data) {
	}

	public record AlertChannelData(
		String id,
		String type,
		boolean enabled,
		@JsonProperty("updated_at")
		Instant updatedAt
	) {
	}

	public record AuditLogListResponse(
		List<AuditLogData> data,
		ApiMeta meta
	) {
	}

	public record AuditLogData(
		String id,
		String actor,
		String action,
		@JsonProperty("resource_type")
		String resourceType,
		@JsonProperty("resource_id")
		String resourceId,
		@JsonProperty("created_at")
		Instant createdAt,
		Map<String, Object> metadata
	) {
	}
}
