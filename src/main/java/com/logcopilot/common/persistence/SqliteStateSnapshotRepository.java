package com.logcopilot.common.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

public class SqliteStateSnapshotRepository implements StateSnapshotRepository {

	private static final Logger logger = LoggerFactory.getLogger(SqliteStateSnapshotRepository.class);
	private static final String TABLE_SQL = """
		create table if not exists state_snapshots (
			scope text primary key,
			payload blob not null,
			updated_at text not null
		)
		""";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final StateCipher stateCipher;

	public SqliteStateSnapshotRepository(
		JdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper,
		StateCipher stateCipher
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.stateCipher = stateCipher;
		this.jdbcTemplate.execute(TABLE_SQL);
	}

	@Override
	public void save(String scope, Object snapshot) {
		if (scope == null || scope.isBlank() || snapshot == null) {
			return;
		}

		try {
			byte[] serialized = objectMapper.writeValueAsBytes(snapshot);
			byte[] encrypted = stateCipher.encrypt(serialized);
			jdbcTemplate.update(
				"""
					insert into state_snapshots(scope, payload, updated_at)
					values (?, ?, ?)
					on conflict(scope) do update set
						payload = excluded.payload,
						updated_at = excluded.updated_at
					""",
				scope,
				encrypted,
				Instant.now().toString()
			);
		} catch (Exception exception) {
			logger.warn("Failed to save state snapshot: scope={}", scope, exception);
		}
	}

	@Override
	public <T> Optional<T> load(String scope, Class<T> type) {
		if (scope == null || scope.isBlank() || type == null) {
			return Optional.empty();
		}
		byte[] encryptedPayload = readEncryptedPayload(scope);
		if (encryptedPayload == null) {
			return Optional.empty();
		}

		try {
			byte[] decrypted = stateCipher.decrypt(encryptedPayload);
			return Optional.of(objectMapper.readValue(decrypted, type));
		} catch (Exception exception) {
			return handleLoadFailure(scope, exception);
		}
	}

	@Override
	public <T> Optional<T> load(String scope, TypeReference<T> typeReference) {
		if (scope == null || scope.isBlank() || typeReference == null) {
			return Optional.empty();
		}
		byte[] encryptedPayload = readEncryptedPayload(scope);
		if (encryptedPayload == null) {
			return Optional.empty();
		}

		try {
			byte[] decrypted = stateCipher.decrypt(encryptedPayload);
			return Optional.of(objectMapper.readValue(decrypted, typeReference));
		} catch (Exception exception) {
			return handleLoadFailure(scope, exception);
		}
	}

	private byte[] readEncryptedPayload(String scope) {
		try {
			return jdbcTemplate.query(
				"select payload from state_snapshots where scope = ?",
				resultSet -> resultSet.next() ? resultSet.getBytes(1) : null,
				scope
			);
		} catch (DataAccessException exception) {
			throw new IllegalStateException("Failed to query state snapshot: " + scope, exception);
		}
	}

	private <T> Optional<T> handleLoadFailure(String scope, Exception exception) {
		// Skip unreadable snapshot to keep application startup resilient.
		// Do not delete row automatically; operators may recover data offline.
		logger.warn("Failed to load state snapshot: scope={}", scope, exception);
		return Optional.empty();
	}
}
