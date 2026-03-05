package com.logcopilot.bootstrap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.auth.TokenLifecycleService;
import com.logcopilot.project.ProjectDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/v1/bootstrap")
public class BootstrapController {

	private final BootstrapService bootstrapService;

	public BootstrapController(BootstrapService bootstrapService) {
		this.bootstrapService = bootstrapService;
	}

	@GetMapping("/status")
	public BootstrapStatusResponse status() {
		BootstrapService.BootstrapStatus status = bootstrapService.status();
		return new BootstrapStatusResponse(new BootstrapStatusData(status.bootstrapped(), status.initializedAt()));
	}

	@PostMapping("/initialize")
	public ResponseEntity<BootstrapInitializeResponse> initialize(
		@NotNull(message = "Request body must not be null")
		@Valid @RequestBody(required = false) BootstrapInitializeRequest request
	) {
		BootstrapService.BootstrapInitialized initialized = bootstrapService.initialize(
			new BootstrapService.InitializeCommand(
				request.projectName(),
				request.environment(),
				request.operatorTokenName(),
				request.ingestTokenName()
			)
		);

		BootstrapInitializeData data = new BootstrapInitializeData(
			initialized.bootstrapped(),
			initialized.initializedAt(),
			initialized.project(),
			toTokenData(initialized.operatorTokenInfo(), initialized.operatorTokenValue()),
			toTokenData(initialized.ingestTokenInfo(), initialized.ingestTokenValue())
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(new BootstrapInitializeResponse(data));
	}

	private TokenWithValueData toTokenData(TokenLifecycleService.TokenInfo tokenInfo, String tokenValue) {
		return new TokenWithValueData(
			tokenInfo.id(),
			tokenInfo.name(),
			tokenInfo.role(),
			tokenInfo.status(),
			tokenInfo.createdAt(),
			tokenInfo.rotatedAt(),
			tokenInfo.revokedAt(),
			tokenInfo.revocationReason(),
			tokenValue
		);
	}

	public record BootstrapInitializeRequest(
		@NotBlank(message = "project_name must not be blank")
		@Size(max = 100, message = "project_name length must be <= 100")
		@JsonProperty("project_name")
		String projectName,
		@NotBlank(message = "environment must be one of: prod, staging, dev")
		@Pattern(regexp = "prod|staging|dev", message = "environment must be one of: prod, staging, dev")
		String environment,
		@Size(max = 80, message = "operator_token_name length must be <= 80")
		@JsonProperty("operator_token_name")
		String operatorTokenName,
		@Size(max = 80, message = "ingest_token_name length must be <= 80")
		@JsonProperty("ingest_token_name")
		String ingestTokenName
	) {
	}

	public record BootstrapStatusResponse(BootstrapStatusData data) {
	}

	public record BootstrapStatusData(
		boolean bootstrapped,
		@JsonProperty("initialized_at")
		String initializedAt
	) {
	}

	public record BootstrapInitializeResponse(BootstrapInitializeData data) {
	}

	public record BootstrapInitializeData(
		boolean bootstrapped,
		@JsonProperty("initialized_at")
		String initializedAt,
		ProjectDto project,
		@JsonProperty("operator_token")
		TokenWithValueData operatorToken,
		@JsonProperty("ingest_token")
		TokenWithValueData ingestToken
	) {
	}

	public record TokenWithValueData(
		String id,
		String name,
		String role,
		String status,
		@JsonProperty("created_at")
		String createdAt,
		@JsonProperty("rotated_at")
		String rotatedAt,
		@JsonProperty("revoked_at")
		String revokedAt,
		@JsonProperty("revocation_reason")
		String revocationReason,
		String token
	) {
	}
}
