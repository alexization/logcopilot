package com.logcopilot.common.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/v1/tokens")
public class TokenLifecycleController {

	private final TokenLifecycleService tokenLifecycleService;

	public TokenLifecycleController(TokenLifecycleService tokenLifecycleService) {
		this.tokenLifecycleService = tokenLifecycleService;
	}

	@GetMapping
	public TokenListResponse listTokens() {
		List<TokenMetadataData> data = tokenLifecycleService.listTokens().stream()
			.map(this::toTokenMetadataData)
			.toList();
		return new TokenListResponse(data);
	}

	@PostMapping
	public ResponseEntity<TokenResponse> issueToken(
		@NotNull(message = "Request body must not be null")
		@Valid @RequestBody(required = false) IssueTokenRequest request
	) {
		TokenLifecycleService.IssuedToken issued = tokenLifecycleService.issueToken(
			new TokenLifecycleService.IssueCommand(request.name(), request.role())
		);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new TokenResponse(toTokenData(issued.tokenInfo(), issued.plainToken())));
	}

	@PostMapping("/{token_id}/rotate")
	public TokenResponse rotateToken(@PathVariable("token_id") String tokenId) {
		TokenLifecycleService.IssuedToken issued = tokenLifecycleService.rotateToken(tokenId);
		return new TokenResponse(toTokenData(issued.tokenInfo(), issued.plainToken()));
	}

	@PostMapping("/{token_id}/revoke")
	public TokenMetadataResponse revokeToken(
		@PathVariable("token_id") String tokenId,
		@Valid @RequestBody(required = false) RevokeTokenRequest request
	) {
		String reason = request == null ? null : request.reason();
		TokenLifecycleService.TokenInfo tokenInfo = tokenLifecycleService.revokeToken(tokenId, reason);
		return new TokenMetadataResponse(toTokenMetadataData(tokenInfo));
	}

	private TokenData toTokenData(TokenLifecycleService.TokenInfo tokenInfo, String plainToken) {
		return new TokenData(
			tokenInfo.id(),
			tokenInfo.name(),
			tokenInfo.role(),
			tokenInfo.status(),
			tokenInfo.createdAt(),
			tokenInfo.rotatedAt(),
			tokenInfo.revokedAt(),
			tokenInfo.revocationReason(),
			plainToken
		);
	}

	private TokenMetadataData toTokenMetadataData(TokenLifecycleService.TokenInfo tokenInfo) {
		return new TokenMetadataData(
			tokenInfo.id(),
			tokenInfo.name(),
			tokenInfo.role(),
			tokenInfo.status(),
			tokenInfo.createdAt(),
			tokenInfo.rotatedAt(),
			tokenInfo.revokedAt(),
			tokenInfo.revocationReason()
		);
	}

	public record IssueTokenRequest(
		@Size(max = 80, message = "name length must be <= 80")
		String name,
		@NotBlank(message = "role must be one of: operator, api, ingest")
		String role
	) {
	}

	public record RevokeTokenRequest(
		@Size(max = 200, message = "reason length must be <= 200")
		String reason
	) {
	}

	public record TokenResponse(TokenData data) {
	}

	public record TokenMetadataResponse(TokenMetadataData data) {
	}

	public record TokenListResponse(List<TokenMetadataData> data) {
	}

	public record TokenData(
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

	public record TokenMetadataData(
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
		String revocationReason
	) {
	}
}
