package com.logcopilot.alert;

import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertServiceTest {

	private final ProjectService projectService = new ProjectService();
	private final AlertService alertService = new AlertService(projectService);

	@Test
	@DisplayName("AlertService는 Slack 채널을 생성 후 동일 타입 요청을 갱신하고 감사 로그를 남긴다")
	void configureSlackCreatesThenUpdatesAndAppendsAuditLog() {
		ProjectDto project = projectService.create("alert-service-slack", "prod");

		AlertService.ConfigureResult created = alertService.configureSlack(
			project.id(),
			"actor-a",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/AAA",
				"#ops",
				0.45
			)
		);

		AlertService.ConfigureResult updated = alertService.configureSlack(
			project.id(),
			"actor-a",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/BBB",
				"#platform",
				0.70
			)
		);

		AlertService.AuditLogListResult logs = alertService.listAuditLogs(
			project.id(),
			new AlertService.AuditLogQuery(null, null, null, 50)
		);

		assertThat(created.created()).isTrue();
		assertThat(updated.created()).isFalse();
		assertThat(updated.channel().id()).isEqualTo(created.channel().id());
		assertThat(logs.data()).hasSize(2);
		assertThat(logs.data().get(0).action()).isEqualTo("alert.slack.configured");
	}

	@Test
	@DisplayName("AlertService는 Email 요청의 주소/수신자 유효성을 검증한다")
	void configureEmailValidatesAddresses() {
		ProjectDto project = projectService.create("alert-service-email", "prod");

		assertThatThrownBy(() -> alertService.configureEmail(
			project.id(),
			"actor-a",
			new AlertService.ConfigureEmailCommand(
				"invalid-email",
				List.of("oncall@example.com"),
				new AlertService.SmtpCommand("smtp.example.com", 587, "user", "secret", true),
				0.45
			)
		))
			.isInstanceOf(ValidationException.class)
			.hasMessage("from must be a valid email");

		assertThatThrownBy(() -> alertService.configureEmail(
			project.id(),
			"actor-a",
			new AlertService.ConfigureEmailCommand(
				"alerts@example.com",
				List.of(),
				new AlertService.SmtpCommand("smtp.example.com", 587, "user", "secret", true),
				0.45
			)
		))
			.isInstanceOf(ValidationException.class)
			.hasMessage("recipients must contain at least 1 email");
	}

	@Test
	@DisplayName("AlertService는 존재하지 않는 프로젝트 알림 설정 요청 시 ValidationException을 던진다")
	void configureThrowsValidationWhenProjectMissing() {
		assertThatThrownBy(() -> alertService.configureSlack(
			"missing-project",
			"actor-a",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/AAA",
				"#ops",
				0.45
			)
		))
			.isInstanceOf(ValidationException.class)
			.hasMessage("Project not found");
	}

	@Test
	@DisplayName("AlertService는 audit 로그 조회에서 action/actor/cursor/limit을 적용한다")
	void listAuditLogsAppliesFiltersAndPagination() {
		ProjectDto project = projectService.create("alert-service-audit", "prod");

		alertService.configureSlack(
			project.id(),
			"actor-a",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/AAA",
				"#ops",
				0.45
			)
		);
		alertService.configureEmail(
			project.id(),
			"actor-b",
			new AlertService.ConfigureEmailCommand(
				"alerts@example.com",
				List.of("oncall@example.com"),
				new AlertService.SmtpCommand("smtp.example.com", 587, "user", "secret", true),
				0.7
			)
		);

		AlertService.AuditLogListResult first = alertService.listAuditLogs(
			project.id(),
			new AlertService.AuditLogQuery(null, null, null, 1)
		);
		AlertService.AuditLogListResult second = alertService.listAuditLogs(
			project.id(),
			new AlertService.AuditLogQuery(null, null, first.nextCursor(), 1)
		);
		AlertService.AuditLogListResult byAction = alertService.listAuditLogs(
			project.id(),
			new AlertService.AuditLogQuery("alert.email.configured", null, null, 50)
		);
		String actor = first.data().get(0).actor();
		AlertService.AuditLogListResult byActor = alertService.listAuditLogs(
			project.id(),
			new AlertService.AuditLogQuery(null, actor, null, 50)
		);

		assertThat(first.data()).hasSize(1);
		assertThat(first.nextCursor()).isEqualTo("1");
		assertThat(second.data()).hasSize(1);
		assertThat(byAction.data()).hasSize(1);
		assertThat(byAction.data().get(0).action()).isEqualTo("alert.email.configured");
		assertThat(byAction.data().get(0).metadata().get("from")).isEqualTo("domain:example.com");
		assertThat(byActor.data()).allMatch(log -> log.actor().equals(actor));
	}

	@Test
	@DisplayName("AlertService는 audit 조회 limit 범위를 벗어나면 ValidationException을 던진다")
	void listAuditLogsThrowsWhenLimitOutOfRange() {
		ProjectDto project = projectService.create("alert-service-limit", "prod");

		assertThatThrownBy(() -> alertService.listAuditLogs(
			project.id(),
			new AlertService.AuditLogQuery(null, null, null, 201)
		))
			.isInstanceOf(ValidationException.class)
			.hasMessage("limit must be between 1 and 200");
	}

	@Test
	@DisplayName("AlertService는 프로젝트별 audit 로그를 retention 상한까지만 유지한다")
	void listAuditLogsKeepsOnlyRecentLogsWithinRetentionLimit() {
		ProjectDto project = projectService.create("alert-service-retention", "prod");
		AlertService limitedAlertService = new AlertService(projectService, 2);

		limitedAlertService.configureSlack(
			project.id(),
			"actor-old",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/OLD",
				"#old",
				0.45
			)
		);
		limitedAlertService.configureEmail(
			project.id(),
			"actor-mid",
			new AlertService.ConfigureEmailCommand(
				"alerts@example.com",
				List.of("oncall@example.com"),
				new AlertService.SmtpCommand("smtp.example.com", 587, "user", "secret", true),
				0.5
			)
		);
		limitedAlertService.configureSlack(
			project.id(),
			"actor-new",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/NEW",
				"#new",
				0.9
			)
		);

		AlertService.AuditLogListResult logs = limitedAlertService.listAuditLogs(
			project.id(),
			new AlertService.AuditLogQuery(null, null, null, 50)
		);

		assertThat(logs.data()).hasSize(2);
		assertThat(logs.data())
			.noneMatch(log -> "#old".equals(log.metadata().get("channel")));
		assertThat(logs.data())
			.anyMatch(log -> "#new".equals(log.metadata().get("channel")));
	}

	@Test
	@DisplayName("AlertService는 존재하지 않는 프로젝트 audit 조회 시 NotFoundException을 던진다")
	void listAuditLogsThrowsNotFoundWhenProjectMissing() {
		assertThatThrownBy(() -> alertService.listAuditLogs(
			"missing-project",
			new AlertService.AuditLogQuery(null, null, null, 50)
		))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Project not found");
	}
}
