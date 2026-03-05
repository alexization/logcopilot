package com.logcopilot.common.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryTokenHashStore implements TokenHashStore {

	private final Map<String, TokenState> tokensById = new LinkedHashMap<>();
	private final Map<String, String> tokenIdByHash = new HashMap<>();

	@Override
	public synchronized void ensureDefaults(Map<String, String> tokenTypeByPlainToken) {
		if (tokenTypeByPlainToken == null || tokenTypeByPlainToken.isEmpty()) {
			return;
		}

		boolean hasOperator = countActiveTokensByRole("operator") > 0L;
		String operatorCandidateId = hasOperator ? null : resolveOperatorCandidateTokenId(tokenTypeByPlainToken);
		for (Map.Entry<String, String> entry : tokenTypeByPlainToken.entrySet()) {
			String plainToken = normalize(entry.getKey());
			String tokenType = normalizeTokenType(entry.getValue());
			if (plainToken == null || tokenType == null) {
				continue;
			}
			String tokenId = "legacy-" + hash(plainToken).substring(0, 16);
			String tokenRole = defaultRoleForLegacyToken(tokenType, !hasOperator && tokenId.equals(operatorCandidateId));
			if ("operator".equals(tokenRole)) {
				hasOperator = true;
			}
			issueToken(tokenId, plainToken, tokenType, tokenRole, "legacy-" + tokenRole);
		}
	}

	@Override
	public synchronized Optional<String> findTokenType(String plainToken) {
		String normalizedToken = normalize(plainToken);
		if (normalizedToken == null) {
			return Optional.empty();
		}
		String hash = hash(normalizedToken);
		String tokenId = tokenIdByHash.get(hash);
		if (tokenId == null) {
			return Optional.empty();
		}
		TokenState tokenState = tokensById.get(tokenId);
		if (tokenState == null || !"active".equals(tokenState.status())) {
			return Optional.empty();
		}
		return Optional.of(tokenState.tokenType());
	}

	@Override
	public synchronized Optional<TokenRecord> findActiveToken(String plainToken) {
		String normalizedToken = normalize(plainToken);
		if (normalizedToken == null) {
			return Optional.empty();
		}
		String hash = hash(normalizedToken);
		String tokenId = tokenIdByHash.get(hash);
		if (tokenId == null) {
			return Optional.empty();
		}
		TokenState tokenState = tokensById.get(tokenId);
		if (tokenState == null || !"active".equals(tokenState.status())) {
			return Optional.empty();
		}
		return Optional.of(toRecord(tokenState));
	}

	@Override
	public synchronized Optional<TokenRecord> findTokenById(String tokenId) {
		String normalizedTokenId = normalize(tokenId);
		if (normalizedTokenId == null) {
			return Optional.empty();
		}
		TokenState tokenState = tokensById.get(normalizedTokenId);
		if (tokenState == null) {
			return Optional.empty();
		}
		return Optional.of(toRecord(tokenState));
	}

	@Override
	public synchronized TokenRecord issueToken(
		String tokenId,
		String plainToken,
		String tokenType,
		String tokenRole,
		String displayName
	) {
		String normalizedTokenId = normalize(tokenId);
		if (normalizedTokenId == null) {
			normalizedTokenId = UUID.randomUUID().toString();
		}
		String normalizedToken = normalize(plainToken);
		String normalizedType = normalizeTokenType(tokenType);
		String normalizedRole = normalizeTokenRole(tokenRole);
		String normalizedDisplayName = normalize(displayName);
		if (normalizedToken == null || normalizedType == null || normalizedRole == null || normalizedDisplayName == null) {
			throw new IllegalArgumentException("token metadata must not be blank");
		}

		String hashed = hash(normalizedToken);
		String existingTokenId = tokenIdByHash.get(hashed);
		if (existingTokenId != null && !existingTokenId.equals(normalizedTokenId)) {
			throw new IllegalArgumentException("token hash already exists");
		}
		TokenState previous = tokensById.get(normalizedTokenId);
		if (previous != null && !previous.tokenHash().equals(hashed)) {
			tokenIdByHash.remove(previous.tokenHash());
		}

		String now = Instant.now().toString();
		TokenState tokenState = new TokenState(
			normalizedTokenId,
			hashed,
			normalizedType,
			normalizedRole,
			normalizedDisplayName,
			"active",
			now,
			null,
			null,
			null
		);
		tokensById.put(normalizedTokenId, tokenState);
		tokenIdByHash.put(tokenState.tokenHash(), normalizedTokenId);
		return toRecord(tokenState);
	}

	@Override
	public synchronized Optional<TokenRecord> rotateToken(String tokenId, String plainToken) {
		String normalizedTokenId = normalize(tokenId);
		String normalizedToken = normalize(plainToken);
		if (normalizedTokenId == null || normalizedToken == null) {
			return Optional.empty();
		}
		TokenState previous = tokensById.get(normalizedTokenId);
		if (previous == null) {
			return Optional.empty();
		}
		if (!"active".equals(previous.status())) {
			return Optional.empty();
		}

		String hashed = hash(normalizedToken);
		String existingTokenId = tokenIdByHash.get(hashed);
		if (existingTokenId != null && !existingTokenId.equals(normalizedTokenId)) {
			throw new IllegalArgumentException("token hash already exists");
		}
		if (!previous.tokenHash().equals(hashed)) {
			tokenIdByHash.remove(previous.tokenHash());
		}
		TokenState updated = new TokenState(
			previous.tokenId(),
			hashed,
			previous.tokenType(),
			previous.tokenRole(),
			previous.displayName(),
			"active",
			previous.createdAt(),
			Instant.now().toString(),
			null,
			null
		);
		tokensById.put(normalizedTokenId, updated);
		tokenIdByHash.put(updated.tokenHash(), normalizedTokenId);
		return Optional.of(toRecord(updated));
	}

	@Override
	public synchronized Optional<TokenRecord> revokeToken(String tokenId, String reason) {
		String normalizedTokenId = normalize(tokenId);
		if (normalizedTokenId == null) {
			return Optional.empty();
		}
		TokenState previous = tokensById.get(normalizedTokenId);
		if (previous == null) {
			return Optional.empty();
		}
		if ("revoked".equals(previous.status())) {
			return Optional.of(toRecord(previous));
		}

		tokenIdByHash.remove(previous.tokenHash());
		TokenState revoked = new TokenState(
			previous.tokenId(),
			previous.tokenHash(),
			previous.tokenType(),
			previous.tokenRole(),
			previous.displayName(),
			"revoked",
			previous.createdAt(),
			previous.rotatedAt(),
			Instant.now().toString(),
			normalize(reason)
		);
		tokensById.put(normalizedTokenId, revoked);
		return Optional.of(toRecord(revoked));
	}

	@Override
	public synchronized List<TokenRecord> listTokens() {
		List<TokenRecord> records = new ArrayList<>(tokensById.size());
		for (TokenState state : tokensById.values()) {
			records.add(toRecord(state));
		}
		return records;
	}

	@Override
	public synchronized long countActiveTokensByType(String tokenType) {
		String normalizedType = normalizeTokenType(tokenType);
		if (normalizedType == null) {
			return 0L;
		}
		return tokensById.values().stream()
			.filter(token -> normalizedType.equals(token.tokenType()))
			.filter(token -> "active".equals(token.status()))
			.count();
	}

	@Override
	public synchronized long countActiveTokensByRole(String tokenRole) {
		String normalizedRole = normalizeTokenRole(tokenRole);
		if (normalizedRole == null) {
			return 0L;
		}
		return tokensById.values().stream()
			.filter(token -> normalizedRole.equals(token.tokenRole()))
			.filter(token -> "active".equals(token.status()))
			.count();
	}

	private TokenRecord toRecord(TokenState state) {
		return new TokenRecord(
			state.tokenId(),
			state.tokenType(),
			state.tokenRole(),
			state.displayName(),
			state.status(),
			state.createdAt(),
			state.rotatedAt(),
			state.revokedAt(),
			state.revocationReason()
		);
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeTokenType(String tokenType) {
		String normalized = normalize(tokenType);
		if (normalized == null) {
			return null;
		}
		String upper = normalized.toUpperCase(Locale.ROOT);
		if (!"API".equals(upper) && !"INGEST".equals(upper)) {
			return null;
		}
		return upper;
	}

	private String normalizeTokenRole(String tokenRole) {
		String normalized = normalize(tokenRole);
		if (normalized == null) {
			return null;
		}
		String lower = normalized.toLowerCase(Locale.ROOT);
		if (!"operator".equals(lower) && !"api".equals(lower) && !"ingest".equals(lower)) {
			return null;
		}
		return lower;
	}

	private String defaultRoleForLegacyToken(String tokenType, boolean promoteToOperator) {
		if (promoteToOperator && !"INGEST".equals(tokenType)) {
			return "operator";
		}
		return "INGEST".equals(tokenType) ? "ingest" : "api";
	}

	private String resolveOperatorCandidateTokenId(Map<String, String> tokenTypeByPlainToken) {
		return tokenTypeByPlainToken.entrySet().stream()
			.map(entry -> {
				String plainToken = normalize(entry.getKey());
				String tokenType = normalizeTokenType(entry.getValue());
				if (plainToken == null || tokenType == null || "INGEST".equals(tokenType)) {
					return null;
				}
				return "legacy-" + hash(plainToken).substring(0, 16);
			})
			.filter(value -> value != null)
			.sorted()
			.findFirst()
			.orElse(null);
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

	private record TokenState(
		String tokenId,
		String tokenHash,
		String tokenType,
		String tokenRole,
		String displayName,
		String status,
		String createdAt,
		String rotatedAt,
		String revokedAt,
		String revocationReason
	) {
	}
}
