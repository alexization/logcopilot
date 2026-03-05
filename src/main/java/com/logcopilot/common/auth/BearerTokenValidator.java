package com.logcopilot.common.auth;

import com.logcopilot.common.persistence.TokenHashStore;
import com.logcopilot.common.error.UnauthorizedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class BearerTokenValidator {

	private static final Map<String, TokenType> VERIFIED_TOKENS = Map.ofEntries(
		Map.entry("ingest-token", TokenType.INGEST),
		Map.entry("test-token", TokenType.API),
		Map.entry("project-token", TokenType.API),
		Map.entry("policy-token", TokenType.API),
		Map.entry("incident-token", TokenType.API),
		Map.entry("llm-token", TokenType.API),
		Map.entry("alert-token", TokenType.API),
		Map.entry("reader-token", TokenType.API),
		Map.entry("actor-token-a", TokenType.API),
		Map.entry("actor-token-b", TokenType.API),
		Map.entry("api-token", TokenType.API),
		Map.entry("token", TokenType.API)
	);
	private static final Map<String, TokenRole> VERIFIED_TOKEN_ROLES = Map.ofEntries(
		Map.entry("ingest-token", TokenRole.INGEST),
		Map.entry("test-token", TokenRole.OPERATOR),
		Map.entry("project-token", TokenRole.API),
		Map.entry("policy-token", TokenRole.API),
		Map.entry("incident-token", TokenRole.API),
		Map.entry("llm-token", TokenRole.API),
		Map.entry("alert-token", TokenRole.API),
		Map.entry("reader-token", TokenRole.API),
		Map.entry("actor-token-a", TokenRole.API),
		Map.entry("actor-token-b", TokenRole.API),
		Map.entry("api-token", TokenRole.API),
		Map.entry("token", TokenRole.API)
	);
	private static final Map<String, String> VERIFIED_TOKEN_TYPES = VERIFIED_TOKENS.entrySet().stream()
		.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().name()));

	private final TokenHashStore tokenHashStore;

	public BearerTokenValidator() {
		this((TokenHashStore) null, true);
	}

	@Autowired
	public BearerTokenValidator(
		ObjectProvider<TokenHashStore> tokenHashStoreProvider,
		@Value("${logcopilot.auth.seed-default-tokens:false}") boolean seedDefaultTokens
	) {
		this(tokenHashStoreProvider.getIfAvailable(), seedDefaultTokens);
	}

	BearerTokenValidator(TokenHashStore tokenHashStore) {
		this(tokenHashStore, true);
	}

	BearerTokenValidator(TokenHashStore tokenHashStore, boolean seedDefaultTokens) {
		this.tokenHashStore = tokenHashStore;
		if (this.tokenHashStore != null && seedDefaultTokens) {
			this.tokenHashStore.ensureDefaults(VERIFIED_TOKEN_TYPES);
		}
	}

	public ValidatedToken validate(String authorization) {
		if (authorization == null) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		String[] parts = authorization.trim().split("\\s+");
		if (parts.length != 2 || !"bearer".equalsIgnoreCase(parts[0]) || parts[1].isBlank()) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		String token = parts[1];
		ValidatedToken validatedToken;
		try {
			validatedToken = resolveValidatedToken(token);
		} catch (RuntimeException exception) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}
		if (validatedToken == null) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		return validatedToken;
	}

	private ValidatedToken resolveValidatedToken(String token) {
		if (tokenHashStore == null) {
			TokenType tokenType = VERIFIED_TOKENS.get(token);
			TokenRole tokenRole = VERIFIED_TOKEN_ROLES.get(token);
			if (tokenType == null || tokenRole == null) {
				return null;
			}
			return new ValidatedToken(token, tokenType, tokenRole);
		}

		Optional<TokenHashStore.TokenRecord> activeToken;
		try {
			activeToken = tokenHashStore.findActiveToken(token);
		} catch (UnsupportedOperationException exception) {
			activeToken = Optional.empty();
		}
		if (activeToken.isPresent()) {
			TokenHashStore.TokenRecord record = activeToken.get();
			TokenType tokenType = toTokenType(record.tokenType());
			TokenRole tokenRole = toTokenRole(record.tokenRole(), tokenType);
			if (tokenType == null || tokenRole == null) {
				return null;
			}
			return new ValidatedToken(token, tokenType, tokenRole);
		}
		Optional<String> tokenType = tokenHashStore.findTokenType(token);
		if (tokenType.isEmpty()) {
			return null;
		}
		TokenType resolvedType = toTokenType(tokenType.get());
		if (resolvedType == null) {
			return null;
		}
		TokenRole fallbackRole = resolvedType == TokenType.INGEST ? TokenRole.INGEST : TokenRole.API;
		return new ValidatedToken(token, resolvedType, fallbackRole);
	}

	private TokenType toTokenType(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return TokenType.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private TokenRole toTokenRole(String value, TokenType tokenType) {
		if (tokenType == TokenType.INGEST) {
			return TokenRole.INGEST;
		}
		if (value == null || value.isBlank()) {
			return TokenRole.API;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "operator" -> TokenRole.OPERATOR;
			case "api" -> TokenRole.API;
			case "ingest" -> TokenRole.INGEST;
			default -> null;
		};
	}

	public record ValidatedToken(
		String value,
		TokenType type,
		TokenRole role
	) {
	}

	public enum TokenType {
		API,
		INGEST
	}

	public enum TokenRole {
		OPERATOR,
		API,
		INGEST
	}
}
