package com.logcopilot.connector;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Primary
@ConditionalOnBean(JdbcTemplate.class)
public class SqliteLokiPullCursorStore implements LokiPullCursorStore {

	private static final String TABLE_SQL = """
		create table if not exists loki_pull_cursors (
			project_id text primary key,
			cursor_value integer not null,
			updated_at text not null
		)
		""";

	private final JdbcTemplate jdbcTemplate;

	public SqliteLokiPullCursorStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.jdbcTemplate.execute(TABLE_SQL);
	}

	@Override
	public synchronized long readCursor(String projectId) {
		if (projectId == null || projectId.isBlank()) {
			return 0L;
		}
		return jdbcTemplate.query(
			"select cursor_value from loki_pull_cursors where project_id = ?",
			resultSet -> resultSet.next() ? resultSet.getLong(1) : 0L,
			projectId
		);
	}

	@Override
	public synchronized void commit(String projectId, long nextCursor) {
		if (projectId == null || projectId.isBlank()) {
			return;
		}
		long normalizedCursor = Math.max(0L, nextCursor);
		long current = readCursor(projectId);
		long committed = Math.max(current, normalizedCursor);
		jdbcTemplate.update(
			"""
				insert into loki_pull_cursors(project_id, cursor_value, updated_at)
				values (?, ?, ?)
				on conflict(project_id) do update set
					cursor_value = excluded.cursor_value,
					updated_at = excluded.updated_at
				""",
			projectId,
			committed,
			Instant.now().toString()
		);
	}
}
