package com.logcopilot.alert;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.api.ApiMeta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/v1/projects/{project_id}")
public class AlertController {

	private final AlertService alertService;

	public AlertController(AlertService alertService) {
		this.alertService = alertService;
	}

	@PostMapping("/alerts/slack")
	public ResponseEntity<AlertChannelResponse> configureSlack(
		@PathVariable("project_id") String projectId,
		@NotNull(message = "Request body must not be null")
		@Valid @RequestBody(required = false) SlackAlertRequest request,
		Authentication authentication
	) {
		String actorToken = authentication.getName();

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
		@NotNull(message = "Request body must not be null")
		@Valid @RequestBody(required = false) EmailAlertRequest request,
		Authentication authentication
	) {
		String actorToken = authentication.getName();

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
		@RequestParam(value = "action", required = false) String action,
		@RequestParam(value = "actor", required = false) String actor,
		@RequestParam(value = "cursor", required = false) String cursor,
		@RequestParam(value = "limit", required = false) Integer limit
	) {
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
		@NotBlank(message = "webhook_url must be a valid URI")
		@JsonProperty("webhook_url")
		String webhookUrl,
		@NotBlank(message = "channel must not be blank")
		String channel,
		@JsonProperty("min_confidence")
		@DecimalMin(value = "0.0", message = "min_confidence must be between 0 and 1")
		@DecimalMax(value = "1.0", message = "min_confidence must be between 0 and 1")
		Double minConfidence
	) {
	}

	public record EmailAlertRequest(
		@NotBlank(message = "from must be a valid email")
		@Email(message = "from must be a valid email")
		String from,
		@NotNull(message = "recipients must contain at least 1 email")
		@Size(min = 1, message = "recipients must contain at least 1 email")
		List<@NotBlank(message = "recipient must be a valid email") @Email(message = "recipient must be a valid email") String> recipients,
		@NotNull(message = "smtp must not be null")
		@Valid
		SmtpRequest smtp,
		@JsonProperty("min_confidence")
		@DecimalMin(value = "0.0", message = "min_confidence must be between 0 and 1")
		@DecimalMax(value = "1.0", message = "min_confidence must be between 0 and 1")
		Double minConfidence
	) {
	}

	public record SmtpRequest(
		@NotBlank(message = "smtp.host must not be blank")
		String host,
		@NotNull(message = "smtp.port must be between 1 and 65535")
		@Min(value = 1, message = "smtp.port must be between 1 and 65535")
		@Max(value = 65535, message = "smtp.port must be between 1 and 65535")
		Integer port,
		@NotBlank(message = "smtp.username must not be blank")
		String username,
		@NotBlank(message = "smtp.password must not be blank")
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
