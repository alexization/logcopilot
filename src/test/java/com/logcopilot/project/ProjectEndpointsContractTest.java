package com.logcopilot.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectEndpointsContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("POST /v1/projects 는 인증 요청에서 201과 생성된 프로젝트를 반환한다")
	void createProjectReturns201WhenAuthorized() throws Exception {
		mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "core-api",
					  "environment": "prod"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isString())
			.andExpect(jsonPath("$.data.name").value("core-api"))
			.andExpect(jsonPath("$.data.environment").value("prod"))
			.andExpect(jsonPath("$.data.created_at").isString());
	}

	@Test
	@DisplayName("GET /v1/projects 는 인증 요청에서 프로젝트 목록을 반환한다")
	void listProjectsReturnsProjectListWhenAuthorized() throws Exception {
		mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "billing-api",
					  "environment": "staging"
					}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/v1/projects")
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data[0].name").value("billing-api"))
			.andExpect(jsonPath("$.data[0].environment").value("staging"));
	}

	@Test
	@DisplayName("POST /v1/projects 는 인증 헤더가 없으면 401을 반환한다")
	void createProjectRejectsMissingBearerToken() throws Exception {
		mockMvc.perform(post("/v1/projects")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "auth-api",
					  "environment": "dev"
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("POST /v1/projects 는 잘못된 입력이면 400을 반환한다")
	void createProjectReturns400ForInvalidRequest() throws Exception {
		mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "",
					  "environment": "prod"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("bad_request"))
			.andExpect(jsonPath("$.error.message").value("Project name must be between 1 and 100 characters"));
	}

	@Test
	@DisplayName("POST /v1/projects 는 중복 프로젝트명 생성 시 409를 반환한다")
	void createProjectReturns409ForDuplicateName() throws Exception {
		mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "orders-api",
					  "environment": "prod"
					}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "orders-api",
					  "environment": "dev"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("conflict"))
			.andExpect(jsonPath("$.error.message").value("Project name already exists"));
	}
}
