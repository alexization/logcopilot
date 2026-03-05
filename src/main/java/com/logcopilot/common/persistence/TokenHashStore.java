package com.logcopilot.common.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Token hash persistence contract.
 *
 * <p>Callers may rely on {@link #ensureDefaults(Map)} and {@link #findTokenType(String)} across all implementations.
 * Lifecycle APIs are optional and throw {@link UnsupportedOperationException} by default so non-lifecycle stores can
 * remain minimal. Lifecycle callers should either use a store known to support these methods or handle
 * {@code UnsupportedOperationException} explicitly.
 */
public interface TokenHashStore {

	/**
	 * Seeds default tokens when the backing store is empty or partially initialized.
	 *
	 * <p>Implementations may ignore invalid entries. This method mutates the store.
	 */
	void ensureDefaults(Map<String, String> tokenTypeByPlainToken);

	/**
	 * Resolves the token type for an active token.
	 */
	Optional<String> findTokenType(String plainToken);

	/**
	 * Finds an active token by plain token value.
	 *
	 * <p>Lifecycle-aware implementations should override this.
	 */
	default Optional<TokenRecord> findActiveToken(String plainToken) {
		throw new UnsupportedOperationException("findActiveToken is not supported");
	}

	/**
	 * Finds a token by identifier regardless of current lifecycle state.
	 *
	 * <p>Lifecycle-aware implementations should override this.
	 */
	default Optional<TokenRecord> findTokenById(String tokenId) {
		throw new UnsupportedOperationException("findTokenById is not supported");
	}

	/**
	 * Issues (creates or upserts) a token record.
	 *
	 * <p>Implementations should persist metadata and return the stored token record.
	 */
	default TokenRecord issueToken(
		String tokenId,
		String plainToken,
		String tokenType,
		String tokenRole,
		String displayName
	) {
		throw new UnsupportedOperationException("issueToken is not supported");
	}

	/**
	 * Rotates an existing token and returns updated metadata.
	 *
	 * <p>Returns empty when the target token does not exist or cannot be rotated.
	 */
	default Optional<TokenRecord> rotateToken(String tokenId, String plainToken) {
		throw new UnsupportedOperationException("rotateToken is not supported");
	}

	/**
	 * Revokes an existing token and returns updated metadata.
	 *
	 * <p>Returns empty when the target token does not exist.
	 */
	default Optional<TokenRecord> revokeToken(String tokenId, String reason) {
		throw new UnsupportedOperationException("revokeToken is not supported");
	}

	/**
	 * Lists all tokens including active/revoked states.
	 */
	default List<TokenRecord> listTokens() {
		throw new UnsupportedOperationException("listTokens is not supported");
	}

	/**
	 * Counts active tokens for a token type.
	 */
	default long countActiveTokensByType(String tokenType) {
		throw new UnsupportedOperationException("countActiveTokensByType is not supported");
	}

	/**
	 * Counts active tokens for a token role.
	 */
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
