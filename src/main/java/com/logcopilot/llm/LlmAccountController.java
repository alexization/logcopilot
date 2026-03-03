package com.logcopilot.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.auth.BearerTokenValidator;
import com.logcopilot.common.error.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController
@RequestMapping("/v1/projects/{project_id}")
public class LlmAccountController {

	private final LlmAccountService llmAccountService;
	private final BearerTokenValidator bearerTokenValidator;

	public LlmAccountController(
		LlmAccountService llmAccountService,
		BearerTokenValidator bearerTokenValidator
	) {
		this.llmAccountService = llmAccountService;
		this.bearerTokenValidator = bearerTokenValidator;
	}

	@PostMapping("/llm-accounts/api-key")
	public ResponseEntity<LlmAccountResponse> upsertApiKeyLlmAccount(
		@PathVariable("project_id") String projectId,
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestBody LlmApiKeyAccountRequest request
	) {
		bearerTokenValidator.validate(authorization);
		validateApiKeyRequestBody(request);

		LlmAccountService.UpsertResult result = llmAccountService.upsertApiKey(
			projectId,
			new LlmAccountService.ApiKeyUpsertCommand(
				request.provider(),
				request.label(),
				request.apiKey(),
				request.model(),
				request.baseUrl()
			)
		);

		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status)
			.body(new LlmAccountResponse(toData(result.account())));
	}

	@PostMapping("/llm-oauth/{provider}/start")
	public OAuthStartResponse startLlmOAuth(
		@PathVariable("project_id") String projectId,
		@PathVariable("provider") String provider,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		bearerTokenValidator.validate(authorization);

		LlmAccountService.OAuthStartResult result = llmAccountService.startOAuth(projectId, provider);
		return new OAuthStartResponse(new OAuthStartData(result.authUrl(), result.state()));
	}

	@GetMapping("/llm-oauth/{provider}/callback")
	public OAuthCallbackResponse callbackLlmOAuth(
		@PathVariable("project_id") String projectId,
		@PathVariable("provider") String provider,
		@RequestParam(value = "code", required = false) String code,
		@RequestParam(value = "state", required = false) String state
	) {
		LlmAccountService.OAuthCallbackResult result = llmAccountService.callbackOAuth(
			projectId,
			provider,
			code,
			state
		);
		return new OAuthCallbackResponse(new OAuthCallbackData(result.linked(), result.accountId()));
	}

	@GetMapping("/llm-accounts")
	public LlmAccountListResponse listLlmAccounts(
		@PathVariable("project_id") String projectId,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		bearerTokenValidator.validate(authorization);
		List<LlmAccountData> data = llmAccountService.list(projectId).stream()
			.map(this::toData)
			.toList();
		return new LlmAccountListResponse(data);
	}

	@DeleteMapping("/llm-accounts/{account_id}")
	public ResponseEntity<Void> deleteLlmAccount(
		@PathVariable("project_id") String projectId,
		@PathVariable("account_id") String accountId,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		bearerTokenValidator.validate(authorization);
		llmAccountService.delete(projectId, accountId);
		return ResponseEntity.noContent().build();
	}

	private void validateApiKeyRequestBody(LlmApiKeyAccountRequest request) {
		if (request == null) {
			throw new BadRequestException("Malformed JSON request body");
		}
	}

	private LlmAccountData toData(LlmAccountService.LlmAccount account) {
		return new LlmAccountData(
			account.id(),
			account.provider(),
			account.authType(),
			account.label(),
			account.model(),
			account.status(),
			account.createdAt()
		);
	}

	public record LlmApiKeyAccountRequest(
		String provider,
		String label,
		@JsonProperty("api_key")
		String apiKey,
		String model,
		@JsonProperty("base_url")
		String baseUrl
	) {
	}

	public record LlmAccountResponse(LlmAccountData data) {
	}

	public record LlmAccountListResponse(List<LlmAccountData> data) {
	}

	public record LlmAccountData(
		String id,
		String provider,
		@JsonProperty("auth_type")
		String authType,
		String label,
		String model,
		String status,
		@JsonProperty("created_at")
		Instant createdAt
	) {
	}

	public record OAuthStartResponse(OAuthStartData data) {
	}

	public record OAuthStartData(
		@JsonProperty("auth_url")
		String authUrl,
		String state
	) {
	}

	public record OAuthCallbackResponse(OAuthCallbackData data) {
	}

	public record OAuthCallbackData(
		boolean linked,
		@JsonProperty("account_id")
		String accountId
	) {
	}
}
