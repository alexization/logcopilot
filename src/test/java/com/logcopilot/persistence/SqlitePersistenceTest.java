package com.logcopilot.persistence;

import com.logcopilot.LogcopilotApplication;
import com.logcopilot.alert.AlertService;
import com.logcopilot.common.persistence.PersistenceProperties;
import com.logcopilot.common.persistence.TokenHashStore;
import com.logcopilot.connector.LokiConnectorService;
import com.logcopilot.connector.LokiPullCursorStore;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.incident.IncidentService;
import com.logcopilot.llm.LlmAccountService;
import com.logcopilot.llm.LlmOAuthProperties;
import com.logcopilot.policy.PolicyService;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SqlitePersistenceTest {

	@TempDir
	Path tempDir;

	@Test
	void restoresCoreStateAcrossRestart() {
		Path dbPath = tempDir.resolve("logcopilot-t21-restart.sqlite");
		deleteIfExists(dbPath);
		String encryptionSecret = "ephemeral-" + UUID.randomUUID();
		String projectId;

		try (ConfigurableApplicationContext context = startContext(dbPath, encryptionSecret)) {
			ProjectService projectService = context.getBean(ProjectService.class);
			LokiConnectorService connectorService = context.getBean(LokiConnectorService.class);
			LlmAccountService llmAccountService = context.getBean(LlmAccountService.class);
			PolicyService policyService = context.getBean(PolicyService.class);
			AlertService alertService = context.getBean(AlertService.class);
			IncidentService incidentService = context.getBean(IncidentService.class);
			LokiPullCursorStore cursorStore = context.getBean(LokiPullCursorStore.class);

			ProjectDto project = projectService.create("t21-persist-project-" + UUID.randomUUID(), "prod");
			projectId = project.id();

			connectorService.upsert(projectId, new LokiConnectorService.LokiConnectorRequest(
				"https://loki.example.com",
				"tenant-a",
				new LokiConnectorService.AuthRequest("bearer", "loki-secret-token", null, null),
				"{service=\"api\"}",
				30
			));
			llmAccountService.upsertApiKey(projectId, new LlmAccountService.ApiKeyUpsertCommand(
				"openai",
				"main",
				"sk-secret-api-key",
				"gpt-4o-mini",
				"https://api.openai.com/v1"
			));
			policyService.updateRedactionPolicy(projectId, new PolicyService.RedactionPolicyCommand(
				true,
				List.of(
					new PolicyService.RedactionRuleCommand("token", "token=[^\\s]+", "token=[REDACTED]"),
					new PolicyService.RedactionRuleCommand("password", "password=[^\\s]+", "password=[REDACTED]"),
					new PolicyService.RedactionRuleCommand("secret", "secret=[^\\s]+", "secret=[REDACTED]")
				)
			));
			alertService.configureEmail(projectId, "actor-a", new AlertService.ConfigureEmailCommand(
				"alerts@example.com",
				List.of("oncall@example.com"),
				new AlertService.SmtpCommand("smtp.example.com", 587, "mailer", "smtp-secret-password", true),
				0.5
			));
			incidentService.recordIngestedEvents(projectId, List.of(sampleEvent("evt-1")));
			cursorStore.commit(projectId, 42L);
		}

		try (ConfigurableApplicationContext context = startContext(dbPath, encryptionSecret)) {
			ProjectService projectService = context.getBean(ProjectService.class);
			LokiConnectorService connectorService = context.getBean(LokiConnectorService.class);
			LlmAccountService llmAccountService = context.getBean(LlmAccountService.class);
			PolicyService policyService = context.getBean(PolicyService.class);
			AlertService alertService = context.getBean(AlertService.class);
			IncidentService incidentService = context.getBean(IncidentService.class);
			LokiPullCursorStore cursorStore = context.getBean(LokiPullCursorStore.class);

			assertThat(projectService.existsById(projectId)).isTrue();
			assertThat(connectorService.findByProjectId(projectId)).isPresent();
			List<LlmAccountService.LlmAccount> llmAccounts = llmAccountService.list(projectId);
			assertThat(llmAccounts).hasSize(1);
			assertThat(llmAccounts.get(0).provider()).isEqualTo("openai");
			assertThat(policyService.redactForLlm(projectId, "token=abc password=pw secret=sec"))
				.isEqualTo("token=[REDACTED] password=[REDACTED] secret=[REDACTED]");
			assertThat(alertService.listAuditLogs(projectId, new AlertService.AuditLogQuery(null, null, null, 50)).data())
				.isNotEmpty();
			assertThat(incidentService.list(projectId, null, null, null, 50).data()).isNotEmpty();
			assertThat(cursorStore.readCursor(projectId)).isEqualTo(42L);
		}
	}

	@Test
	void storesSecretsEncryptedAndIngestTokensAsHashes() throws Exception {
		Path dbPath = tempDir.resolve("logcopilot-t21-secret.sqlite");
		deleteIfExists(dbPath);
		String encryptionSecret = "ephemeral-" + UUID.randomUUID();
		String projectId;
		Path[] effectiveDbPath = new Path[] { dbPath };

		try (ConfigurableApplicationContext context = startContext(dbPath, encryptionSecret)) {
			ProjectService projectService = context.getBean(ProjectService.class);
			LokiConnectorService connectorService = context.getBean(LokiConnectorService.class);
			LlmAccountService llmAccountService = context.getBean(LlmAccountService.class);
			AlertService alertService = context.getBean(AlertService.class);
			TokenHashStore tokenHashStore = context.getBean(TokenHashStore.class);
			PersistenceProperties persistenceProperties = context.getBean(PersistenceProperties.class);
			effectiveDbPath[0] = resolveSqlitePath(persistenceProperties.getSqlitePath());

			ProjectDto project = projectService.create("t21-secret-project-" + UUID.randomUUID(), "prod");
			projectId = project.id();

			connectorService.upsert(projectId, new LokiConnectorService.LokiConnectorRequest(
				"https://loki.example.com",
				"tenant-a",
				new LokiConnectorService.AuthRequest("basic", null, "loki-user", "loki-secret-password"),
				"{service=\"api\"}",
				30
			));
			llmAccountService.upsertApiKey(projectId, new LlmAccountService.ApiKeyUpsertCommand(
				"gemini",
				"gemini-main",
				"gsk-secret-api-key",
				"gemini-2.0-flash",
				null
			));
			alertService.configureEmail(projectId, "actor-a", new AlertService.ConfigureEmailCommand(
				"alerts@example.com",
				List.of("oncall@example.com"),
				new AlertService.SmtpCommand("smtp.example.com", 587, "mailer", "smtp-secret-password", true),
				0.5
			));

			assertThat(tokenHashStore.findTokenType("ingest-token")).contains("INGEST");
		}

		byte[] dbBytes = Files.readAllBytes(effectiveDbPath[0]);
		assertThat(dbBytes.length).isPositive();
		assertThat(contains(dbBytes, "loki-secret-password")).isFalse();
		assertThat(contains(dbBytes, "gsk-secret-api-key")).isFalse();
		assertThat(contains(dbBytes, "smtp-secret-password")).isFalse();
		assertThat(contains(dbBytes, "ingest-token")).isFalse();
	}

	@Test
	void migratesLegacyTokenHashSchemaAndPreservesTokenLookup() throws Exception {
		Path dbPath = tempDir.resolve("logcopilot-legacy-token-schema.sqlite");
		deleteIfExists(dbPath);
		String encryptionSecret = "ephemeral-" + UUID.randomUUID();
		String legacyToken = "legacy-ingest-token";

		createLegacyTokenHashTable(dbPath, legacyToken, "INGEST");

		try (ConfigurableApplicationContext context = startContext(dbPath, encryptionSecret, false)) {
			TokenHashStore tokenHashStore = context.getBean(TokenHashStore.class);
			assertThat(tokenHashStore.findTokenType(legacyToken)).contains("INGEST");
			assertThat(tokenHashStore.listTokens())
				.anyMatch(token -> "INGEST".equals(token.tokenType()) && "ingest".equals(token.tokenRole()));
		}
	}

	@Test
	void migratesLegacyApiTokensAndKeepsAtLeastOneOperatorRole() throws Exception {
		Path dbPath = tempDir.resolve("logcopilot-legacy-api-token-schema.sqlite");
		deleteIfExists(dbPath);
		String encryptionSecret = "ephemeral-" + UUID.randomUUID();
		String legacyApiToken = "legacy-api-token";

		createLegacyTokenHashTable(dbPath, legacyApiToken, "API");

		try (ConfigurableApplicationContext context = startContext(dbPath, encryptionSecret, false)) {
			TokenHashStore tokenHashStore = context.getBean(TokenHashStore.class);
			assertThat(tokenHashStore.findTokenType(legacyApiToken)).contains("API");
			assertThat(tokenHashStore.listTokens())
				.anyMatch(token -> "API".equals(token.tokenType()) && "operator".equals(token.tokenRole()));
		}
	}

	@Test
	void revokeTokenIsIdempotentForAlreadyRevokedToken() {
		Path dbPath = tempDir.resolve("logcopilot-revoke-idempotent.sqlite");
		deleteIfExists(dbPath);
		String encryptionSecret = "ephemeral-" + UUID.randomUUID();

		try (ConfigurableApplicationContext context = startContext(dbPath, encryptionSecret, false)) {
			TokenHashStore tokenHashStore = context.getBean(TokenHashStore.class);
			tokenHashStore.issueToken("token-idempotent", "plain-token-idempotent", "API", "api", "idempotent");

			TokenHashStore.TokenRecord firstRevoked = tokenHashStore.revokeToken("token-idempotent", "first-reason")
				.orElseThrow();
			TokenHashStore.TokenRecord secondRevoked = tokenHashStore.revokeToken("token-idempotent", "second-reason")
				.orElseThrow();

			assertThat(firstRevoked.status()).isEqualTo("revoked");
			assertThat(secondRevoked.status()).isEqualTo("revoked");
			assertThat(secondRevoked.revokedAt()).isEqualTo(firstRevoked.revokedAt());
			assertThat(secondRevoked.revocationReason()).isEqualTo("first-reason");
		}
	}

	private ConfigurableApplicationContext startContext(Path dbPath, String encryptionSecret) {
		return startContext(dbPath, encryptionSecret, true);
	}

	private ConfigurableApplicationContext startContext(
		Path dbPath,
		String encryptionSecret,
		boolean seedDefaultTokens
	) {
		return new SpringApplicationBuilder(LogcopilotApplication.class)
			.run(
				"--server.port=0",
				"--spring.task.scheduling.enabled=false",
				"--logcopilot.persistence.enabled=true",
				"--logcopilot.auth.seed-default-tokens=" + seedDefaultTokens,
				"--logcopilot.persistence.sqlite-path=" + dbPath.toAbsolutePath(),
				"--logcopilot.persistence.encryption-key=" + encryptionSecret,
				"--logcopilot.llm.oauth.mode=" + LlmOAuthProperties.Mode.STUB.name().toLowerCase()
			);
	}

	private void deleteIfExists(Path dbPath) {
		try {
			Files.deleteIfExists(dbPath);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to clean sqlite file: " + dbPath, exception);
		}
	}

	private Path resolveSqlitePath(String configuredPath) {
		if (configuredPath == null || configuredPath.isBlank()) {
			return Path.of("./data/logcopilot.sqlite").toAbsolutePath();
		}
		String normalized = configuredPath.trim();
		if (normalized.startsWith("jdbc:sqlite:")) {
			normalized = normalized.substring("jdbc:sqlite:".length());
		}
		return Path.of(normalized).toAbsolutePath();
	}

	private CanonicalLogEvent sampleEvent(String eventId) {
		return new CanonicalLogEvent(
			eventId,
			"2026-03-04T00:00:00Z",
			"api",
			"error",
			"database timeout",
			null,
			null,
			null,
			null
		);
	}

	private boolean contains(byte[] source, String plainText) {
		return new String(source, StandardCharsets.UTF_8).contains(plainText);
	}

	private void createLegacyTokenHashTable(Path dbPath, String plainToken, String tokenType) throws Exception {
		String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
		try (java.sql.Connection connection = java.sql.DriverManager.getConnection(jdbcUrl);
		     java.sql.Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				create table if not exists bearer_token_hashes (
					token_hash text primary key,
					token_type text not null,
					created_at text not null
				)
				""");
			try (java.sql.PreparedStatement prepared = connection.prepareStatement(
				"insert into bearer_token_hashes(token_hash, token_type, created_at) values (?, ?, ?)"
			)) {
				prepared.setString(1, hash(plainToken));
				prepared.setString(2, tokenType);
				prepared.setString(3, "2026-03-04T00:00:00Z");
				prepared.executeUpdate();
			}
		}
	}

	private String hash(String plainToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(bytes);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to hash token for test setup", exception);
		}
	}
}
