package com.logcopilot.incident;

import com.logcopilot.common.error.ConflictException;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.incident.domain.IncidentDetail;
import com.logcopilot.incident.domain.IncidentListResult;
import com.logcopilot.incident.domain.IncidentStatus;
import com.logcopilot.incident.domain.ReanalyzeAcceptedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentServiceTest {

	private final IncidentService incidentService = new IncidentService();

	@Test
	@DisplayName("IncidentService는 ingest 이벤트를 서비스 단위 incident로 기록한다")
	void recordIngestedEventsCreatesIncidentsByService() {
		incidentService.recordIngestedEvents("project-1", List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "api error"),
			event("evt-2", "2026-03-03T03:00:30Z", "worker", "warn", "worker warn")
		));

		IncidentListResult result = incidentService.list("project-1", null, null, null, null);

		assertThat(result.data()).hasSize(2);
		assertThat(result.data()).extracting(it -> it.status().value())
			.containsOnly("open");
		assertThat(result.data()).extracting(it -> it.service())
			.containsExactly("api", "worker");
	}

	@Test
	@DisplayName("IncidentService는 cursor/limit으로 incident 목록을 페이지네이션한다")
	void listIncidentsPaginatesWithCursorAndLimit() {
		incidentService.recordIngestedEvents("project-1", List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "api error"),
			event("evt-2", "2026-03-03T03:00:30Z", "worker", "warn", "worker warn"),
			event("evt-3", "2026-03-03T03:01:00Z", "web", "info", "web info")
		));

		IncidentListResult firstPage = incidentService.list("project-1", null, null, null, 1);
		IncidentListResult secondPage = incidentService.list("project-1", null, null, firstPage.nextCursor(), 1);

		assertThat(firstPage.data()).hasSize(1);
		assertThat(firstPage.nextCursor()).isEqualTo("1");
		assertThat(secondPage.data()).hasSize(1);
	}

	@Test
	@DisplayName("IncidentService는 존재하지 않는 incident 조회 시 NotFoundException을 던진다")
	void getIncidentThrowsWhenMissing() {
		assertThatThrownBy(() -> incidentService.getIncident("missing-incident"))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Incident not found");
	}

	@Test
	@DisplayName("IncidentService는 재분석 요청 시 상태를 investigating으로 전이한다")
	void reanalyzeIncidentTransitionsStatusToInvestigating() {
		incidentService.recordIngestedEvents("project-1", List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "api error")
		));
		String incidentId = incidentService.list("project-1", null, null, null, null).data().get(0).id();

		ReanalyzeAcceptedResult accepted = incidentService.reanalyzeIncident(incidentId, "follow-up");
		IncidentDetail detail = incidentService.getIncident(incidentId);

		assertThat(accepted.accepted()).isTrue();
		assertThat(accepted.jobId()).isNotBlank();
		assertThat(detail.status()).isEqualTo(IncidentStatus.INVESTIGATING);
	}

	@Test
	@DisplayName("IncidentService는 investigating 상태에서 재분석 재요청 시 ConflictException을 던진다")
	void reanalyzeIncidentThrowsConflictWhenAlreadyInvestigating() {
		incidentService.recordIngestedEvents("project-1", List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "api error")
		));
		String incidentId = incidentService.list("project-1", null, null, null, null).data().get(0).id();
		incidentService.reanalyzeIncident(incidentId, "first");

		assertThatThrownBy(() -> incidentService.reanalyzeIncident(incidentId, "second"))
			.isInstanceOf(ConflictException.class)
			.hasMessage("Incident reanalysis already in progress");
	}

	private CanonicalLogEvent event(
		String eventId,
		String timestamp,
		String service,
		String severity,
		String message
	) {
		return new CanonicalLogEvent(
			eventId,
			timestamp,
			service,
			severity,
			message,
			null,
			null,
			null,
			Map.of()
		);
	}
}
