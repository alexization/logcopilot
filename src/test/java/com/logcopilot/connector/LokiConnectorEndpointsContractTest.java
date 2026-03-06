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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
	@DisplayName("GET /v1/projects/{project_id}/connectors/loki 는 현재 설정을 반환한다")
	void getLokiConnectorReturnsCurrentConfiguration() throws Exception {
		String projectId = createProjectId("loki-get-configured");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"basic",
					null,
					"reader",
					"reader-secret",
					"{service=\"api\"}",
					45
				)))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.configured").value(true))
			.andExpect(jsonPath("$.data.type").value("loki"))
			.andExpect(jsonPath("$.data.status").value("active"))
			.andExpect(jsonPath("$.data.endpoint").value("https://loki.example.com"))
			.andExpect(jsonPath("$.data.tenant_id").isEmpty())
			.andExpect(jsonPath("$.data.auth.type").value("basic"))
			.andExpect(jsonPath("$.data.auth.token").isEmpty())
			.andExpect(jsonPath("$.data.auth.token_configured").value(false))
			.andExpect(jsonPath("$.data.auth.username").value("reader"))
			.andExpect(jsonPath("$.data.auth.password").isEmpty())
			.andExpect(jsonPath("$.data.auth.password_configured").value(true))
			.andExpect(jsonPath("$.data.query").value("{service=\"api\"}"))
			.andExpect(jsonPath("$.data.poll_interval_seconds").value(45))
			.andExpect(jsonPath("$.data.updated_at").isString());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/connectors/loki 는 미설정 프로젝트에서 configured=false를 반환한다")
	void getLokiConnectorReturnsUnconfiguredWhenMissing() throws Exception {
		String projectId = createProjectId("loki-get-empty");

		mockMvc.perform(get("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.configured").value(false))
			.andExpect(jsonPath("$.data.type").value("loki"))
			.andExpect(jsonPath("$.data.status").value("not_configured"))
			.andExpect(jsonPath("$.data.endpoint").isEmpty())
			.andExpect(jsonPath("$.data.auth.type").value("none"))
			.andExpect(jsonPath("$.data.auth.token_configured").value(false))
			.andExpect(jsonPath("$.data.auth.password_configured").value(false))
			.andExpect(jsonPath("$.data.query").isEmpty())
			.andExpect(jsonPath("$.data.poll_interval_seconds").isEmpty())
			.andExpect(jsonPath("$.data.updated_at").isEmpty());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/connectors/loki 는 인증 누락 시 401을 반환한다")
	void getLokiConnectorRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("loki-get-auth");

		mockMvc.perform(get("/v1/projects/{project_id}/connectors/loki", projectId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/connectors/loki 는 프로젝트가 없으면 404를 반환한다")
	void getLokiConnectorReturns404WhenProjectMissing() throws Exception {
		mockMvc.perform(get("/v1/projects/{project_id}/connectors/loki", "missing-project")
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
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
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki 는 JSON null 본문이면 400을 반환한다")
	void upsertLokiConnectorReturns400OnNullJsonBody() throws Exception {
		String projectId = createProjectId("loki-null-body");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("bad_request"))
			.andExpect(jsonPath("$.error.message").value("Malformed JSON request body"));
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

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki/test 는 커넥터 미설정이면 404를 반환한다")
	void testLokiConnectorReturns404WhenConnectorMissing() throws Exception {
		String projectId = createProjectId("loki-test-connector-missing");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki/test", projectId)
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Loki connector not found"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki 는 기존 secret을 유지한 채 갱신할 수 있다")
	void upsertLokiConnectorKeepsExistingSecretWhenInputBlank() throws Exception {
		String projectId = createProjectId("loki-secret-preserve");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"bearer",
					"token-initial",
					null,
					null,
					"{service=\"api\"}",
					30
				)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"bearer",
					"",
					null,
					null,
					"{service=\"worker\"}",
					60
				)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.query").value("{service=\"worker\"}"))
			.andExpect(jsonPath("$.data.poll_interval_seconds").value(60))
			.andExpect(jsonPath("$.data.auth.type").value("bearer"))
			.andExpect(jsonPath("$.data.auth.token").isEmpty())
			.andExpect(jsonPath("$.data.auth.token_configured").value(true));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/connectors/loki 는 basic 인증정보를 공란 갱신 시 기존값으로 유지한다")
	void upsertLokiConnectorKeepsExistingBasicCredentialsWhenInputBlank() throws Exception {
		String projectId = createProjectId("loki-basic-preserve");

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"basic",
					null,
					"reader",
					"reader-secret",
					"{service=\"api\"}",
					30
				)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(lokiConnectorRequestBody(
					"https://loki.example.com",
					"basic",
					null,
					"",
					"",
					"{service=\"worker\"}",
					60
				)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/v1/projects/{project_id}/connectors/loki", projectId)
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.query").value("{service=\"worker\"}"))
			.andExpect(jsonPath("$.data.poll_interval_seconds").value(60))
			.andExpect(jsonPath("$.data.auth.type").value("basic"))
			.andExpect(jsonPath("$.data.auth.username").value("reader"))
			.andExpect(jsonPath("$.data.auth.password").isEmpty())
			.andExpect(jsonPath("$.data.auth.password_configured").value(true));
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
