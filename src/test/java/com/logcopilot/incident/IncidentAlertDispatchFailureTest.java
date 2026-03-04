package com.logcopilot.incident;

import com.logcopilot.alert.AlertService;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.incident.analyzer.IncidentAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentAlertDispatchFailureTest {

	@Mock
	private AlertService alertService;

	@Test
	@DisplayName("IncidentService는 alert dispatch 실패 시 실패 audit 기록 메서드를 호출한다")
	void recordsDispatchFailureWhenDispatchThrows() {
		IncidentAnalyzer analyzer = command -> command.previousReport();
		IncidentService incidentService = new IncidentService(analyzer, alertService);
		when(alertService.dispatchIncidentAlert(anyString(), any()))
			.thenThrow(new RuntimeException("dispatch failed"));
		doNothing().when(alertService).recordDispatchFailure(anyString(), any(), anyString());

		incidentService.recordIngestedEvents("project-1", List.of(event()));

		ArgumentCaptor<AlertService.DispatchIncidentAlertCommand> commandCaptor =
			ArgumentCaptor.forClass(AlertService.DispatchIncidentAlertCommand.class);
		ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
		verify(alertService, times(1)).recordDispatchFailure(anyString(), commandCaptor.capture(), reasonCaptor.capture());
		AlertService.DispatchIncidentAlertCommand command = commandCaptor.getValue();
		assertThat(command.service()).isEqualTo("api");
		assertThat(command.severityScore()).isEqualTo(80);
		assertThat(command.actorToken()).isEqualTo("system:incident");
		assertThat(reasonCaptor.getValue()).contains("dispatch failed");
	}

	private CanonicalLogEvent event() {
		return new CanonicalLogEvent(
			"evt-1",
			"2026-03-03T03:00:00Z",
			"api",
			"error",
			"failure",
			null,
			null,
			null,
			Map.of()
		);
	}
}
