package com.logcopilot.alert;

import com.logcopilot.LogcopilotApplication;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPersistenceTest {

	@TempDir
	Path tempDir;

	@Test
	void keepsAuditLogsAppendOnlyAcrossRestart() {
		Path dbPath = tempDir.resolve("logcopilot-t21-audit.sqlite");
		deleteIfExists(dbPath);
		String encryptionSecret = "ephemeral-" + UUID.randomUUID();
		String projectId;
		List<String> firstLogIds;

		try (ConfigurableApplicationContext context = startContext(dbPath, encryptionSecret)) {
			ProjectService projectService = context.getBean(ProjectService.class);
			AlertService alertService = context.getBean(AlertService.class);

			ProjectDto project = projectService.create("t21-audit-project-" + UUID.randomUUID(), "prod");
			projectId = project.id();

			alertService.configureSlack(
				projectId,
				"actor-a",
				new AlertService.ConfigureSlackCommand(
					"https://hooks.slack.com/services/T000/B000/AAA",
					"#ops",
					0.45
				)
			);
			alertService.configureEmail(
				projectId,
				"actor-b",
				new AlertService.ConfigureEmailCommand(
					"alerts@example.com",
					List.of("oncall@example.com"),
					new AlertService.SmtpCommand("smtp.example.com", 587, "mailer", "smtp-secret", true),
					0.55
				)
			);

			firstLogIds = alertService.listAuditLogs(projectId, new AlertService.AuditLogQuery(null, null, null, 50))
				.data()
				.stream()
				.map(AlertService.AuditLog::id)
				.toList();
			assertThat(firstLogIds).hasSize(2);
		}

		try (ConfigurableApplicationContext context = startContext(dbPath, encryptionSecret)) {
			AlertService alertService = context.getBean(AlertService.class);
			List<AlertService.AuditLog> restored = alertService.listAuditLogs(
				projectId,
				new AlertService.AuditLogQuery(null, null, null, 50)
			).data();

			assertThat(restored).hasSize(2);
			assertThat(restored.stream().map(AlertService.AuditLog::id).toList())
				.containsExactlyInAnyOrderElementsOf(firstLogIds);

			alertService.configureSlack(
				projectId,
				"actor-c",
				new AlertService.ConfigureSlackCommand(
					"https://hooks.slack.com/services/T000/B000/BBB",
					"#platform",
					0.65
				)
			);

			List<AlertService.AuditLog> afterAppend = alertService.listAuditLogs(
				projectId,
				new AlertService.AuditLogQuery(null, null, null, 50)
			).data();

			assertThat(afterAppend).hasSize(3);
			assertThat(afterAppend.stream().map(AlertService.AuditLog::id).toList())
				.containsAll(firstLogIds);
		}
	}

	private ConfigurableApplicationContext startContext(Path dbPath, String encryptionSecret) {
		return new SpringApplicationBuilder(LogcopilotApplication.class)
			.run(
				"--server.port=0",
				"--spring.task.scheduling.enabled=false",
				"--logcopilot.persistence.enabled=true",
				"--logcopilot.persistence.sqlite-path=" + dbPath.toAbsolutePath(),
				"--logcopilot.persistence.encryption-key=" + encryptionSecret
			);
	}

	private void deleteIfExists(Path dbPath) {
		try {
			Files.deleteIfExists(dbPath);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to clean sqlite file: " + dbPath, exception);
		}
	}
}
