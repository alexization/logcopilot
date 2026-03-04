package com.logcopilot.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1/projects/{project_id}")
public class LlmAccountController {

	private final LlmAccountService llmAccountService;

	public LlmAccountController(LlmAccountService llmAccountService) {
		this.llmAccountService = llmAccountService;
	}

	@PostMapping("/llm-accounts/api-key")
	public ResponseEntity<LlmAccountResponse> upsertApiKeyLlmAccount(
		@PathVariable("project_id") String projectId,
		@Valid @RequestBody LlmApiKeyAccountRequest request
	) {
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
		@PathVariable("provider") String provider
	) {
		LlmAccountService.OAuthStartResult result = llmAccountService.startOAuth(projectId, provider);
		return new OAuthStartResponse(new OAuthStartData(result.authUrl(), result.state()));
	}

	@GetMapping("/llm-oauth/{provider}/callback")
	public OAuthCallbackResponse callbackLlmOAuth(
		@PathVariable("project_id") String projectId,
		@PathVariable("provider") String provider,
		@RequestParam(value = "code", required = false) String code,
		@RequestParam(value = "state", required = false) String state,
		@RequestParam(value = "error", required = false) String error,
		@RequestParam(value = "error_description", required = false) String errorDescription
	) {
		LlmAccountService.OAuthCallbackResult result = llmAccountService.callbackOAuth(
			projectId,
			provider,
			code,
			state,
			error,
			errorDescription
		);
		return new OAuthCallbackResponse(new OAuthCallbackData(result.linked(), result.accountId()));
	}

	@GetMapping("/llm-accounts")
	public LlmAccountListResponse listLlmAccounts(@PathVariable("project_id") String projectId) {
		List<LlmAccountData> data = llmAccountService.list(projectId).stream()
			.map(this::toData)
			.toList();
		return new LlmAccountListResponse(data);
	}

	@DeleteMapping("/llm-accounts/{account_id}")
	public ResponseEntity<Void> deleteLlmAccount(
		@PathVariable("project_id") String projectId,
		@PathVariable("account_id") String accountId
	) {
		llmAccountService.delete(projectId, accountId);
		return ResponseEntity.noContent().build();
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
		@NotBlank(message = "provider must not be blank")
		@Pattern(regexp = "(?i)openai|gemini", message = "provider must be one of: openai, gemini")
		String provider,
		@Size(max = 80, message = "label must be at most 80 characters")
		String label,
		@NotBlank(message = "api_key must not be blank")
		@JsonProperty("api_key")
		String apiKey,
		@NotBlank(message = "model must not be blank")
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
