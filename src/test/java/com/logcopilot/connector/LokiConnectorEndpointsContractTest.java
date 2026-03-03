package com.logcopilot.connector;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LokiConnectorEndpointsContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki 는 신규 생성 시 201을 반환한다")
	void upsertLokiConnectorReturns201OnCreate() throws Exception {
		String projectId = createProjectId("loki-create");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"none",
					null,
					null,
					null,
					"{service=\"api\"}",
					30
				)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isString())
			.andExpect(jsonPath("$.data.type").value("loki"))
			.andExpect(jsonPath("$.data.status").value("active"))
			.andExpect(jsonPath("$.data.updated_at").isString());
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki 는 기존 설정이면 200을 반환한다")
	void upsertLokiConnectorReturns200OnUpdate() throws Exception {
		String projectId = createProjectId("loki-update");

		MvcResult created = mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"none",
					null,
					null,
					null,
					"{service=\"api\"}",
					30
				)))
			.andExpect(status().isCreated())
			.andReturn();

		String connectorId = jsonValue(created, "$.data.id");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"none",
					null,
					null,
					null,
					"{service=\"worker\"}",
					45
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(connectorId))
			.andExpect(jsonPath("$.data.type").value("loki"))
			.andExpect(jsonPath("$.data.status").value("active"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki 는 인증 누락 시 401을 반환한다")
	void upsertLokiConnectorRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("loki-auth-missing");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"none",
					null,
					null,
					null,
					"{service=\"api\"}",
					30
				)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki 는 프로젝트가 없으면 404를 반환한다")
	void upsertLokiConnectorReturns404WhenProjectMissing() throws Exception {
		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", "missing-project")
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"none",
					null,
					null,
					null,
					"{service=\"api\"}",
					30
				)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki 는 유효성 실패 시 422를 반환한다")
	void upsertLokiConnectorReturns422OnValidationError() throws Exception {
		String projectId = createProjectId("loki-validation");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"none",
					null,
					null,
					null,
					"{service=\"api\"}",
					1
				)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("poll_interval_seconds must be between 5 and 300"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki/test 는 테스트 성공 시 200을 반환한다")
	void testLokiConnectorReturns200OnSuccess() throws Exception {
		String projectId = createProjectId("loki-test-success");
		upsertConnector(projectId, "https://loki.example.com");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki/test", projectId)
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.success").value(true))
			.andExpect(jsonPath("$.data.sample_count").isNumber())
			.andExpect(jsonPath("$.data.latency_ms").isNumber())
			.andExpect(jsonPath("$.data.message").isString());
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki/test 는 인증 누락 시 401을 반환한다")
	void testLokiConnectorRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("loki-test-auth");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki/test", projectId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki/test 는 프로젝트가 없으면 404를 반환한다")
	void testLokiConnectorReturns404WhenProjectMissing() throws Exception {
		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki/test", "missing-project")
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki/test 는 업스트림 실패 시 502를 반환한다")
	void testLokiConnectorReturns502OnUpstreamFailure() throws Exception {
		String projectId = createProjectId("loki-test-fail");
		upsertConnector(projectId, "https://bad-gateway.example.com");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki/test", projectId)
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.error.code").value("bad_gateway"))
			.andExpect(jsonPath("$.error.message").value("Failed to reach Loki upstream"));
	}

	private String createProjectId(String namePrefix) throws Exception {
		String projectName = namePrefix + "-" + UUID.randomUUID();

		MvcResult result = mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(projectRequestBody(projectName, "prod")))
			.andExpect(status().isCreated())
			.andReturn();

		String projectId = jsonValue(result, "$.data.id");
		assertThat(projectId).isNotBlank();
		return projectId;
	}

	private void upsertConnector(String projectId, String endpoint) throws Exception {
		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					endpoint,
					"none",
					null,
					null,
					null,
					"{service=\"api\"}",
					30
				)))
			.andExpect(status().isCreated());
	}

	private String projectRequestBody(String name, String environment) {
		return """
			{
			  "name": "%s",
			  "environment": "%s"
			}
			""".formatted(name, environment);
	}

	private String lokiConnectorRequestBody(
		String endpoint,
		String authType,
		String token,
		String username,
		String password,
		String query,
		Integer pollIntervalSeconds
	) {
		return """
			{
			  "endpoint": "%s",
			  "tenant_id": null,
			  "auth": {
			    "type": "%s",
			    "token": %s,
			    "username": %s,
			    "password": %s
			  },
			  "query": "%s",
			  "poll_interval_seconds": %s
			}
			""".formatted(
			escapeJson(endpoint),
			escapeJson(authType),
			asJsonNullableString(token),
			asJsonNullableString(username),
			asJsonNullableString(password),
			escapeJson(query),
			pollIntervalSeconds == null ? "null" : pollIntervalSeconds.toString()
		);
	}

	private String asJsonNullableString(String value) {
		return value == null ? "null" : "\"" + escapeJson(value) + "\"";
	}

	private String escapeJson(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"");
	}

	private String jsonValue(MvcResult result, String jsonPath) throws Exception {
		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		if ("$.data.id".equals(jsonPath)) {
			return root.path("data").path("id").asText();
		}
		throw new IllegalArgumentException("Unsupported jsonPath: " + jsonPath);
	}
}
