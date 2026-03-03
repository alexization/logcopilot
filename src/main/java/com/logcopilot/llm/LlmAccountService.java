package com.logcopilot.llm;

import com.logcopilot.common.error.BadRequestException;
import com.logcopilot.common.error.ConflictException;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.project.ProjectService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class LlmAccountService {

	private final ProjectService projectService;
	private final Map<String, LinkedHashMap<String, AccountState>> accountsByProject = new HashMap<>();
	private final Map<String, Map<String, String>> apiKeyAccountIdByProjectProvider = new HashMap<>();
	private final Map<String, Map<String, String>> oauthAccountIdByProjectProvider = new HashMap<>();
	private final Map<String, OAuthState> oauthStateByValue = new HashMap<>();

	public LlmAccountService(ProjectService projectService) {
		this.projectService = projectService;
	}

	public synchronized UpsertResult upsertApiKey(String projectId, ApiKeyUpsertCommand command) {
		requireProjectForScopedRequest(projectId);
		if (command == null) {
			throw new ValidationException("Request body must not be null");
		}

		String provider = validateProviderForValidationError(command.provider());
		String model = validateModel(command.model());
		String label = normalizeLabel(command.label());
		String baseUrl = validateBaseUrl(command.baseUrl());
		validateApiKey(command.apiKey());

		LinkedHashMap<String, AccountState> accounts = accountsByProject.computeIfAbsent(
			projectId,
			ignored -> new LinkedHashMap<>()
		);
		Map<String, String> byProvider = apiKeyAccountIdByProjectProvider.computeIfAbsent(
			projectId,
			ignored -> new HashMap<>()
		);

		String existingId = byProvider.get(provider);
		if (existingId == null) {
			String accountId = UUID.randomUUID().toString();
			AccountState created = new AccountState(
				accountId,
				provider,
				"api_key",
				label,
				model,
				"active",
				Instant.now(),
				baseUrl,
				command.apiKey()
			);
			accounts.put(accountId, created);
			byProvider.put(provider, accountId);
			return new UpsertResult(true, toLlmAccount(created));
		}

		AccountState existing = accounts.get(existingId);
		if (existing == null) {
			throw new NotFoundException("LLM account not found");
		}

		AccountState updated = new AccountState(
			existing.id(),
			provider,
			"api_key",
			label,
			model,
			"active",
			existing.createdAt(),
			baseUrl,
			command.apiKey()
		);
		accounts.put(existingId, updated);
		return new UpsertResult(false, toLlmAccount(updated));
	}

	public synchronized OAuthStartResult startOAuth(String projectId, String providerInput) {
		requireProjectForScopedRequest(projectId);
		String provider = validateProviderForBadRequest(providerInput);

		String state = UUID.randomUUID().toString();
		oauthStateByValue.put(state, new OAuthState(projectId, provider, Instant.now()));
		String authUrl = "https://auth.%s.example.com/oauth/authorize?project_id=%s&state=%s"
			.formatted(provider, projectId, state);
		return new OAuthStartResult(authUrl, state);
	}

	public synchronized OAuthCallbackResult callbackOAuth(
		String projectId,
		String providerInput,
		String code,
		String state
	) {
		requireProjectForScopedRequest(projectId);
		String provider = validateProviderForBadRequest(providerInput);
		validateCode(code);
		validateState(state);

		OAuthState started = oauthStateByValue.get(state);
		if (started == null
			|| !started.projectId().equals(projectId)
			|| !started.provider().equals(provider)) {
			throw new ConflictException("Invalid or expired oauth state");
		}
		oauthStateByValue.remove(state);

		LinkedHashMap<String, AccountState> accounts = accountsByProject.computeIfAbsent(
			projectId,
			ignored -> new LinkedHashMap<>()
		);
		Map<String, String> byProvider = oauthAccountIdByProjectProvider.computeIfAbsent(
			projectId,
			ignored -> new HashMap<>()
		);

		String existingId = byProvider.get(provider);
		if (existingId != null && accounts.containsKey(existingId)) {
			return new OAuthCallbackResult(true, existingId);
		}

		String accountId = UUID.randomUUID().toString();
		AccountState created = new AccountState(
			accountId,
			provider,
			"oauth",
			provider + "-oauth",
			defaultModel(provider),
			"active",
			Instant.now(),
			null,
			null
		);
		accounts.put(accountId, created);
		byProvider.put(provider, accountId);
		return new OAuthCallbackResult(true, accountId);
	}

	public synchronized List<LlmAccount> list(String projectId) {
		requireProject(projectId);
		LinkedHashMap<String, AccountState> accounts = accountsByProject.get(projectId);
		if (accounts == null) {
			return List.of();
		}
		return accounts.values().stream().map(this::toLlmAccount).toList();
	}

	public synchronized void delete(String projectId, String accountId) {
		requireProject(projectId);
		LinkedHashMap<String, AccountState> accounts = accountsByProject.get(projectId);
		if (accounts == null || accountId == null || accountId.isBlank()) {
			throw new NotFoundException("LLM account not found");
		}

		AccountState removed = accounts.remove(accountId);
		if (removed == null) {
			throw new NotFoundException("LLM account not found");
		}

		if ("api_key".equals(removed.authType())) {
			Map<String, String> byProvider = apiKeyAccountIdByProjectProvider.get(projectId);
			if (byProvider != null) {
				byProvider.remove(removed.provider());
			}
			return;
		}

		if ("oauth".equals(removed.authType())) {
			Map<String, String> byProvider = oauthAccountIdByProjectProvider.get(projectId);
			if (byProvider != null) {
				byProvider.remove(removed.provider());
			}
		}
	}

	private void requireProject(String projectId) {
		if (projectId == null || projectId.isBlank() || !projectService.existsById(projectId)) {
			throw new NotFoundException("Project not found");
		}
	}

	private void requireProjectForScopedRequest(String projectId) {
		if (projectId == null || projectId.isBlank() || !projectService.existsById(projectId)) {
			throw new BadRequestException("Project not found");
		}
	}

	private String validateProviderForValidationError(String provider) {
		String normalized = normalizeProvider(provider);
		if (normalized == null) {
			throw new ValidationException("provider must be one of: openai, gemini");
		}
		return normalized;
	}

	private String validateProviderForBadRequest(String provider) {
		String normalized = normalizeProvider(provider);
		if (normalized == null) {
			throw new BadRequestException("provider must be one of: openai, gemini");
		}
		return normalized;
	}

	private String normalizeProvider(String provider) {
		if (provider == null) {
			return null;
		}
		String normalized = provider.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			return null;
		}
		return switch (normalized) {
			case "openai", "gemini" -> normalized;
			default -> null;
		};
	}

	private void validateApiKey(String apiKey) {
		if (apiKey == null || apiKey.trim().isEmpty()) {
			throw new ValidationException("api_key must not be blank");
		}
	}

	private String validateModel(String model) {
		if (model == null || model.trim().isEmpty()) {
			throw new ValidationException("model must not be blank");
		}
		return model.trim();
	}

	private String normalizeLabel(String label) {
		if (label == null) {
			return null;
		}
		String trimmed = label.trim();
		if (trimmed.length() > 80) {
			throw new ValidationException("label must be at most 80 characters");
		}
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String validateBaseUrl(String baseUrl) {
		if (baseUrl == null) {
			return null;
		}
		String trimmed = baseUrl.trim();
		if (trimmed.isEmpty()) {
			return null;
		}

		try {
			URI uri = new URI(trimmed);
			if (!uri.isAbsolute() || uri.getHost() == null) {
				throw new ValidationException("base_url must be a valid URI");
			}
		} catch (URISyntaxException exception) {
			throw new ValidationException("base_url must be a valid URI");
		}
		return trimmed;
	}

	private void validateCode(String code) {
		if (code == null || code.trim().isEmpty()) {
			throw new BadRequestException("code must not be blank");
		}
	}

	private void validateState(String state) {
		if (state == null || state.trim().isEmpty()) {
			throw new BadRequestException("state must not be blank");
		}
	}

	private String defaultModel(String provider) {
		return "openai".equals(provider) ? "gpt-4o-mini" : "gemini-2.0-flash";
	}

	private LlmAccount toLlmAccount(AccountState state) {
		return new LlmAccount(
			state.id(),
			state.provider(),
			state.authType(),
			state.label(),
			state.model(),
			state.status(),
			state.createdAt()
		);
	}

	public record ApiKeyUpsertCommand(
		String provider,
		String label,
		String apiKey,
		String model,
		String baseUrl
	) {
	}

	public record UpsertResult(
		boolean created,
		LlmAccount account
	) {
	}

	public record OAuthStartResult(
		String authUrl,
		String state
	) {
	}

	public record OAuthCallbackResult(
		boolean linked,
		String accountId
	) {
	}

	public record LlmAccount(
		String id,
		String provider,
		String authType,
		String label,
		String model,
		String status,
		Instant createdAt
	) {
	}

	private record AccountState(
		String id,
		String provider,
		String authType,
		String label,
		String model,
		String status,
		Instant createdAt,
		String baseUrl,
		String secret
	) {
	}

	private record OAuthState(
		String projectId,
		String provider,
		Instant createdAt
	) {
	}
}
