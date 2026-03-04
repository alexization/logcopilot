package com.logcopilot.incident;

import com.logcopilot.common.auth.BearerTokenValidator;
import com.logcopilot.common.security.SecurityConfiguration;
import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import com.logcopilot.incident.domain.IncidentDetail;
import com.logcopilot.incident.domain.IncidentStatus;
import com.logcopilot.incident.domain.ReanalyzeAcceptedResult;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import(SecurityConfiguration.class)
class IncidentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private IncidentService incidentService;

	@MockBean
	private ProjectService projectService;

	@MockBean
	private BearerTokenValidator bearerTokenValidator;

	@BeforeEach
	void setUpAuthenticationStub() {
		when(bearerTokenValidator.validate("Bearer token")).thenReturn("token");
	}

	@Test
	@DisplayName("IncidentController는 incident의 project가 없으면 상세 조회에 404를 반환한다")
	void getIncidentReturns404WhenIncidentProjectMissing() throws Exception {
		IncidentDetail detail = incidentDetail("incident-1", "missing-project", IncidentStatus.OPEN);
		when(incidentService.getIncident("incident-1")).thenReturn(detail);
		when(projectService.existsById("missing-project")).thenReturn(false);

		mockMvc.perform(get("/v1/incidents/{incident_id}", "incident-1")
				.header("Authorization", "Bearer token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
	}

	@Test
	@DisplayName("IncidentController는 incident의 project가 없으면 재분석에 404를 반환한다")
	void reanalyzeIncidentReturns404WhenIncidentProjectMissing() throws Exception {
		IncidentDetail detail = incidentDetail("incident-1", "missing-project", IncidentStatus.OPEN);
		when(incidentService.getIncident("incident-1")).thenReturn(detail);
		when(projectService.existsById("missing-project")).thenReturn(false);

		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", "incident-1")
				.header("Authorization", "Bearer token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"retry\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));

		verify(incidentService, never()).reanalyzeIncident("incident-1", "retry");
	}

	@Test
	@DisplayName("IncidentController는 incident의 project가 있으면 재분석을 진행한다")
	void reanalyzeIncidentReturns202WhenIncidentProjectExists() throws Exception {
		IncidentDetail detail = incidentDetail("incident-1", "project-1", IncidentStatus.OPEN);
		when(incidentService.getIncident("incident-1")).thenReturn(detail);
		when(projectService.existsById("project-1")).thenReturn(true);
		when(incidentService.reanalyzeIncident("incident-1", "retry"))
			.thenReturn(new ReanalyzeAcceptedResult(true, "job-1"));

		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", "incident-1")
				.header("Authorization", "Bearer token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"retry\"}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.accepted").value(true))
			.andExpect(jsonPath("$.data.job_id").value("job-1"));
	}

	@Test
	@DisplayName("IncidentController는 reason 길이가 500자를 초과하면 422를 반환한다")
	void reanalyzeIncidentReturns422WhenReasonTooLong() throws Exception {
		String reason = "a".repeat(501);

		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", "incident-1")
				.header("Authorization", "Bearer token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"" + reason + "\"}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("reason must be at most 500 characters"));

		verify(incidentService, never()).getIncident("incident-1");
		verify(incidentService, never()).reanalyzeIncident("incident-1", reason);
	}

	private IncidentDetail incidentDetail(String incidentId, String projectId, IncidentStatus status) {
		return new IncidentDetail(
			incidentId,
			projectId,
			status,
			"api",
			80,
			3,
			Instant.parse("2026-03-03T03:00:00Z"),
			Instant.parse("2026-03-03T03:05:00Z"),
			new AnalysisReport(
				"summary",
				List.of(new Hypothesis("cause", 0.5, List.of("evidence"))),
				List.of("next"),
				List.of("limitation")
			)
		);
	}
}
