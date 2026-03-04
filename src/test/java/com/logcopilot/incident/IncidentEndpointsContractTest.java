package com.logcopilot.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IncidentEndpointsContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("GET /v1/projects/{project_id}/incidents 는 인증 요청에서 incident 목록을 반환한다")
	void listIncidentsReturns200WhenAuthorized() throws Exception {
		String projectId = createProjectId("incident-list");
		ingestEvents(projectId, List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "first error"),
			event("evt-2", "2026-03-03T03:00:30Z", "worker", "warn", "worker warn")
		));

		mockMvc.perform(get("/v1/projects/{project_id}/incidents", projectId)
				.header("Authorization", "Bearer incident-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data[0].id").isString())
			.andExpect(jsonPath("$.data[0].project_id").value(projectId))
			.andExpect(jsonPath("$.data[0].status").isString())
			.andExpect(jsonPath("$.data[0].service").isString())
			.andExpect(jsonPath("$.data[0].severity_score").isNumber())
			.andExpect(jsonPath("$.data[0].event_count").isNumber())
			.andExpect(jsonPath("$.data[0].first_seen").isString())
			.andExpect(jsonPath("$.data[0].last_seen").isString())
			.andExpect(jsonPath("$.meta.request_id").isString());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/incidents 는 status/service 쿼리로 필터링한다")
	void listIncidentsAppliesStatusAndServiceFilters() throws Exception {
		String projectId = createProjectId("incident-filter");
		ingestEvents(projectId, List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "api error"),
			event("evt-2", "2026-03-03T03:00:30Z", "worker", "warn", "worker warn")
		));

		String incidentId = firstIncidentId(projectId);
		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", incidentId)
				.header("Authorization", "Bearer incident-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"investigate\"}"))
			.andExpect(status().isAccepted());

		mockMvc.perform(get("/v1/projects/{project_id}/incidents", projectId)
				.header("Authorization", "Bearer incident-token")
				.queryParam("status", "investigating"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(incidentId))
			.andExpect(jsonPath("$.data[0].status").value("investigating"));

		mockMvc.perform(get("/v1/projects/{project_id}/incidents", projectId)
				.header("Authorization", "Bearer incident-token")
				.queryParam("service", "unknown-service"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/incidents 는 service 대소문자를 구분하지 않고 집계한다")
	void listIncidentsAggregatesServicesCaseInsensitively() throws Exception {
		String projectId = createProjectId("incident-service-normalize");
		ingestEvents(projectId, List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "API", "error", "api upper"),
			event("evt-2", "2026-03-03T03:00:30Z", "api", "warn", "api lower")
		));

		mockMvc.perform(get("/v1/projects/{project_id}/incidents", projectId)
				.header("Authorization", "Bearer incident-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].service").value("api"))
			.andExpect(jsonPath("$.data[0].event_count").value(2));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/incidents 는 인증 누락 시 401을 반환한다")
	void listIncidentsRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("incident-auth");

		mockMvc.perform(get("/v1/projects/{project_id}/incidents", projectId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/incidents 는 프로젝트가 없으면 404를 반환한다")
	void listIncidentsReturns404WhenProjectMissing() throws Exception {
		mockMvc.perform(get("/v1/projects/{project_id}/incidents", "missing-project")
				.header("Authorization", "Bearer incident-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
	}

	@Test
	@DisplayName("GET /v1/incidents/{incident_id} 는 상세 incident를 반환한다")
	void getIncidentReturns200ForExistingIncident() throws Exception {
		String projectId = createProjectId("incident-detail");
		ingestEvents(projectId, List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "first error")
		));
		String incidentId = firstIncidentId(projectId);

		mockMvc.perform(get("/v1/incidents/{incident_id}", incidentId)
				.header("Authorization", "Bearer incident-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(incidentId))
			.andExpect(jsonPath("$.data.project_id").value(projectId))
			.andExpect(jsonPath("$.data.report.summary").isString())
			.andExpect(jsonPath("$.data.report.hypotheses").isArray())
			.andExpect(jsonPath("$.data.report.next_actions").isArray())
			.andExpect(jsonPath("$.data.report.limitations").isArray());
	}

	@Test
	@DisplayName("GET /v1/incidents/{incident_id} 는 incident가 없으면 404를 반환한다")
	void getIncidentReturns404WhenIncidentMissing() throws Exception {
		mockMvc.perform(get("/v1/incidents/{incident_id}", "missing-incident")
				.header("Authorization", "Bearer incident-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Incident not found"));
	}

	@Test
	@DisplayName("POST /v1/incidents/{incident_id}/reanalyze 는 재분석 요청을 수락한다")
	void reanalyzeIncidentReturns202WhenAccepted() throws Exception {
		String projectId = createProjectId("incident-reanalyze");
		ingestEvents(projectId, List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "first error")
		));
		String incidentId = firstIncidentId(projectId);

		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", incidentId)
				.header("Authorization", "Bearer incident-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"new evidence\"}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.accepted").value(true))
			.andExpect(jsonPath("$.data.job_id").isString());
	}

	@Test
	@DisplayName("POST /v1/incidents/{incident_id}/reanalyze 는 LLM 계정이 있으면 LLM 분석 리포트를 생성한다")
	void reanalyzeIncidentUsesLlmReportWhenAccountExists() throws Exception {
		String projectId = createProjectId("incident-reanalyze-llm");
		registerOpenAiLlmAccount(projectId);
		configureRedactionPolicy(projectId);
		ingestEvents(projectId, List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "first error")
		));
		String incidentId = firstIncidentId(projectId);

		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", incidentId)
				.header("Authorization", "Bearer incident-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"new evidence token=abc password=pw-1\"}"))
			.andExpect(status().isAccepted());

		MvcResult detail = mockMvc.perform(get("/v1/incidents/{incident_id}", incidentId)
				.header("Authorization", "Bearer incident-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.report.summary").value(org.hamcrest.Matchers.containsString("LLM-assisted")))
			.andExpect(jsonPath("$.data.report.limitations[*]")
				.value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("LLM"))))
			.andReturn();

		String detailBody = detail.getResponse().getContentAsString();
		assertThat(detailBody)
			.contains("[REDACTED]")
			.doesNotContain("token=abc")
			.doesNotContain("password=pw-1");
	}

	@Test
	@DisplayName("POST /v1/incidents/{incident_id}/reanalyze 는 LLM 계정이 없으면 fallback 리포트를 생성한다")
	void reanalyzeIncidentUsesFallbackReportWhenLlmUnavailable() throws Exception {
		String projectId = createProjectId("incident-reanalyze-fallback");
		ingestEvents(projectId, List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "first error")
		));
		String incidentId = firstIncidentId(projectId);

		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", incidentId)
				.header("Authorization", "Bearer incident-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"new evidence token=abc password=pw-1\"}"))
			.andExpect(status().isAccepted());

		MvcResult detail = mockMvc.perform(get("/v1/incidents/{incident_id}", incidentId)
				.header("Authorization", "Bearer incident-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.report.limitations[*]")
				.value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("fallback report generated"))))
			.andReturn();

		String detailBody = detail.getResponse().getContentAsString();
		assertThat(detailBody)
			.contains("[REDACTED]")
			.doesNotContain("token=abc")
			.doesNotContain("password=pw-1");
	}

	@Test
	@DisplayName("POST /v1/incidents/{incident_id}/reanalyze 는 진행 중 상태에서 409를 반환한다")
	void reanalyzeIncidentReturns409WhenAlreadyInvestigating() throws Exception {
		String projectId = createProjectId("incident-reanalyze-conflict");
		ingestEvents(projectId, List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "first error")
		));
		String incidentId = firstIncidentId(projectId);

		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", incidentId)
				.header("Authorization", "Bearer incident-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"first\"}"))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", incidentId)
				.header("Authorization", "Bearer incident-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"second\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("conflict"))
			.andExpect(jsonPath("$.error.message").value("Incident reanalysis already in progress"));
	}

	@Test
	@DisplayName("POST /v1/incidents/{incident_id}/reanalyze 는 incident가 없으면 404를 반환한다")
	void reanalyzeIncidentReturns404WhenIncidentMissing() throws Exception {
		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", "missing-incident")
				.header("Authorization", "Bearer incident-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"missing\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Incident not found"));
	}

	@Test
	@DisplayName("POST /v1/incidents/{incident_id}/reanalyze 는 reason이 500자를 초과하면 422를 반환한다")
	void reanalyzeIncidentReturns422WhenReasonTooLong() throws Exception {
		String projectId = createProjectId("incident-reanalyze-reason-limit");
		ingestEvents(projectId, List.of(
			event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "first error")
		));
		String incidentId = firstIncidentId(projectId);
		String reason = "a".repeat(501);

		mockMvc.perform(post("/v1/incidents/{incident_id}/reanalyze", incidentId)
				.header("Authorization", "Bearer incident-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"" + reason + "\"}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("reason must be at most 500 characters"))
			.andExpect(jsonPath("$.error.details[0].field").value("reason"))
			.andExpect(jsonPath("$.error.details[0].message").value("reason must be at most 500 characters"));
	}

	private String createProjectId(String namePrefix) throws Exception {
		String projectName = namePrefix + "-" + UUID.randomUUID();

		MvcResult result = mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(projectRequestBody(projectName, "prod")))
			.andExpect(status().isCreated())
			.andReturn();

		String projectId = jsonValue(result, "/data/id");
		assertThat(projectId).isNotBlank();
		return projectId;
	}

	private void ingestEvents(String projectId, List<Map<String, Object>> events) throws Exception {
		String body = ingestEventsRequestBody(projectId, "loki", "batch-" + UUID.randomUUID(), events);

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted());
	}

	private String firstIncidentId(String projectId) throws Exception {
		MvcResult result = mockMvc.perform(get("/v1/projects/{project_id}/incidents", projectId)
				.header("Authorization", "Bearer incident-token"))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode firstId = root.path("data").path(0).path("id");
		assertThat(firstId.asText()).isNotBlank();
		return firstId.asText();
	}

	private String projectRequestBody(String name, String environment) {
		return """
			{
			  "name": "%s",
			  "environment": "%s"
			}
			""".formatted(name, environment);
	}

	private void registerOpenAiLlmAccount(String projectId) throws Exception {
		mockMvc.perform(post("/v1/projects/{project_id}/llm-accounts/api-key", projectId)
				.header("Authorization", "Bearer llm-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "openai",
					  "label": "openai-main",
					  "api_key": "sk-openai-1",
					  "model": "gpt-4o-mini",
					  "base_url": null
					}
					"""))
			.andExpect(status().isCreated());
	}

	private void configureRedactionPolicy(String projectId) throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.put("/v1/projects/{project_id}/policies/redaction", projectId)
				.header("Authorization", "Bearer policy-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "enabled": true,
					  "rules": [
					    {"name":"token","pattern":"token=[^\\\\s]+","replace_with":"token=[REDACTED]"},
					    {"name":"password","pattern":"password=[^\\\\s]+","replace_with":"password=[REDACTED]"},
					    {"name":"secret","pattern":"secret=[^\\\\s]+","replace_with":"secret=[REDACTED]"}
					  ]
					}
					"""))
			.andExpect(status().isOk());
	}

	private String ingestEventsRequestBody(
		String projectId,
		String source,
		String batchId,
		List<Map<String, Object>> events
	) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("project_id", projectId);
		body.put("source", source);
		body.put("batch_id", batchId);
		body.put("events", events);
		return objectMapper.writeValueAsString(body);
	}

	private Map<String, Object> event(
		String eventId,
		String timestamp,
		String service,
		String severity,
		String message
	) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("event_id", eventId);
		event.put("timestamp", timestamp);
		event.put("service", service);
		event.put("severity", severity);
		event.put("message", message);
		return event;
	}

	private String jsonValue(MvcResult result, String pointer) throws Exception {
		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		return root.at(pointer).asText();
	}
}
