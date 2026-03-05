package com.logcopilot.common.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.List;

public interface TokenHashStore {

	void ensureDefaults(Map<String, String> tokenTypeByPlainToken);

	Optional<String> findTokenType(String plainToken);

	default Optional<TokenRecord> findActiveToken(String plainToken) {
		throw new UnsupportedOperationException("findActiveToken is not supported");
	}

	default TokenRecord issueToken(
		String tokenId,
		String plainToken,
		String tokenType,
		String tokenRole,
		String displayName
	) {
		throw new UnsupportedOperationException("issueToken is not supported");
	}

	default Optional<TokenRecord> rotateToken(String tokenId, String plainToken) {
		throw new UnsupportedOperationException("rotateToken is not supported");
	}

	default Optional<TokenRecord> revokeToken(String tokenId, String reason) {
		throw new UnsupportedOperationException("revokeToken is not supported");
	}

	default List<TokenRecord> listTokens() {
		throw new UnsupportedOperationException("listTokens is not supported");
	}

	default long countActiveTokensByType(String tokenType) {
		throw new UnsupportedOperationException("countActiveTokensByType is not supported");
	}

	default long countActiveTokensByRole(String tokenRole) {
		throw new UnsupportedOperationException("countActiveTokensByRole is not supported");
	}

	record TokenRecord(
		String id,
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
