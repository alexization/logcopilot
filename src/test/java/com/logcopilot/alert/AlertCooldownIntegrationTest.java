package com.logcopilot.alert;

import com.logcopilot.ingest.IngestService;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.ingest.domain.IngestEventsCommand;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
	"logcopilot.alert.storm.cooldown=PT10M",
	"logcopilot.alert.storm.rate-budget=10",
	"logcopilot.alert.storm.rate-window=PT1H",
	"logcopilot.alert.storm.quiet-hours.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AlertCooldownIntegrationTest {

	@Autowired
	private ProjectService projectService;

	@Autowired
	private AlertService alertService;

	@Autowired
	private IngestService ingestService;

	@Test
	@DisplayName("Incident ingest 경로는 alert dispatch cooldown 정책을 적용한다")
	void ingestPipelineAppliesAlertDispatchCooldownPolicy() {
		ProjectDto project = projectService.create("alert-cooldown-integration", "prod");
		alertService.configureSlack(
			project.id(),
			"actor-a",
			new AlertService.ConfigureSlackCommand(
				"https://hooks.slack.com/services/T000/B000/AAA",
				"#ops",
				0.45
			)
		);

		ingestService.ingestEvents("idem-1", ingestCommand(project.id(), "batch-1", "evt-1"));
		ingestService.ingestEvents("idem-2", ingestCommand(project.id(), "batch-2", "evt-2"));

		AlertService.AuditLogListResult dispatched = alertService.listAuditLogs(
			project.id(),
			new AlertService.AuditLogQuery("alert.dispatched", null, null, 50)
		);
		AlertService.AuditLogListResult suppressed = alertService.listAuditLogs(
			project.id(),
			new AlertService.AuditLogQuery("alert.dispatch.suppressed", null, null, 50)
		);

		assertThat(dispatched.data()).hasSize(1);
		assertThat(dispatched.data().get(0).resourceType()).isEqualTo("incident");
		assertThat(suppressed.data()).hasSize(1);
		assertThat(suppressed.data().get(0).resourceType()).isEqualTo("incident");
		assertThat(suppressed.data().get(0).metadata().get("reason")).isEqualTo("cooldown");
	}

	private IngestEventsCommand ingestCommand(String projectId, String batchId, String eventId) {
		return new IngestEventsCommand(
			projectId,
			"loki",
			batchId,
			List.of(new CanonicalLogEvent(
				eventId,
				"2026-03-03T03:00:00Z",
				"api",
				"error",
				"failure",
				null,
				null,
				null,
				Map.of()
			))
		);
	}
}
