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
	@DisplayName("GET /v1/projects/{project_id}/alerts/slack 는 현재 Slack 설정을 반환한다")
	void getSlackAlertReturnsCurrentConfiguration() throws Exception {
		String projectId = createProjectId("alert-slack-get");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(slackRequestBody("https://hooks.slack.com/services/T000/B000/GET", "#platform", 0.82)))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.configured").value(true))
			.andExpect(jsonPath("$.data.type").value("slack"))
			.andExpect(jsonPath("$.data.enabled").value(true))
			.andExpect(jsonPath("$.data.webhook_url").isEmpty())
			.andExpect(jsonPath("$.data.webhook_configured").value(true))
			.andExpect(jsonPath("$.data.channel").value("#platform"))
			.andExpect(jsonPath("$.data.min_confidence").value(0.82))
			.andExpect(jsonPath("$.data.updated_at").isString());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/alerts/slack 는 미설정 시 configured=false를 반환한다")
	void getSlackAlertReturnsUnconfiguredWhenMissing() throws Exception {
		String projectId = createProjectId("alert-slack-get-default");

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.configured").value(false))
			.andExpect(jsonPath("$.data.type").value("slack"))
			.andExpect(jsonPath("$.data.enabled").value(false))
			.andExpect(jsonPath("$.data.webhook_url").isEmpty())
			.andExpect(jsonPath("$.data.webhook_configured").value(false))
			.andExpect(jsonPath("$.data.channel").isEmpty())
			.andExpect(jsonPath("$.data.min_confidence").value(0.45))
			.andExpect(jsonPath("$.data.updated_at").isEmpty());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/alerts/slack 는 인증 누락 시 401을 반환한다")
	void getSlackAlertRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("alert-slack-get-auth");

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/slack", projectId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/alerts/slack 는 프로젝트가 없으면 404를 반환한다")
	void getSlackAlertReturns404WhenProjectMissing() throws Exception {
		mockMvc.perform(get("/v1/projects/{project_id}/alerts/slack", "missing-project")
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
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
	@DisplayName("POST /v1/projects/{project_id}/alerts/slack 는 JSON null 본문이면 422를 반환한다")
	void configureSlackReturns422OnNullJsonBody() throws Exception {
		String projectId = createProjectId("alert-slack-null-body");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("Request body must not be null"))
			.andExpect(jsonPath("$.error.details[0].field").value("request"))
			.andExpect(jsonPath("$.error.details[0].message").value("Request body must not be null"));
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
	@DisplayName("POST /v1/projects/{project_id}/alerts/email 는 기존 채널 설정 시 200으로 갱신한다")
	void configureEmailReturns200OnUpdate() throws Exception {
		String projectId = createProjectId("alert-email-update");

		MvcResult created = mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailRequestBody(
					"alerts@example.com",
					"\"oncall@example.com\"",
					"smtp.example.com",
					587,
					"smtp-user",
					"smtp-secret",
					true,
					0.6
				)))
			.andExpect(status().isCreated())
			.andReturn();

		String channelId = jsonValue(created, "/data/id");
		assertThat(channelId).isNotBlank();

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailRequestBody(
					"alerts@example.com",
					"\"sre@example.com\",\"lead@example.com\"",
					"smtp.example.com",
					587,
					"smtp-user",
					"smtp-secret",
					true,
					0.7
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(channelId))
			.andExpect(jsonPath("$.data.type").value("email"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/alerts/email 는 현재 Email 설정을 반환한다")
	void getEmailAlertReturnsCurrentConfiguration() throws Exception {
		String projectId = createProjectId("alert-email-get");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailRequestBody(
					"alerts@example.com",
					"\"ops@example.com\",\"sre@example.com\"",
					"smtp.example.com",
					465,
					"smtp-reader",
					"smtp-secret-get",
					false,
					0.91
				)))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.configured").value(true))
			.andExpect(jsonPath("$.data.type").value("email"))
			.andExpect(jsonPath("$.data.enabled").value(true))
			.andExpect(jsonPath("$.data.from").value("alerts@example.com"))
			.andExpect(jsonPath("$.data.recipients[0]").value("ops@example.com"))
			.andExpect(jsonPath("$.data.recipients[1]").value("sre@example.com"))
			.andExpect(jsonPath("$.data.smtp.host").value("smtp.example.com"))
			.andExpect(jsonPath("$.data.smtp.port").value(465))
			.andExpect(jsonPath("$.data.smtp.username").value("smtp-reader"))
			.andExpect(jsonPath("$.data.smtp.password").isEmpty())
			.andExpect(jsonPath("$.data.smtp.password_configured").value(true))
			.andExpect(jsonPath("$.data.smtp.starttls").value(false))
			.andExpect(jsonPath("$.data.min_confidence").value(0.91))
			.andExpect(jsonPath("$.data.updated_at").isString());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/alerts/email 는 미설정 시 configured=false를 반환한다")
	void getEmailAlertReturnsUnconfiguredWhenMissing() throws Exception {
		String projectId = createProjectId("alert-email-get-default");

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.configured").value(false))
			.andExpect(jsonPath("$.data.type").value("email"))
			.andExpect(jsonPath("$.data.enabled").value(false))
			.andExpect(jsonPath("$.data.from").isEmpty())
			.andExpect(jsonPath("$.data.recipients").isArray())
			.andExpect(jsonPath("$.data.recipients.length()").value(0))
			.andExpect(jsonPath("$.data.smtp.host").isEmpty())
			.andExpect(jsonPath("$.data.smtp.port").isEmpty())
			.andExpect(jsonPath("$.data.smtp.username").isEmpty())
			.andExpect(jsonPath("$.data.smtp.password").isEmpty())
			.andExpect(jsonPath("$.data.smtp.password_configured").value(false))
			.andExpect(jsonPath("$.data.smtp.starttls").value(true))
			.andExpect(jsonPath("$.data.min_confidence").value(0.45))
			.andExpect(jsonPath("$.data.updated_at").isEmpty());
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/alerts/email 는 인증 누락 시 401을 반환한다")
	void getEmailAlertRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("alert-email-get-auth");

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/email", projectId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/alerts/email 는 프로젝트가 없으면 404를 반환한다")
	void getEmailAlertReturns404WhenProjectMissing() throws Exception {
		mockMvc.perform(get("/v1/projects/{project_id}/alerts/email", "missing-project")
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("not_found"))
			.andExpect(jsonPath("$.error.message").value("Project not found"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/slack 는 webhook 공백 시 기존 secret을 유지한다")
	void configureSlackKeepsExistingWebhookWhenBlank() throws Exception {
		String projectId = createProjectId("alert-slack-preserve-webhook");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(slackRequestBody("https://hooks.slack.com/services/T000/B000/INIT", "#ops", 0.45)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "webhook_url": "",
					  "channel": "#platform",
					  "min_confidence": 0.71
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.channel").value("#platform"))
			.andExpect(jsonPath("$.data.min_confidence").value(0.71))
			.andExpect(jsonPath("$.data.webhook_url").isEmpty())
			.andExpect(jsonPath("$.data.webhook_configured").value(true));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/slack 는 webhook null/생략 시 기존 secret을 유지한다")
	void configureSlackKeepsExistingWebhookWhenNullOrOmitted() throws Exception {
		String projectId = createProjectId("alert-slack-preserve-null-omitted");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(slackRequestBody("https://hooks.slack.com/services/T000/B000/BASE", "#ops", 0.45)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "webhook_url": null,
					  "channel": "#platform",
					  "min_confidence": 0.72
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "channel": "#platform-2",
					  "min_confidence": 0.74
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.channel").value("#platform-2"))
			.andExpect(jsonPath("$.data.webhook_configured").value(true));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/slack 는 최초 생성에서 webhook 누락 시 422를 반환한다")
	void configureSlackReturns422WhenWebhookMissingOnCreate() throws Exception {
		String projectId = createProjectId("alert-slack-create-missing-webhook");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/slack", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "channel": "#ops",
					  "min_confidence": 0.45
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("webhook_url must be a valid URI"));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/email 는 smtp.password 공백 시 기존 secret을 유지한다")
	void configureEmailKeepsExistingPasswordWhenBlank() throws Exception {
		String projectId = createProjectId("alert-email-preserve-password");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailRequestBody(
					"alerts@example.com",
					"\"ops@example.com\"",
					"smtp.example.com",
					587,
					"smtp-user",
					"smtp-secret-init",
					true,
					0.6
				)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "from": "alerts@example.com",
					  "recipients": ["platform@example.com"],
					  "smtp": {
					    "host": "smtp.example.com",
					    "port": 587,
					    "username": "smtp-user",
					    "password": "",
					    "starttls": true
					  },
					  "min_confidence": 0.74
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipients[0]").value("platform@example.com"))
			.andExpect(jsonPath("$.data.min_confidence").value(0.74))
			.andExpect(jsonPath("$.data.smtp.password").isEmpty())
			.andExpect(jsonPath("$.data.smtp.password_configured").value(true));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/email 는 smtp.password null/생략 시 기존 secret을 유지한다")
	void configureEmailKeepsExistingPasswordWhenNullOrOmitted() throws Exception {
		String projectId = createProjectId("alert-email-preserve-null-omitted");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailRequestBody(
					"alerts@example.com",
					"\"ops@example.com\"",
					"smtp.example.com",
					587,
					"smtp-user",
					"smtp-secret-init",
					true,
					0.6
				)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "from": "alerts@example.com",
					  "recipients": ["platform@example.com"],
					  "smtp": {
					    "host": "smtp.example.com",
					    "port": 587,
					    "username": "smtp-user",
					    "password": null,
					    "starttls": true
					  },
					  "min_confidence": 0.73
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "from": "alerts@example.com",
					  "recipients": ["platform2@example.com"],
					  "smtp": {
					    "host": "smtp.example.com",
					    "port": 587,
					    "username": "smtp-user",
					    "starttls": true
					  },
					  "min_confidence": 0.75
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(get("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipients[0]").value("platform2@example.com"))
			.andExpect(jsonPath("$.data.smtp.password").isEmpty())
			.andExpect(jsonPath("$.data.smtp.password_configured").value(true));
	}

	@Test
	@DisplayName("POST /v1/projects/{project_id}/alerts/email 는 최초 생성에서 smtp.password 누락 시 422를 반환한다")
	void configureEmailReturns422WhenPasswordMissingOnCreate() throws Exception {
		String projectId = createProjectId("alert-email-create-missing-password");

		mockMvc.perform(post("/v1/projects/{project_id}/alerts/email", projectId)
				.header("Authorization", "Bearer alert-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "from": "alerts@example.com",
					  "recipients": ["ops@example.com"],
					  "smtp": {
					    "host": "smtp.example.com",
					    "port": 587,
					    "username": "smtp-user",
					    "starttls": true
					  },
					  "min_confidence": 0.6
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("smtp.password must not be blank"));
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
		assertThat(actor)
			.startsWith("token:")
			.doesNotContain("actor-token-a")
			.doesNotContain("actor-token-b");

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

	@Test
	@DisplayName("GET /v1/projects/{project_id}/audit-logs 는 cursor가 음수면 422를 반환한다")
	void listAuditLogsReturns422WhenCursorNegative() throws Exception {
		String projectId = createProjectId("audit-log-cursor-negative");

		mockMvc.perform(get("/v1/projects/{project_id}/audit-logs", projectId)
				.header("Authorization", "Bearer reader-token")
				.queryParam("cursor", "-1"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("cursor must be a non-negative integer"));
	}

	@Test
	@DisplayName("GET /v1/projects/{project_id}/audit-logs 는 cursor가 숫자가 아니면 422를 반환한다")
	void listAuditLogsReturns422WhenCursorInvalid() throws Exception {
		String projectId = createProjectId("audit-log-cursor-invalid");

		mockMvc.perform(get("/v1/projects/{project_id}/audit-logs", projectId)
				.header("Authorization", "Bearer reader-token")
				.queryParam("cursor", "abc"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("cursor must be a non-negative integer"));
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
