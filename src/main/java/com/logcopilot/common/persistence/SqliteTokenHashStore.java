package com.logcopilot.common.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

public class SqliteTokenHashStore implements TokenHashStore {

	private static final String TABLE_SQL = """
		create table if not exists bearer_token_hashes (
			token_hash text primary key,
			token_type text not null,
			created_at text not null
		)
		""";

	private final JdbcTemplate jdbcTemplate;

	public SqliteTokenHashStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.jdbcTemplate.execute(TABLE_SQL);
	}

	@Override
	public void ensureDefaults(Map<String, String> tokenTypeByPlainToken) {
		if (tokenTypeByPlainToken == null || tokenTypeByPlainToken.isEmpty()) {
			return;
		}

		for (Map.Entry<String, String> entry : tokenTypeByPlainToken.entrySet()) {
			String plainToken = entry.getKey();
			String tokenType = entry.getValue();
			if (plainToken == null || plainToken.isBlank() || tokenType == null || tokenType.isBlank()) {
				continue;
			}
			jdbcTemplate.update(
				"""
					insert into bearer_token_hashes(token_hash, token_type, created_at)
					values (?, ?, ?)
					on conflict(token_hash) do update set
						token_type = excluded.token_type
					""",
				hash(plainToken),
				tokenType,
				Instant.now().toString()
			);
		}
	}

	@Override
	public Optional<String> findTokenType(String plainToken) {
		if (plainToken == null || plainToken.isBlank()) {
			return Optional.empty();
		}
		String hashed = hash(plainToken);
		return jdbcTemplate.query(
			"select token_type from bearer_token_hashes where token_hash = ?",
			resultSet -> resultSet.next() ? Optional.of(resultSet.getString(1)) : Optional.empty(),
			hashed
		);
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
