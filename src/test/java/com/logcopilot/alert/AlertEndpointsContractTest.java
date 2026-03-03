package com.logcopilot.alert;

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
class AlertEndpointsContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/slack 는 신규 설정 시 201을 반환한다")
	void configureSlackReturns201OnCreate() throws Exception {
		String projectId = createProjectId("alert-slack-create");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(slackRequestBody("https://hooks.slack.com/services/T000/B000/XXX", "#ops", 0.45)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isString())
			.andExpect(jsonPath("$.data.type").value("slack"))
			.andExpect(jsonPath("$.data.enabled").value(true))
			.andExpect(jsonPath("$.data.updated_at").isString());
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/slack 는 기존 채널 설정 시 200으로 갱신한다")
	void configureSlackReturns200OnUpdate() throws Exception {
		String projectId = createProjectId("alert-slack-update");

		MvcResult created = mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(slackRequestBody("https://hooks.slack.com/services/T000/B000/AAA", "#ops", 0.40)))
			.andExpect(status().isCreated())
			.andReturn();

		String channelId = jsonValue(created, "/data/id");
		assertThat(channelId).isNotBlank();

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(slackRequestBody("https://hooks.slack.com/services/T000/B000/BBB", "#platform", 0.80)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(channelId))
			.andExpect(jsonPath("$.data.type").value("slack"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/slack 는 인증 누락 시 401을 반환한다")
	void configureSlackRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("alert-slack-auth");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(slackRequestBody("https://hooks.slack.com/services/T000/B000/XXX", "#ops", 0.45)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/slack 는 webhook_url이 잘못되면 422를 반환한다")
	void configureSlackReturns422WhenWebhookInvalid() throws Exception {
		String projectId = createProjectId("alert-slack-invalid-webhook");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(slackRequestBody("invalid-uri", "#ops", 0.45)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("webhook_url must be a valid URI"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/email 는 신규 설정 시 201을 반환한다")
	void configureEmailReturns201OnCreate() throws Exception {
		String projectId = createProjectId("alert-email-create");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailRequestBody(
					"alerts@example.com",
					"\"oncall@example.com\",\"lead@example.com\"",
					"smtp.example.com",
					587,
					"smtp-user",
					"smtp-secret",
					true,
					0.6
				)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isString())
			.andExpect(jsonPath("$.data.type").value("email"))
			.andExpect(jsonPath("$.data.enabled").value(true));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/email 는 recipients가 비면 422를 반환한다")
	void configureEmailReturns422WhenRecipientsEmpty() throws Exception {
		String projectId = createProjectId("alert-email-invalid-recipients");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailRequestBody(
					"alerts@example.com",
					null,
					"smtp.example.com",
					587,
					"smtp-user",
					"smtp-secret",
					true,
					0.6
				)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("recipients must contain at least 1 email"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/audit-logs 는 필터와 커서를 적용해 200을 반환한다")
	void listAuditLogsReturnsFilteredLogsWithPagination() throws Exception {
		String projectId = createProjectId("audit-log-list");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer actor-token-a")
				.contentType(MediaType.APPLICATION_JSON)
				.content(slackRequestBody("https://hooks.slack.com/services/T000/B000/AAA", "#ops", 0.5)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer actor-token-b")
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailRequestBody(
					"alerts@example.com",
					"\"oncall@example.com\"",
					"smtp.example.com",
					587,
					"smtp-user",
					"smtp-secret",
					true,
					0.7
				)))
			.andExpect(status().isCreated());

		MvcResult firstPage = mockMvc.perform(get("/v1/projects/{project_id}/audit-logs", projectId)
				.header("Authorization", "Bearer reader-token")
				.queryParam("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").isString())
			.andExpect(jsonPath("$.data[0].action").isString())
			.andExpect(jsonPath("$.data[0].resource_type").value("alert_channel"))
			.andExpect(jsonPath("$.meta.request_id").isString())
			.andExpect(jsonPath("$.meta.next_cursor").isString())
			.andReturn();

		String actor = jsonValue(firstPage, "/data/0/actor");
		String nextCursor = jsonValue(firstPage, "/meta/next_cursor");

		mockMvc.perform(get("/v1/projects/{project_id}/audit-logs", projectId)
				.header("Authorization", "Bearer reader-token")
				.queryParam("cursor", nextCursor)
				.queryParam("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1));

		mockMvc.perform(get("/v1/projects/{project_id}/audit-logs", projectId)
				.header("Authorization", "Bearer reader-token")
				.queryParam("action", "alert.email.configured"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].action").value("alert.email.configured"));

		mockMvc.perform(get("/v1/projects/{project_id}/audit-logs", projectId)
				.header("Authorization", "Bearer reader-token")
				.queryParam("actor", actor))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].actor").value(actor));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/audit-logs 는 인증 누락 시 401을 반환한다")
	void listAuditLogsRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("audit-log-auth");

		mockMvc.perform(get("/v1/projects/{project_id}/audit-logs", projectId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/audit-logs 는 프로젝트가 없으면 404를 반환한다")
	void listAuditLogsReturns404WhenProjectMissing() throws Exception {
		mockMvc.perform(get("/v1/projects/{project_id}/audit-logs", "missing-project")
				.header("Authorization", "Bearer reader-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/audit-logs 는 limit이 범위를 벗어나면 422를 반환한다")
	void listAuditLogsReturns422WhenLimitOutOfRange() throws Exception {
		String projectId = createProjectId("audit-log-limit");

		mockMvc.perform(get("/v1/projects/{project_id}/audit-logs", projectId)
				.header("Authorization", "Bearer reader-token")
				.queryParam("limit", "201"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("limit must be between 1 and 200"));
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

	private String slackRequestBody(String webhookUrl, String channel, Double minConfidence) {
		return """
			{
			  "webhook_url": "%s",
			  "channel": "%s",
			  "min_confidence": %s
			}
			""".formatted(webhookUrl, channel, minConfidence == null ? "null" : minConfidence);
	}

	private String emailRequestBody(
		String from,
		String recipientsJson,
		String host,
		int port,
		String username,
		String password,
		boolean starttls,
		Double minConfidence
	) {
		String recipients = recipientsJson == null ? "[]" : "[" + recipientsJson + "]";
		return """
			{
			  "from": "%s",
			  "recipients": %s,
			  "smtp": {
			    "host": "%s",
			    "port": %d,
			    "username": "%s",
			    "password": "%s",
			    "starttls": %s
			  },
			  "min_confidence": %s
			}
			""".formatted(
			from,
			recipients,
			host,
			port,
			username,
			password,
			starttls,
			minConfidence == null ? "null" : minConfidence
		);
	}

	private String jsonValue(MvcResult result, String pointer) throws Exception {
		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode node = root.at(pointer);
		return node.isMissingNode() || node.isNull() ? null : node.asText();
	}
}
