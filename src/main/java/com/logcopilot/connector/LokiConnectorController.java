package com.logcopilot.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/projects/{project_id}/connectors/loki")
public class LokiConnectorController {

	private final LokiConnectorService lokiConnectorService;

	public LokiConnectorController(LokiConnectorService lokiConnectorService) {
		this.lokiConnectorService = lokiConnectorService;
	}

	@PostMapping
	public ResponseEntity<ConnectorResponse> upsertLokiConnector(
		@PathVariable("project_id") String projectId,
		@Valid @RequestBody LokiConnectorRequest request
	) {
		LokiConnectorService.UpsertResult result = lokiConnectorService.upsert(
			projectId,
			new LokiConnectorService.LokiConnectorRequest(
				request.endpoint(),
				request.tenantId(),
				request.auth(),
				request.query(),
				request.pollIntervalSeconds()
			)
		);

		ConnectorResponse body = new ConnectorResponse(
			new ConnectorData(
				result.connector().id(),
				"loki",
				"active",
				result.connector().updatedAt()
			)
		);

		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(body);
	}

	@PostMapping("/test")
	public LokiTestResponse testLokiConnector(
		@PathVariable("project_id") String projectId
	) {
		LokiConnectorService.LokiTestResult result = lokiConnectorService.test(projectId);
		return new LokiTestResponse(
			new LokiTestData(
				result.success(),
				result.sampleCount(),
				result.latencyMs(),
				result.message()
			)
		);
	}

	public record LokiConnectorRequest(
		@NotBlank(message = "Endpoint must be a valid URI")
		String endpoint,
		@JsonProperty("tenant_id")
		String tenantId,
		@NotNull(message = "Auth type must be one of: none, bearer, basic")
		@Valid
		LokiConnectorService.AuthRequest auth,
		@NotBlank(message = "Query must not be blank")
		String query,
		@JsonProperty("poll_interval_seconds")
		@Min(value = 5, message = "poll_interval_seconds must be between 5 and 300")
		@Max(value = 300, message = "poll_interval_seconds must be between 5 and 300")
		Integer pollIntervalSeconds
	) {
	}

	public record ConnectorResponse(ConnectorData data) {
	}

	public record ConnectorData(
		String id,
		String type,
		String status,
		@JsonProperty("updated_at")
		Instant updatedAt
	) {
	}

	public record LokiTestResponse(LokiTestData data) {
	}

	public record LokiTestData(
		boolean success,
		@JsonProperty("sample_count")
		int sampleCount,
		@JsonProperty("latency_ms")
		int latencyMs,
		String message
	) {
	}
}
