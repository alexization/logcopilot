package com.logcopilot.system;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.error.UnauthorizedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SystemController {

	@GetMapping("/healthz")
	public HealthResponse healthz() {
		return new HealthResponse("ok");
	}

	@GetMapping("/readyz")
	public ReadyResponse readyz() {
		return new ReadyResponse("ready", Map.of("application", "up"));
	}

	@GetMapping("/v1/system/info")
	public SystemInfoResponse getSystemInfo(
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		validateBearerToken(authorization);
		return new SystemInfoResponse(
			new SystemInfoData(
				"1.0.0-mvp",
				"sqlite",
				"in_process",
				Map.of(
					"ingest_otlp_enabled", false,
					"llm_oauth_enabled", true,
					"alerts_enabled", true
				)
			)
		);
	}

	private void validateBearerToken(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}
	}

	public record HealthResponse(String status) {
	}

	public record ReadyResponse(
		String status,
		Map<String, String> checks
	) {
	}

	public record SystemInfoResponse(SystemInfoData data) {
	}

	public record SystemInfoData(
		String version,
		@JsonProperty("storage_mode")
		String storageMode,
		@JsonProperty("queue_mode")
		String queueMode,
		Map<String, Boolean> features
	) {
	}
}
