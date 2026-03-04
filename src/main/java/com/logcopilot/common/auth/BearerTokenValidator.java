package com.logcopilot.common.auth;

import com.logcopilot.common.error.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.util.Map;

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

	public ValidatedToken validate(String authorization) {
		if (authorization == null) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		String[] parts = authorization.trim().split("\\s+");
		if (parts.length != 2 || !"bearer".equalsIgnoreCase(parts[0]) || parts[1].isBlank()) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		String token = parts[1];
		TokenType tokenType = VERIFIED_TOKENS.get(token);
		if (tokenType == null) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		return new ValidatedToken(token, tokenType);
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
