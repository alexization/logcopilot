package com.logcopilot.policy;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PolicyEndpointsContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("PUT /v1/projects/{project_id}/policies/export 는 인증 요청에서 200을 반환한다")
	void updateExportPolicyReturns200WhenAuthorized() throws Exception {
		String projectId = createProjectId("policy-export-ok");

		mockMvc.perform(put("/v1/projects/{project_id}/policies/export", projectId)
				.header("Authorization", "Bearer policy-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(exportRequestBody("level1_byom_only")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.level").value("level1_byom_only"))
			.andExpect(jsonPath("$.data.updated_at").isString());
	}

	@Test
	@DisplayName("PUT /v1/projects/{project_id}/policies/export 는 인증 누락 시 401을 반환한다")
	void updateExportPolicyRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("policy-export-auth");

		mockMvc.perform(put("/v1/projects/{project_id}/policies/export", projectId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(exportRequestBody("level1_byom_only")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("PUT /v1/projects/{project_id}/policies/export 는 level이 잘못되면 400을 반환한다")
	void updateExportPolicyReturns400WhenLevelInvalid() throws Exception {
		String projectId = createProjectId("policy-export-invalid-level");

		mockMvc.perform(put("/v1/projects/{project_id}/policies/export", projectId)
				.header("Authorization", "Bearer policy-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(exportRequestBody("level3")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("bad_request"))
			.andExpect(jsonPath("$.error.message")
				.value("level must be one of: level0_rule_only, level1_byom_only, level2_byom_with_telemetry"));
	}

	@Test
	@DisplayName("PUT /v1/projects/{project_id}/policies/export 는 프로젝트가 없으면 400을 반환한다")
	void updateExportPolicyReturns400WhenProjectMissing() throws Exception {
		mockMvc.perform(put("/v1/projects/{project_id}/policies/export", "missing-project")
				.header("Authorization", "Bearer policy-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(exportRequestBody("level1_byom_only")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("bad_request"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
	}

	@Test
	@DisplayName("PUT /v1/projects/{project_id}/policies/redaction 는 인증 요청에서 200을 반환한다")
	void updateRedactionPolicyReturns200WhenAuthorized() throws Exception {
		String projectId = createProjectId("policy-redaction-ok");

		mockMvc.perform(put("/v1/projects/{project_id}/policies/redaction", projectId)
				.header("Authorization", "Bearer policy-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(redactionRequestBody(
					true,
					"""
					[
					  {"name":"email","pattern":"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+","replace_with":"[REDACTED_EMAIL]"},
					  {"name":"token","pattern":"token=[^ ]+","replace_with":"token=[REDACTED]"}
					]
					"""
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.enabled").value(true))
			.andExpect(jsonPath("$.data.rules_count").value(2))
			.andExpect(jsonPath("$.data.updated_at").isString());
	}

	@Test
	@DisplayName("PUT /v1/projects/{project_id}/policies/redaction 는 인증 누락 시 401을 반환한다")
	void updateRedactionPolicyRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("policy-redaction-auth");

		mockMvc.perform(put("/v1/projects/{project_id}/policies/redaction", projectId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(redactionRequestBody(true, "[]")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("PUT /v1/projects/{project_id}/policies/redaction 는 규칙이 200개 초과면 400을 반환한다")
	void updateRedactionPolicyReturns400WhenTooManyRules() throws Exception {
		String projectId = createProjectId("policy-redaction-many");

		mockMvc.perform(put("/v1/projects/{project_id}/policies/redaction", projectId)
				.header("Authorization", "Bearer policy-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(redactionRequestBody(true, tooManyRulesJsonArray(201))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("bad_request"))
			.andExpect(jsonPath("$.error.message").value("rules must contain at most 200 items"));
	}

	@Test
	@DisplayName("PUT /v1/projects/{project_id}/policies/redaction 는 정규식이 잘못되면 400을 반환한다")
	void updateRedactionPolicyReturns400WhenPatternInvalid() throws Exception {
		String projectId = createProjectId("policy-redaction-invalid-pattern");

		mockMvc.perform(put("/v1/projects/{project_id}/policies/redaction", projectId)
				.header("Authorization", "Bearer policy-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(redactionRequestBody(
					true,
					"""
					[
					  {"name":"broken","pattern":"(","replace_with":"[MASKED]"}
					]
					"""
				)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("bad_request"))
			.andExpect(jsonPath("$.error.message").value("rules[0].pattern must be a valid regex"));
	}

	@Test
	@DisplayName("PUT /v1/projects/{project_id}/policies/redaction 는 과도하게 긴 정규식이면 400을 반환한다")
	void updateRedactionPolicyReturns400WhenPatternTooLong() throws Exception {
		String projectId = createProjectId("policy-redaction-long-pattern");
		String longPattern = "a".repeat(513);

		mockMvc.perform(put("/v1/projects/{project_id}/policies/redaction", projectId)
				.header("Authorization", "Bearer policy-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(redactionRequestBody(
					true,
					"""
					[
					  {"name":"long-pattern","pattern":"%s","replace_with":"[MASKED]"}
					]
					""".formatted(longPattern)
				)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("bad_request"))
			.andExpect(jsonPath("$.error.message").value("rules[0].pattern must be at most 512 characters"));
	}

	private String createProjectId(String namePrefix) throws Exception {
		String projectName = namePrefix + "-" + UUID.randomUUID();

		MvcResult result = mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer project-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(projectRequestBody(projectName, "prod")))
			.andExpect(status().isCreated())
			.andReturn();
		return jsonValue(result, "/data/id");
	}

	private String projectRequestBody(String name, String environment) {
		return """
			{
			  "name": "%s",
			  "environment": "%s"
			}
			""".formatted(name, environment);
	}

	private String exportRequestBody(String level) {
		return """
			{
			  "level": "%s"
			}
			""".formatted(level);
	}

	private String redactionRequestBody(boolean enabled, String rulesJsonArray) {
		return """
			{
			  "enabled": %s,
			  "rules": %s
			}
			""".formatted(enabled, rulesJsonArray);
	}

	private String tooManyRulesJsonArray(int size) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		for (int index = 0; index < size; index++) {
			if (index > 0) {
				builder.append(",");
			}
			builder.append("""
				{"name":"rule-%d","pattern":"token-%d","replace_with":"[MASKED]"}
				""".formatted(index, index));
		}
		builder.append("]");
		return builder.toString();
	}

	private String jsonValue(MvcResult result, String pointer) throws Exception {
		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		return root.at(pointer).asText();
	}
}
