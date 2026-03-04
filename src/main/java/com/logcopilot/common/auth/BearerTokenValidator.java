package com.logcopilot.common.auth;

import com.logcopilot.common.persistence.TokenHashStore;
import com.logcopilot.common.error.UnauthorizedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
	private static final Map<String, String> VERIFIED_TOKEN_TYPES = VERIFIED_TOKENS.entrySet().stream()
		.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().name()));

	private final TokenHashStore tokenHashStore;

	public BearerTokenValidator() {
		this((TokenHashStore) null);
	}

	@Autowired
	public BearerTokenValidator(ObjectProvider<TokenHashStore> tokenHashStoreProvider) {
		this(tokenHashStoreProvider.getIfAvailable());
	}

	BearerTokenValidator(TokenHashStore tokenHashStore) {
		this.tokenHashStore = tokenHashStore;
		if (this.tokenHashStore != null) {
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
		TokenType tokenType;
		try {
			tokenType = resolveTokenType(token);
		} catch (RuntimeException exception) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}
		if (tokenType == null) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		return new ValidatedToken(token, tokenType);
	}

	private TokenType resolveTokenType(String token) {
		if (tokenHashStore == null) {
			return VERIFIED_TOKENS.get(token);
		}
		Optional<String> tokenType = tokenHashStore.findTokenType(token);
		if (tokenType.isEmpty()) {
			return null;
		}
		try {
			return TokenType.valueOf(tokenType.get());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	public record ValidatedToken(
		String value,
		TokenType type
	) {
	}

	public enum TokenType {
		API,
		INGEST
	}
}
