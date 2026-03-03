package com.logcopilot.llm;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LlmAccountEndpointsContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("POST /v1/projects/{project_id}/llm-accounts/api-key 는 신규 등록 시 201을 반환한다")
	void upsertApiKeyLlmAccountReturns201OnCreate() throws Exception {
		String projectId = createProjectId("llm-apikey-create");

		mockMvc.perform(post("/v1/projects/{project_id}/llm-accounts/api-key", projectId)
				.header("Authorization", "Bearer llm-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(apiKeyRequestBody("openai", "openai-main", "sk-openai-1", "gpt-4o-mini", null)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isString())
			.andExpect(jsonPath("$.data.provider").value("openai"))
			.andExpect(jsonPath("$.data.auth_type").value("api_key"))
			.andExpect(jsonPath("$.data.model").value("gpt-4o-mini"))
			.andExpect(jsonPath("$.data.status").value("active"))
			.andExpect(jsonPath("$.data.created_at").isString());
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/llm-accounts/api-key 는 기존 provider면 200으로 갱신한다")
	void upsertApiKeyLlmAccountReturns200OnUpdate() throws Exception {
		String projectId = createProjectId("llm-apikey-update");

		MvcResult created = mockMvc.perform(post("/v1/projects/{project_id}/llm-accounts/api-key", projectId)
				.header("Authorization", "Bearer llm-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(apiKeyRequestBody("openai", "openai-main", "sk-openai-1", "gpt-4o-mini", null)))
			.andExpect(status().isCreated())
			.andReturn();
		String accountId = jsonValue(created, "/data/id");

		mockMvc.perform(post("/v1/projects/{project_id}/llm-accounts/api-key", projectId)
				.header("Authorization", "Bearer llm-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(apiKeyRequestBody("openai", "openai-main", "sk-openai-2", "gpt-4.1-mini", "https://api.openai.com/v1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(accountId))
			.andExpect(jsonPath("$.data.model").value("gpt-4.1-mini"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/llm-accounts/api-key 는 인증 누락 시 401을 반환한다")
	void upsertApiKeyLlmAccountRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("llm-apikey-auth");

		mockMvc.perform(post("/v1/projects/{project_id}/llm-accounts/api-key", projectId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(apiKeyRequestBody("openai", "openai-main", "sk-openai-1", "gpt-4o-mini", null)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/llm-accounts/api-key 는 provider 검증 실패 시 422를 반환한다")
	void upsertApiKeyLlmAccountReturns422WhenProviderInvalid() throws Exception {
		String projectId = createProjectId("llm-apikey-provider");

		mockMvc.perform(post("/v1/projects/{project_id}/llm-accounts/api-key", projectId)
				.header("Authorization", "Bearer llm-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(apiKeyRequestBody("anthropic", "bad", "sk-123", "claude", null)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("provider must be one of: openai, gemini"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/llm-accounts/api-key 는 프로젝트가 없으면 400을 반환한다")
	void upsertApiKeyLlmAccountReturns400WhenProjectMissing() throws Exception {
		mockMvc.perform(post("/v1/projects/{project_id}/llm-accounts/api-key", "missing-project")
				.header("Authorization", "Bearer llm-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(apiKeyRequestBody("openai", "openai-main", "sk-openai-1", "gpt-4o-mini", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("bad_request"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/llm-oauth/{provider}/start 는 OAuth URL과 state를 반환한다")
	void startLlmOAuthReturns200WithAuthUrl() throws Exception {
		String projectId = createProjectId("llm-oauth-start");

		mockMvc.perform(post("/v1/projects/{project_id}/llm-oauth/{provider}/start", projectId, "openai")
				.header("Authorization", "Bearer llm-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.auth_url").isString())
			.andExpect(jsonPath("$.data.state").isString());
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/llm-oauth/{provider}/start 는 provider가 잘못되면 400을 반환한다")
	void startLlmOAuthReturns400WhenProviderInvalid() throws Exception {
		String projectId = createProjectId("llm-oauth-provider");

		mockMvc.perform(post("/v1/projects/{project_id}/llm-oauth/{provider}/start", projectId, "claude")
				.header("Authorization", "Bearer llm-token"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("bad_request"))
			.andExpect(jsonPath("$.error.message").value("provider must be one of: openai, gemini"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/llm-oauth/{provider}/callback 은 계정 연동 시 200을 반환한다")
	void callbackLlmOAuthReturns200WhenLinked() throws Exception {
		String projectId = createProjectId("llm-oauth-callback");
		MvcResult started = mockMvc.perform(post("/v1/projects/{project_id}/llm-oauth/{provider}/start", projectId, "gemini")
				.header("Authorization", "Bearer llm-token"))
			.andExpect(status().isOk())
			.andReturn();
		String state = jsonValue(started, "/data/state");

		mockMvc.perform(get("/v1/projects/{project_id}/llm-oauth/{provider}/callback", projectId, "gemini")
				.queryParam("code", "oauth-code-1")
				.queryParam("state", state))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.linked").value(true))
			.andExpect(jsonPath("$.data.account_id").isString());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/llm-oauth/{provider}/callback 은 state 불일치 시 409를 반환한다")
	void callbackLlmOAuthReturns409OnStateMismatch() throws Exception {
		String projectId = createProjectId("llm-oauth-callback-conflict");
		mockMvc.perform(post("/v1/projects/{project_id}/llm-oauth/{provider}/start", projectId, "openai")
				.header("Authorization", "Bearer llm-token"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/v1/projects/{project_id}/llm-oauth/{provider}/callback", projectId, "openai")
				.queryParam("code", "oauth-code-1")
				.queryParam("state", "mismatched-state"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("conflict"))
			.andExpect(jsonPath("$.error.message").value("Invalid or expired oauth state"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/llm-accounts 는 계정 목록을 반환한다")
	void listLlmAccountsReturnsAccountList() throws Exception {
		String projectId = createProjectId("llm-list");
		mockMvc.perform(post("/v1/projects/{project_id}/llm-accounts/api-key", projectId)
				.header("Authorization", "Bearer llm-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(apiKeyRequestBody("openai", "openai-main", "sk-openai-1", "gpt-4o-mini", null)))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/v1/projects/{project_id}/llm-accounts", projectId)
				.header("Authorization", "Bearer llm-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data[0].provider").value("openai"))
			.andExpect(jsonPath("$.data[0].auth_type").value("api_key"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/llm-accounts 는 인증 누락 시 401을 반환한다")
	void listLlmAccountsRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("llm-list-auth");

		mockMvc.perform(get("/v1/projects/{project_id}/llm-accounts", projectId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("DELETE /v1/projects/{project_id}/llm-accounts/{account_id} 는 삭제 성공 시 204를 반환한다")
	void deleteLlmAccountReturns204OnSuccess() throws Exception {
		String projectId = createProjectId("llm-delete");
		MvcResult created = mockMvc.perform(post("/v1/projects/{project_id}/llm-accounts/api-key", projectId)
				.header("Authorization", "Bearer llm-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(apiKeyRequestBody("gemini", "gemini-main", "gsk-1", "gemini-2.0-flash", null)))
			.andExpect(status().isCreated())
			.andReturn();
		String accountId = jsonValue(created, "/data/id");

		mockMvc.perform(delete("/v1/projects/{project_id}/llm-accounts/{account_id}", projectId, accountId)
				.header("Authorization", "Bearer llm-token"))
			.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/llm-accounts 는 프로젝트가 없으면 404를 반환한다")
	void listLlmAccountsReturns404WhenProjectMissing() throws Exception {
		mockMvc.perform(get("/v1/projects/{project_id}/llm-accounts", "missing-project")
				.header("Authorization", "Bearer llm-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
	}

	@Test
	@DisplayName("DELETE /v1/projects/{project_id}/llm-accounts/{account_id} 는 account가 없으면 404를 반환한다")
	void deleteLlmAccountReturns404WhenAccountMissing() throws Exception {
		String projectId = createProjectId("llm-delete-missing");

		mockMvc.perform(delete("/v1/projects/{project_id}/llm-accounts/{account_id}", projectId, "missing-account")
				.header("Authorization", "Bearer llm-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("LLM account not found"));
	}

	private String createProjectId(String namePrefix) throws Exception {
		String projectName = namePrefix + "-" + UUID.randomUUID();

		MvcResult result = mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer project-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(projectRequestBody(projectName, "prod")))
			.andExpect(status().isCreated())
			.andReturn();

		String projectId = jsonValue(result, "/data/id");
		assertThat(projectId).isNotBlank();
		return projectId;
	}

	private String projectRequestBody(String name, String environment) {
		return """
			{
			  "name": "%s",
			  "environment": "%s"
			}
			""".formatted(name, environment);
	}

	private String apiKeyRequestBody(
		String provider,
		String label,
		String apiKey,
		String model,
		String baseUrl
	) {
		return """
			{
			  "provider": "%s",
			  "label": "%s",
			  "api_key": "%s",
			  "model": "%s",
			  "base_url": %s
			}
			""".formatted(
			provider,
			label,
			apiKey,
			model,
			baseUrl == null ? "null" : "\"" + baseUrl + "\""
		);
	}

	private String jsonValue(MvcResult result, String pointer) throws Exception {
		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		return root.at(pointer).asText();
	}
}
