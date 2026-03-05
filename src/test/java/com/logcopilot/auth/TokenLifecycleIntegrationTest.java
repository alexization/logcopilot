package com.logcopilot.auth;

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

@SpringBootTest(properties = "logcopilot.auth.seed-default-tokens=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TokenLifecycleIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("토큰 발급/회전/폐기 수명주기를 통해 인증 경계가 갱신된다")
	void tokenLifecycleIssueRotateRevoke() throws Exception {
		BootstrapFixture bootstrap = bootstrap();

		mockMvc.perform(get("/v1/tokens")
				.header("Authorization", "Bearer " + bootstrap.operatorToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data.length()").value(2));

		MvcResult issueResult = mockMvc.perform(post("/v1/tokens")
				.header("Authorization", "Bearer " + bootstrap.operatorToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "ci-agent",
					  "role": "api"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isString())
			.andExpect(jsonPath("$.data.role").value("api"))
			.andExpect(jsonPath("$.data.status").value("active"))
			.andExpect(jsonPath("$.data.token").isString())
			.andReturn();

		JsonNode issued = read(issueResult);
		String issuedTokenId = issued.at("/data/id").asText();
		String issuedTokenValue = issued.at("/data/token").asText();
		assertThat(issuedTokenId).isNotBlank();
		assertThat(issuedTokenValue).isNotBlank();

		mockMvc.perform(get("/v1/system/info")
				.header("Authorization", "Bearer " + issuedTokenValue))
			.andExpect(status().isOk());

		mockMvc.perform(get("/v1/tokens")
				.header("Authorization", "Bearer " + issuedTokenValue))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("forbidden"));

		MvcResult rotateResult = mockMvc.perform(post("/v1/tokens/{token_id}/rotate", issuedTokenId)
				.header("Authorization", "Bearer " + bootstrap.operatorToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(issuedTokenId))
			.andExpect(jsonPath("$.data.role").value("api"))
			.andExpect(jsonPath("$.data.status").value("active"))
			.andExpect(jsonPath("$.data.rotated_at").isString())
			.andExpect(jsonPath("$.data.token").isString())
			.andReturn();

		String rotatedTokenValue = read(rotateResult).at("/data/token").asText();
		assertThat(rotatedTokenValue).isNotBlank().isNotEqualTo(issuedTokenValue);

		mockMvc.perform(get("/v1/system/info")
				.header("Authorization", "Bearer " + issuedTokenValue))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"));

		mockMvc.perform(get("/v1/system/info")
				.header("Authorization", "Bearer " + rotatedTokenValue))
			.andExpect(status().isOk());

		mockMvc.perform(post("/v1/tokens/{token_id}/revoke", issuedTokenId)
				.header("Authorization", "Bearer " + bootstrap.operatorToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "rotation complete"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(issuedTokenId))
			.andExpect(jsonPath("$.data.status").value("revoked"))
			.andExpect(jsonPath("$.data.revoked_at").isString());

		mockMvc.perform(get("/v1/system/info")
				.header("Authorization", "Bearer " + rotatedTokenValue))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"));
	}

	@Test
	@DisplayName("ingest role 토큰을 발급하면 ingest 엔드포인트에 접근할 수 있다")
	void issuedIngestTokenCanCallIngestEndpoint() throws Exception {
		BootstrapFixture bootstrap = bootstrap();

		MvcResult issueResult = mockMvc.perform(post("/v1/tokens")
				.header("Authorization", "Bearer " + bootstrap.operatorToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "collector-2",
					  "role": "ingest"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.role").value("ingest"))
			.andExpect(jsonPath("$.data.token").isString())
			.andReturn();

		String ingestToken = read(issueResult).at("/data/token").asText();
		assertThat(ingestToken).isNotBlank();

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer " + ingestToken)
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestBody(bootstrap.projectId())))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.accepted").value(true));
	}

	@Test
	@DisplayName("토큰 발급은 role 값이 잘못되면 422를 반환한다")
	void issueTokenReturns422ForInvalidRole() throws Exception {
		BootstrapFixture bootstrap = bootstrap();

		mockMvc.perform(post("/v1/tokens")
				.header("Authorization", "Bearer " + bootstrap.operatorToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "broken-role",
					  "role": "viewer"
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("role must be one of: operator, api, ingest"));
	}

	private BootstrapFixture bootstrap() throws Exception {
		MvcResult result = mockMvc.perform(post("/v1/bootstrap/initialize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "project_name": "bootstrap-for-token",
					  "environment": "prod",
					  "operator_token_name": "root-admin",
					  "ingest_token_name": "primary-ingest"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();

		JsonNode payload = read(result);
		return new BootstrapFixture(
			payload.at("/data/project/id").asText(),
			payload.at("/data/operator_token/token").asText(),
			payload.at("/data/ingest_token/token").asText()
		);
	}

	private JsonNode read(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private String ingestBody(String projectId) {
		return """
			{
			  "project_id": "%s",
			  "source": "loki",
			  "batch_id": "batch-1",
			  "events": [
			    {
			      "event_id": "evt-1",
			      "timestamp": "2026-03-04T00:00:00Z",
			      "service": "api",
			      "severity": "error",
			      "message": "failure"
			    }
			  ]
			}
			""".formatted(projectId);
	}

	private record BootstrapFixture(
		String projectId,
		String operatorToken,
		String ingestToken
	) {
	}
}
