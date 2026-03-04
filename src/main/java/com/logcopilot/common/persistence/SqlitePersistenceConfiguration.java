package com.logcopilot.common.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@EnableConfigurationProperties(PersistenceProperties.class)
public class SqlitePersistenceConfiguration {

	@Bean
	@ConditionalOnProperty(name = "logcopilot.persistence.enabled", havingValue = "true", matchIfMissing = true)
	public DataSource sqliteDataSource(PersistenceProperties properties) {
		String jdbcUrl = toJdbcUrl(properties.getSqlitePath());
		prepareSqliteParentDirectory(properties.getSqlitePath());

		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl(jdbcUrl);
		return dataSource;
	}

	@Bean
	@ConditionalOnBean(DataSource.class)
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	@ConditionalOnBean(JdbcTemplate.class)
	public StateCipher stateCipher(PersistenceProperties properties) {
		return new StateCipher(properties.getEncryptionKey());
	}

	@Bean
	@ConditionalOnBean(JdbcTemplate.class)
	public StateSnapshotRepository stateSnapshotRepository(
		JdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper,
		StateCipher stateCipher
	) {
		return new SqliteStateSnapshotRepository(jdbcTemplate, objectMapper, stateCipher);
	}

	@Bean
	@ConditionalOnBean(JdbcTemplate.class)
	public TokenHashStore tokenHashStore(JdbcTemplate jdbcTemplate) {
		return new SqliteTokenHashStore(jdbcTemplate);
	}

	private String toJdbcUrl(String sqlitePath) {
		if (sqlitePath == null || sqlitePath.isBlank()) {
			return "jdbc:sqlite:./data/logcopilot.sqlite";
		}
		String trimmed = sqlitePath.trim();
		if (trimmed.startsWith("jdbc:sqlite:")) {
			return trimmed;
		}
		if (trimmed.startsWith(":memory:")) {
			return "jdbc:sqlite::memory:";
		}
		return "jdbc:sqlite:" + trimmed;
	}

	private void prepareSqliteParentDirectory(String sqlitePath) {
		if (sqlitePath == null || sqlitePath.isBlank()) {
			createParentDirectory("./data/logcopilot.sqlite");
			return;
		}
		String trimmed = sqlitePath.trim();
		if (trimmed.startsWith("jdbc:sqlite:")) {
			trimmed = trimmed.substring("jdbc:sqlite:".length());
		}
		if (trimmed.startsWith(":memory:") || trimmed.startsWith(":resource:")) {
			return;
		}
		String filesystemPath = toFilesystemPathForDirectoryPreparation(trimmed);
		if (filesystemPath == null || filesystemPath.isBlank()) {
			return;
		}
		createParentDirectory(filesystemPath);
	}

	private String toFilesystemPathForDirectoryPreparation(String rawPath) {
		int querySeparatorIndex = rawPath.indexOf('?');
		String withoutQuery = querySeparatorIndex >= 0
			? rawPath.substring(0, querySeparatorIndex)
			: rawPath;
		if (!withoutQuery.startsWith("file:")) {
			return withoutQuery;
		}
		try {
			URI uri = new URI(withoutQuery);
			String path = uri.getPath();
			if (path != null && !path.isBlank()) {
				return path;
			}
			String schemeSpecificPart = uri.getSchemeSpecificPart();
			if (schemeSpecificPart == null || schemeSpecificPart.isBlank()) {
				return null;
			}
			if (schemeSpecificPart.startsWith("//")) {
				URI normalized = new URI("file:" + schemeSpecificPart);
				return normalized.getPath();
			}
			return schemeSpecificPart;
		} catch (URISyntaxException exception) {
			return null;
		}
	}

	private void createParentDirectory(String sqlitePath) {
		Path path = Paths.get(sqlitePath);
		Path parent = path.toAbsolutePath().getParent();
		if (parent == null) {
			return;
		}
		try {
			Files.createDirectories(parent);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to prepare SQLite directory: " + parent, exception);
		}
	}
}
