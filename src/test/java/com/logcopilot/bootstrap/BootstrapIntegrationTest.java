package com.logcopilot.bootstrap;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "logcopilot.auth.seed-default-tokens=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BootstrapIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("GET /v1/bootstrap/status 는 초기 상태에서 bootstrapped=false를 반환한다")
	void bootstrapStatusReturnsFalseBeforeInitialization() throws Exception {
		mockMvc.perform(get("/v1/bootstrap/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.bootstrapped").value(false))
			.andExpect(jsonPath("$.data.initialized_at").value(nullValue()));
	}

	@Test
	@DisplayName("POST /v1/bootstrap/initialize 는 초기 프로젝트와 운영자/ingest 토큰을 발급한다")
	void bootstrapInitializeCreatesProjectAndInitialTokens() throws Exception {
		MvcResult bootstrapResult = mockMvc.perform(post("/v1/bootstrap/initialize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "project_name": "bootstrap-core",
					  "environment": "prod",
					  "operator_token_name": "ui-admin",
					  "ingest_token_name": "collector"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.bootstrapped").value(true))
			.andExpect(jsonPath("$.data.project.id").isString())
			.andExpect(jsonPath("$.data.project.name").value("bootstrap-core"))
			.andExpect(jsonPath("$.data.operator_token.token").isString())
			.andExpect(jsonPath("$.data.ingest_token.token").isString())
			.andReturn();

		JsonNode response = objectMapper.readTree(bootstrapResult.getResponse().getContentAsString());
		String operatorToken = response.at("/data/operator_token/token").asText();

		mockMvc.perform(get("/v1/system/info")
				.header("Authorization", "Bearer " + operatorToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.version").isString());

		mockMvc.perform(get("/v1/bootstrap/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.bootstrapped").value(true))
			.andExpect(jsonPath("$.data.initialized_at").isString());
	}

	@Test
	@DisplayName("POST /v1/bootstrap/initialize 는 bootstrap 완료 후 409를 반환한다")
	void bootstrapInitializeRejectsWhenAlreadyBootstrapped() throws Exception {
		mockMvc.perform(post("/v1/bootstrap/initialize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "project_name": "bootstrap-core",
					  "environment": "prod"
					}
					"""))
			.andExpect(status().isCreated());

		MvcResult second = mockMvc.perform(post("/v1/bootstrap/initialize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "project_name": "bootstrap-core-2",
					  "environment": "staging"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("conflict"))
			.andExpect(jsonPath("$.error.message").value("Bootstrap already completed"))
			.andReturn();

		JsonNode error = objectMapper.readTree(second.getResponse().getContentAsString());
		assertThat(error.at("/error/code").asText()).isEqualTo("conflict");
	}

	@Test
	@DisplayName("POST /v1/bootstrap/initialize 는 잘못된 environment 입력 시 422를 반환한다")
	void bootstrapInitializeReturns422ForInvalidEnvironment() throws Exception {
		mockMvc.perform(post("/v1/bootstrap/initialize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "project_name": "bootstrap-core",
					  "environment": "qa"
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("environment must be one of: prod, staging, dev"));
	}
}
