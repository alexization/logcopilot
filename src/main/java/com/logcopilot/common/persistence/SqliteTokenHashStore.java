package com.logcopilot.common.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SqliteTokenHashStore implements TokenHashStore {

	private static final String TABLE_NAME = "bearer_token_hashes";
	private static final String TABLE_SQL = """
		create table if not exists bearer_token_hashes (
			token_id text primary key,
			token_hash text not null unique,
			token_type text not null,
			token_role text not null,
			display_name text not null,
			status text not null,
			created_at text not null,
			rotated_at text,
			revoked_at text,
			revocation_reason text
		)
		""";

	private static final Set<String> REQUIRED_COLUMNS = Set.of(
		"token_id",
		"token_hash",
		"token_type",
		"token_role",
		"display_name",
		"status",
		"created_at",
		"rotated_at",
		"revoked_at",
		"revocation_reason"
	);

	private final JdbcTemplate jdbcTemplate;

	public SqliteTokenHashStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		ensureSchema();
	}

	@Override
	public void ensureDefaults(Map<String, String> tokenTypeByPlainToken) {
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
			String now = Instant.now().toString();
			jdbcTemplate.update(
				"""
					insert into bearer_token_hashes(
						token_id,
						token_hash,
						token_type,
						token_role,
						display_name,
						status,
						created_at,
						rotated_at,
						revoked_at,
						revocation_reason
					)
					values (?, ?, ?, ?, ?, 'active', ?, null, null, null)
					on conflict(token_id) do update set
						token_hash = excluded.token_hash,
						token_type = excluded.token_type,
						token_role = excluded.token_role,
						display_name = excluded.display_name,
						status = 'active',
						rotated_at = null,
						revoked_at = null,
						revocation_reason = null
				""",
				tokenId,
				hash(plainToken),
				tokenType,
				tokenRole,
				"legacy-" + tokenRole,
				now
			);
		}
	}

	@Override
	public Optional<String> findTokenType(String plainToken) {
		return findActiveToken(plainToken).map(TokenRecord::tokenType);
	}

	@Override
	public Optional<TokenRecord> findActiveToken(String plainToken) {
		String normalizedToken = normalize(plainToken);
		if (normalizedToken == null) {
			return Optional.empty();
		}
		String hashed = hash(normalizedToken);
		return jdbcTemplate.query(
			"""
				select token_id,
				       token_type,
				       token_role,
				       display_name,
				       status,
				       created_at,
				       rotated_at,
				       revoked_at,
				       revocation_reason
				from bearer_token_hashes
				where token_hash = ?
				  and status = 'active'
			""",
			resultSet -> {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				return Optional.of(toRecord(resultSet));
			},
			hashed
		);
	}

	@Override
	public Optional<TokenRecord> findTokenById(String tokenId) {
		String normalizedTokenId = normalize(tokenId);
		if (normalizedTokenId == null) {
			return Optional.empty();
		}
		return readById(normalizedTokenId);
	}

	@Override
	public TokenRecord issueToken(
		String tokenId,
		String plainToken,
		String tokenType,
		String tokenRole,
		String displayName
	) {
		String normalizedTokenId = normalize(tokenId);
		String normalizedToken = normalize(plainToken);
		String normalizedType = normalizeTokenType(tokenType);
		String normalizedRole = normalizeTokenRole(tokenRole);
		String normalizedDisplayName = normalize(displayName);
		if (normalizedTokenId == null) {
			normalizedTokenId = UUID.randomUUID().toString();
		}
		if (normalizedToken == null || normalizedType == null || normalizedRole == null || normalizedDisplayName == null) {
			throw new IllegalArgumentException("token metadata must not be blank");
		}

		String now = Instant.now().toString();
		jdbcTemplate.update(
			"""
				insert into bearer_token_hashes(
					token_id,
					token_hash,
					token_type,
					token_role,
					display_name,
					status,
					created_at,
					rotated_at,
					revoked_at,
					revocation_reason
				)
				values (?, ?, ?, ?, ?, 'active', ?, null, null, null)
				on conflict(token_id) do update set
					token_hash = excluded.token_hash,
					token_type = excluded.token_type,
					token_role = excluded.token_role,
					display_name = excluded.display_name,
					status = 'active',
					rotated_at = null,
					revoked_at = null,
					revocation_reason = null
			""",
			normalizedTokenId,
			hash(normalizedToken),
			normalizedType,
			normalizedRole,
			normalizedDisplayName,
			now
		);
		return readById(normalizedTokenId)
			.orElseThrow(() -> new IllegalStateException("Issued token not found"));
	}

	@Override
	public Optional<TokenRecord> rotateToken(String tokenId, String plainToken) {
		String normalizedTokenId = normalize(tokenId);
		String normalizedToken = normalize(plainToken);
		if (normalizedTokenId == null || normalizedToken == null) {
			return Optional.empty();
		}

		int updated = jdbcTemplate.update(
			"""
				update bearer_token_hashes
				set token_hash = ?,
					status = 'active',
					rotated_at = ?,
					revoked_at = null,
					revocation_reason = null
				where token_id = ?
				  and status = 'active'
			""",
			hash(normalizedToken),
			Instant.now().toString(),
			normalizedTokenId
		);
		if (updated == 0) {
			return Optional.empty();
		}
		return readById(normalizedTokenId);
	}

	@Override
	public Optional<TokenRecord> revokeToken(String tokenId, String reason) {
		String normalizedTokenId = normalize(tokenId);
		if (normalizedTokenId == null) {
			return Optional.empty();
		}

		int updated = jdbcTemplate.update(
			"""
				update bearer_token_hashes
				set status = 'revoked',
					revoked_at = ?,
					revocation_reason = ?
				where token_id = ?
				  and status = 'active'
			""",
			Instant.now().toString(),
			normalize(reason),
			normalizedTokenId
		);
		if (updated == 0) {
			return readById(normalizedTokenId);
		}
		return readById(normalizedTokenId);
	}

	@Override
	public List<TokenRecord> listTokens() {
		return jdbcTemplate.query(
			"""
				select token_id,
				       token_type,
				       token_role,
				       display_name,
				       status,
				       created_at,
				       rotated_at,
				       revoked_at,
				       revocation_reason
				from bearer_token_hashes
				order by created_at asc
			""",
			(resultSet, rowNum) -> toRecord(resultSet)
		);
	}

	@Override
	public long countActiveTokensByType(String tokenType) {
		String normalizedType = normalizeTokenType(tokenType);
		if (normalizedType == null) {
			return 0L;
		}
		Long count = jdbcTemplate.queryForObject(
			"""
				select count(*)
				from bearer_token_hashes
				where token_type = ?
				  and status = 'active'
			""",
			Long.class,
			normalizedType
		);
		return count == null ? 0L : count;
	}

	@Override
	public long countActiveTokensByRole(String tokenRole) {
		String normalizedRole = normalizeTokenRole(tokenRole);
		if (normalizedRole == null) {
			return 0L;
		}
		Long count = jdbcTemplate.queryForObject(
			"""
				select count(*)
				from bearer_token_hashes
				where token_role = ?
				  and status = 'active'
			""",
			Long.class,
			normalizedRole
		);
		return count == null ? 0L : count;
	}

	private Optional<TokenRecord> readById(String tokenId) {
		return jdbcTemplate.query(
			"""
				select token_id,
				       token_type,
				       token_role,
				       display_name,
				       status,
				       created_at,
				       rotated_at,
				       revoked_at,
				       revocation_reason
				from bearer_token_hashes
				where token_id = ?
			""",
			resultSet -> resultSet.next() ? Optional.of(toRecord(resultSet)) : Optional.empty(),
			tokenId
		);
	}

	private TokenRecord toRecord(java.sql.ResultSet resultSet) throws java.sql.SQLException {
		return new TokenRecord(
			resultSet.getString("token_id"),
			resultSet.getString("token_type"),
			resultSet.getString("token_role"),
			resultSet.getString("display_name"),
			resultSet.getString("status"),
			resultSet.getString("created_at"),
			resultSet.getString("rotated_at"),
			resultSet.getString("revoked_at"),
			resultSet.getString("revocation_reason")
		);
	}

	private void ensureSchema() {
		Set<String> columns = new HashSet<>(jdbcTemplate.query(
			"pragma table_info(" + TABLE_NAME + ")",
			(resultSet, rowNum) -> resultSet.getString("name")
		));
		if (columns.isEmpty()) {
			jdbcTemplate.execute(TABLE_SQL);
			return;
		}
		if (columns.containsAll(REQUIRED_COLUMNS)) {
			return;
		}
		migrateLegacyTable();
	}

	private void migrateLegacyTable() {
		String migrationLegacyTable = TABLE_NAME + "_legacy_" + System.nanoTime();
		jdbcTemplate.execute((Connection connection) -> {
			boolean previousAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			try (Statement statement = connection.createStatement()) {
				statement.execute("alter table " + TABLE_NAME + " rename to " + migrationLegacyTable);
				statement.execute(TABLE_SQL);
				statement.execute(
					"""
						insert into bearer_token_hashes(
							token_id,
							token_hash,
							token_type,
							token_role,
							display_name,
							status,
							created_at,
							rotated_at,
							revoked_at,
							revocation_reason
						)
						select
							'legacy-' || substr(token_hash, 1, 16),
							token_hash,
							upper(token_type),
							case
								when upper(token_type) = 'INGEST' then 'ingest'
								when token_hash = (
									select min(token_hash)
									from %s
									where upper(token_type) <> 'INGEST'
								) then 'operator'
								else 'api'
							end,
							'legacy-' || case
								when upper(token_type) = 'INGEST' then 'ingest'
								when token_hash = (
									select min(token_hash)
									from %s
									where upper(token_type) <> 'INGEST'
								) then 'operator'
								else 'api'
							end,
							'active',
							coalesce(created_at, CURRENT_TIMESTAMP),
							null,
							null,
							null
						from %s
					""".formatted(migrationLegacyTable, migrationLegacyTable, migrationLegacyTable)
				);
				statement.execute("drop table " + migrationLegacyTable);
				connection.commit();
				return null;
			} catch (SQLException exception) {
				connection.rollback();
				throw new IllegalStateException("Failed to migrate legacy token hash table", exception);
			} finally {
				connection.setAutoCommit(previousAutoCommit);
			}
		});
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
}
