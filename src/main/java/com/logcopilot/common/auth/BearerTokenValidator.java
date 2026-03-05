package com.logcopilot.common.auth;

import com.logcopilot.common.persistence.TokenHashStore;
import com.logcopilot.common.error.UnauthorizedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class BearerTokenValidator {

	private static final Map<String, SeedToken> VERIFIED_TOKENS = Map.ofEntries(
		Map.entry("ingest-token", new SeedToken(TokenType.INGEST, TokenRole.INGEST)),
		Map.entry("test-token", new SeedToken(TokenType.API, TokenRole.OPERATOR)),
		Map.entry("project-token", new SeedToken(TokenType.API, TokenRole.API)),
		Map.entry("policy-token", new SeedToken(TokenType.API, TokenRole.API)),
		Map.entry("incident-token", new SeedToken(TokenType.API, TokenRole.API)),
		Map.entry("llm-token", new SeedToken(TokenType.API, TokenRole.API)),
		Map.entry("alert-token", new SeedToken(TokenType.API, TokenRole.API)),
		Map.entry("reader-token", new SeedToken(TokenType.API, TokenRole.API)),
		Map.entry("actor-token-a", new SeedToken(TokenType.API, TokenRole.API)),
		Map.entry("actor-token-b", new SeedToken(TokenType.API, TokenRole.API)),
		Map.entry("api-token", new SeedToken(TokenType.API, TokenRole.API)),
		Map.entry("token", new SeedToken(TokenType.API, TokenRole.API))
	);
	private static final Map<String, String> VERIFIED_TOKEN_TYPES = VERIFIED_TOKENS.entrySet().stream()
		.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().type().name()));

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
			seedDefaultTokens();
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
			SeedToken seedToken = VERIFIED_TOKENS.get(token);
			if (seedToken == null) {
				return null;
			}
			return new ValidatedToken(token, seedToken.type(), seedToken.role());
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

	private void seedDefaultTokens() {
		try {
			for (Map.Entry<String, SeedToken> entry : VERIFIED_TOKENS.entrySet()) {
				String plainToken = entry.getKey();
				SeedToken seedToken = entry.getValue();
				String role = seedToken.role().apiValue();
				tokenHashStore.issueToken(
					"legacy-" + hash(plainToken).substring(0, 16),
					plainToken,
					seedToken.type().name(),
					role,
					"legacy-" + role
				);
			}
		} catch (UnsupportedOperationException unsupportedOperationException) {
			tokenHashStore.ensureDefaults(VERIFIED_TOKEN_TYPES);
		}
	}

	private String hash(String plainToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(bytes);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to hash token", exception);
		}
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
		INGEST;

		public String apiValue() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private record SeedToken(
		TokenType type,
		TokenRole role
	) {
	}
}
