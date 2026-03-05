package com.logcopilot.common.auth;

import com.logcopilot.common.error.ConflictException;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.common.persistence.TokenHashStore;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TokenLifecycleService {

	private static final String API_TOKEN_TYPE = BearerTokenValidator.TokenType.API.name();
	private static final String INGEST_TOKEN_TYPE = BearerTokenValidator.TokenType.INGEST.name();
	private static final int TOKEN_NAME_MAX_LENGTH = 80;

	private final TokenHashStore tokenHashStore;
	private final SecureRandom secureRandom = new SecureRandom();

	public TokenLifecycleService(TokenHashStore tokenHashStore) {
		this.tokenHashStore = tokenHashStore;
	}

	public synchronized List<TokenInfo> listTokens() {
		return tokenHashStore.listTokens().stream()
			.map(this::toTokenInfo)
			.toList();
	}

	public synchronized IssuedToken issueToken(IssueCommand command) {
		if (command == null) {
			throw new ValidationException("Request body must not be null");
		}
		TokenRole role = normalizeRole(command.role());
		String displayName = normalizeDisplayName(command.name(), role);
		String tokenId = UUID.randomUUID().toString();
		String plainToken = generatePlainToken(role);

		TokenHashStore.TokenRecord record;
		try {
			record = tokenHashStore.issueToken(
				tokenId,
				plainToken,
				toTokenType(role),
				role.apiValue(),
				displayName
			);
		} catch (IllegalArgumentException exception) {
			throw new ValidationException("Invalid token lifecycle request");
		}

		return new IssuedToken(toTokenInfo(record), plainToken);
	}

	public synchronized IssuedToken rotateToken(String tokenId) {
		String normalizedTokenId = normalizeRequired(tokenId, "token_id must not be blank");
		TokenHashStore.TokenRecord existing = findTokenOrThrow(normalizedTokenId);
		if (!"active".equals(existing.status())) {
			throw new ConflictException("Token already revoked");
		}

		TokenRole role = normalizeRole(existing.tokenRole());
		String plainToken = generatePlainToken(role);
		TokenHashStore.TokenRecord rotated = tokenHashStore.rotateToken(normalizedTokenId, plainToken)
			.orElseThrow(() -> new NotFoundException("Token not found"));
		return new IssuedToken(toTokenInfo(rotated), plainToken);
	}

	public synchronized TokenInfo revokeToken(String tokenId, String reason) {
		String normalizedTokenId = normalizeRequired(tokenId, "token_id must not be blank");
		TokenHashStore.TokenRecord existing = findTokenOrThrow(normalizedTokenId);
		if ("revoked".equals(existing.status())) {
			return toTokenInfo(existing);
		}

		String tokenType = normalizeRequired(existing.tokenType(), "Invalid token type");
		TokenRole role = normalizeRole(existing.tokenRole());
		if (role == TokenRole.OPERATOR && tokenHashStore.countActiveTokensByRole("operator") <= 1L) {
			throw new ConflictException("Cannot revoke last operator token");
		}
		if (API_TOKEN_TYPE.equals(tokenType) && tokenHashStore.countActiveTokensByType(API_TOKEN_TYPE) <= 1L) {
			throw new ConflictException("Cannot revoke last API-capable token");
		}

		TokenHashStore.TokenRecord revoked = tokenHashStore.revokeToken(normalizedTokenId, normalizeReason(reason))
			.orElseThrow(() -> new NotFoundException("Token not found"));
		return toTokenInfo(revoked);
	}

	public synchronized void forceRevokeToken(String tokenId, String reason) {
		String normalizedTokenId = normalizeRequired(tokenId, "token_id must not be blank");
		tokenHashStore.revokeToken(normalizedTokenId, normalizeReason(reason));
	}

	private TokenHashStore.TokenRecord findTokenOrThrow(String tokenId) {
		try {
			return tokenHashStore.findTokenById(tokenId)
				.orElseThrow(() -> new NotFoundException("Token not found"));
		} catch (UnsupportedOperationException unsupportedOperationException) {
			return tokenHashStore.listTokens().stream()
				.filter(token -> tokenId.equals(token.id()))
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Token not found"));
		}
	}

	private TokenInfo toTokenInfo(TokenHashStore.TokenRecord record) {
		TokenRole role = normalizeRole(record.tokenRole());
		return new TokenInfo(
			record.id(),
			record.displayName(),
			role.apiValue(),
			normalizeStatus(record.status()),
			record.createdAt(),
			record.rotatedAt(),
			record.revokedAt(),
			record.revocationReason()
		);
	}

	private String toTokenType(TokenRole role) {
		return role == TokenRole.INGEST ? INGEST_TOKEN_TYPE : API_TOKEN_TYPE;
	}

	private TokenRole normalizeRole(String role) {
		String normalized = normalizeRequired(role, "role must be one of: operator, api, ingest")
			.toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "operator" -> TokenRole.OPERATOR;
			case "api" -> TokenRole.API;
			case "ingest" -> TokenRole.INGEST;
			default -> throw new ValidationException("role must be one of: operator, api, ingest");
		};
	}

	private String normalizeStatus(String status) {
		String normalized = normalizeRequired(status, "Invalid token status").toLowerCase(Locale.ROOT);
		if (!"active".equals(normalized) && !"revoked".equals(normalized)) {
			throw new ValidationException("Invalid token status");
		}
		return normalized;
	}

	private String normalizeDisplayName(String name, TokenRole role) {
		String normalized = trimToNull(name);
		if (normalized == null) {
			return switch (role) {
				case OPERATOR -> "operator-token";
				case API -> "api-token";
				case INGEST -> "ingest-token";
			};
		}
		if (normalized.length() > TOKEN_NAME_MAX_LENGTH) {
			throw new ValidationException("name length must be <= 80");
		}
		return normalized;
	}

	private String normalizeReason(String reason) {
		String normalized = trimToNull(reason);
		if (normalized == null) {
			return null;
		}
		if (normalized.length() > 200) {
			throw new ValidationException("reason length must be <= 200");
		}
		return normalized;
	}

	private String normalizeRequired(String value, String message) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			throw new ValidationException(message);
		}
		return normalized;
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String generatePlainToken(TokenRole role) {
		byte[] randomBytes = new byte[24];
		secureRandom.nextBytes(randomBytes);
		String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		return "lcp_" + role.apiValue() + "_" + encoded;
	}

	public enum TokenRole {
		OPERATOR("operator"),
		API("api"),
		INGEST("ingest");

		private final String apiValue;

		TokenRole(String apiValue) {
			this.apiValue = apiValue;
		}

		public String apiValue() {
			return apiValue;
		}
	}

	public record IssueCommand(
		String name,
		String role
	) {
	}

	public record IssuedToken(
		TokenInfo tokenInfo,
		String plainToken
	) {
	}

	public record TokenInfo(
		String id,
		String name,
		String role,
		String status,
		String createdAt,
		String rotatedAt,
		String revokedAt,
		String revocationReason
	) {
	}
}
