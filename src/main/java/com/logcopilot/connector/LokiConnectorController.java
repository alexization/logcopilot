package com.logcopilot.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.error.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestBody LokiConnectorRequest request
	) {
		validateBearerToken(authorization);

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
		@PathVariable("project_id") String projectId,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		validateBearerToken(authorization);

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

	private void validateBearerToken(String authorization) {
		if (authorization == null) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		String[] parts = authorization.trim().split("\\s+", 2);
		if (parts.length != 2 || !"bearer".equalsIgnoreCase(parts[0]) || parts[1].isBlank()) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}
	}

	public record LokiConnectorRequest(
		String endpoint,
		@JsonProperty("tenant_id")
		String tenantId,
		LokiConnectorService.AuthRequest auth,
		String query,
		@JsonProperty("poll_interval_seconds")
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
