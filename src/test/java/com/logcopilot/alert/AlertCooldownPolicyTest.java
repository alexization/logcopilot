package com.logcopilot.alert;

import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertCooldownPolicyTest {

	@Test
	@DisplayName("AlertService는 cooldown 시간 내 중복 전송을 억제한다")
	void suppressesDispatchWithinCooldownWindow() {
		MutableClock clock = new MutableClock(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC);
		ProjectService projectService = new ProjectService();
		AlertService alertService = new AlertService(
			projectService,
			5_000,
			new AlertService.AlertStormPolicy(
				Duration.ofMinutes(5),
				10,
				Duration.ofHours(1),
				false,
				LocalTime.of(23, 0),
				LocalTime.of(7, 0),
				ZoneOffset.UTC
			),
			clock
		);
		ProjectDto project = projectService.create("alert-cooldown", "prod");

		alertService.configureSlack(
			project.id(),
			"actor-a",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/AAA",
				"#ops",
				0.45
			)
		);

		AlertService.AlertDispatchResult first = alertService.dispatchIncidentAlert(
			project.id(),
			new AlertService.DispatchIncidentAlertCommand("inc-1", "api", 80, "system")
		);
		AlertService.AlertDispatchResult second = alertService.dispatchIncidentAlert(
			project.id(),
			new AlertService.DispatchIncidentAlertCommand("inc-2", "api", 80, "system")
		);

		assertThat(first.dispatched()).isTrue();
		assertThat(first.reason()).isEqualTo("dispatched");
		assertThat(second.dispatched()).isFalse();
		assertThat(second.reason()).isEqualTo("cooldown");
	}

	@Test
	@DisplayName("AlertService는 서비스 단위로 cooldown/rate-budget을 독립 적용한다")
	void appliesStormPolicyPerServiceScope() {
		MutableClock clock = new MutableClock(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC);
		ProjectService projectService = new ProjectService();
		AlertService alertService = new AlertService(
			projectService,
			5_000,
			new AlertService.AlertStormPolicy(
				Duration.ofMinutes(5),
				1,
				Duration.ofHours(1),
				false,
				LocalTime.of(23, 0),
				LocalTime.of(7, 0),
				ZoneOffset.UTC
			),
			clock
		);
		ProjectDto project = projectService.create("alert-service-scope", "prod");
		alertService.configureSlack(
			project.id(),
			"actor-a",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/AAA",
				"#ops",
				0.45
			)
		);

		AlertService.AlertDispatchResult apiFirst = alertService.dispatchIncidentAlert(
			project.id(),
			new AlertService.DispatchIncidentAlertCommand("inc-1", "api", 80, "system")
		);
		AlertService.AlertDispatchResult workerFirst = alertService.dispatchIncidentAlert(
			project.id(),
			new AlertService.DispatchIncidentAlertCommand("inc-2", "worker", 80, "system")
		);
		AlertService.AlertDispatchResult apiSecond = alertService.dispatchIncidentAlert(
			project.id(),
			new AlertService.DispatchIncidentAlertCommand("inc-3", "api", 80, "system")
		);

		assertThat(apiFirst.dispatched()).isTrue();
		assertThat(workerFirst.dispatched()).isTrue();
		assertThat(apiSecond.dispatched()).isFalse();
		assertThat(apiSecond.reason()).isEqualTo("cooldown");
	}

	@Test
	@DisplayName("AlertService는 quiet-hours 구간에서 전송을 억제한다")
	void suppressesDispatchDuringQuietHours() {
		MutableClock clock = new MutableClock(Instant.parse("2026-03-04T01:00:00Z"), ZoneOffset.UTC);
		ProjectService projectService = new ProjectService();
		AlertService alertService = new AlertService(
			projectService,
			5_000,
			new AlertService.AlertStormPolicy(
				Duration.ZERO,
				10,
				Duration.ofHours(1),
				true,
				LocalTime.of(0, 0),
				LocalTime.of(6, 0),
				ZoneOffset.UTC
			),
			clock
		);
		ProjectDto project = projectService.create("alert-quiet-hours", "prod");

		alertService.configureSlack(
			project.id(),
			"actor-a",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/AAA",
				"#ops",
				0.45
			)
		);

		AlertService.AlertDispatchResult result = alertService.dispatchIncidentAlert(
			project.id(),
			new AlertService.DispatchIncidentAlertCommand("inc-1", "api", 80, "system")
		);

		assertThat(result.dispatched()).isFalse();
		assertThat(result.reason()).isEqualTo("quiet_hours");
	}

	@Test
	@DisplayName("AlertService는 rate-budget 초과 시 전송을 억제한다")
	void suppressesDispatchWhenRateBudgetExceeded() {
		MutableClock clock = new MutableClock(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC);
		ProjectService projectService = new ProjectService();
		AlertService alertService = new AlertService(
			projectService,
			5_000,
			new AlertService.AlertStormPolicy(
				Duration.ZERO,
				2,
				Duration.ofHours(1),
				false,
				LocalTime.of(23, 0),
				LocalTime.of(7, 0),
				ZoneOffset.UTC
			),
			clock
		);
		ProjectDto project = projectService.create("alert-rate-budget", "prod");

		alertService.configureEmail(
			project.id(),
			"actor-a",
			new AlertService.ConfigureEmailCommand(
				"alerts@example.com",
				List.of("oncall@example.com"),
				new AlertService.SmtpCommand("smtp.example.com", 587, "user", "secret", true),
				0.45
			)
		);

		AlertService.AlertDispatchResult first = alertService.dispatchIncidentAlert(
			project.id(),
			new AlertService.DispatchIncidentAlertCommand("inc-1", "api", 80, "system")
		);
		AlertService.AlertDispatchResult second = alertService.dispatchIncidentAlert(
			project.id(),
			new AlertService.DispatchIncidentAlertCommand("inc-2", "api", 80, "system")
		);
		AlertService.AlertDispatchResult third = alertService.dispatchIncidentAlert(
			project.id(),
			new AlertService.DispatchIncidentAlertCommand("inc-3", "api", 80, "system")
		);

		assertThat(first.dispatched()).isTrue();
		assertThat(second.dispatched()).isTrue();
		assertThat(third.dispatched()).isFalse();
		assertThat(third.reason()).isEqualTo("rate_budget_exceeded");
	}

	private static final class MutableClock extends Clock {
		private Instant instant;
		private final ZoneId zoneId;

		private MutableClock(Instant instant, ZoneId zoneId) {
			this.instant = instant;
			this.zoneId = zoneId;
		}

		@Override
		public ZoneId getZone() {
			return zoneId;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
