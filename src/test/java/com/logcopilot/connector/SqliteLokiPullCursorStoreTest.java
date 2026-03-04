package com.logcopilot.connector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteLokiPullCursorStoreTest {

	@TempDir
	Path tempDir;

	@Test
	void keepsCursorMonotonicWithAtomicUpsert() {
		SqliteLokiPullCursorStore store = new SqliteLokiPullCursorStore(jdbcTemplate(tempDir.resolve("cursor-monotonic.sqlite")));

		store.commit("project-1", 10L);
		store.commit("project-1", 7L);
		store.commit("project-1", 21L);

		assertThat(store.readCursor("project-1")).isEqualTo(21L);
	}

	@Test
	void normalizesNegativeCursorBeforeCommit() {
		SqliteLokiPullCursorStore store = new SqliteLokiPullCursorStore(jdbcTemplate(tempDir.resolve("cursor-negative.sqlite")));

		store.commit("project-1", -1L);

		assertThat(store.readCursor("project-1")).isZero();
	}

	private JdbcTemplate jdbcTemplate(Path dbPath) {
		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
		return new JdbcTemplate(dataSource);
	}
}
