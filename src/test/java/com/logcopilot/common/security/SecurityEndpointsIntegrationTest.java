package com.logcopilot.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityEndpointsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("공개 엔드포인트 healthz/readyz 는 인증 없이 접근 가능하다")
	void publicEndpointsAreAccessibleWithoutAuthorization() throws Exception {
		mockMvc.perform(get("/healthz"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/readyz"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("bootstrap 상태 조회 엔드포인트는 인증 없이 접근 가능하다")
	void bootstrapStatusIsAccessibleWithoutAuthorization() throws Exception {
		mockMvc.perform(get("/v1/bootstrap/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.bootstrapped").exists());
	}

	@Test
	@DisplayName("bootstrap 초기화 엔드포인트는 인증 없이 접근 가능하다")
	void bootstrapInitializeIsAccessibleWithoutAuthorization() throws Exception {
		mockMvc.perform(post("/v1/bootstrap/initialize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "project_name": "bootstrap-core",
					  "environment": "prod"
					}
					"""))
			.andExpect(status().is(not(401)));
	}

	@Test
	@DisplayName("OAuth callback 엔드포인트는 인증 없이 접근 가능하다")
	void oauthCallbackIsAccessibleWithoutAuthorization() throws Exception {
		// 이 테스트는 보안 경계 확인이 목적이라, 인증 실패(401)만 아니면 허용한다.
		mockMvc.perform(get("/v1/projects/{project_id}/llm-oauth/{provider}/callback", "project-1", "openai")
				.queryParam("code", "oauth-code")
				.queryParam("state", "oauth-state"))
			.andExpect(status().is4xxClientError())
			.andExpect(jsonPath("$.error.code", not("unauthorized")));
	}

	@Test
	@DisplayName("보호 엔드포인트는 무인증 요청에서 JSON 파싱 전에 401로 차단한다")
	void protectedEndpointsReturn401BeforeJsonBindingWhenUnauthorized() throws Exception {
		mockMvc.perform(post("/v1/projects")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"broken\","))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("ingest 토큰으로 일반 API 접근 시 403을 반환한다")
	void apiEndpointsReturn403ForIngestToken() throws Exception {
		mockMvc.perform(get("/v1/system/info")
				.header("Authorization", "Bearer ingest-token"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("forbidden"))
			.andExpect(jsonPath("$.error.message").value("Access denied"));
	}

	@Test
	@DisplayName("일반 bearer 토큰으로 ingest API 접근 시 403을 반환한다")
	void ingestEndpointsReturn403ForBearerToken() throws Exception {
		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer api-token")
				.header("Idempotency-Key", "idem-security-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "project_id": "project-1",
					  "source": "loki",
					  "batch_id": "batch-1",
					  "events": [
					    {
					      "event_id": "evt-1",
					      "timestamp": "2026-03-03T03:00:00Z",
					      "service": "api",
					      "severity": "error",
					      "message": "failure"
					    }
					  ]
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("forbidden"))
			.andExpect(jsonPath("$.error.message").value("Access denied"));
	}

	@Test
	@DisplayName("등록되지 않은 bearer 토큰은 401로 차단한다")
	void unknownBearerTokenReturns401() throws Exception {
		mockMvc.perform(get("/v1/system/info")
				.header("Authorization", "Bearer unknown-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("favicon 요청은 인증 경계를 통과하고 자원이 없으면 404를 반환한다")
	void faviconRequestIsNotBlockedByAuthentication() throws Exception {
		mockMvc.perform(get("/favicon.ico"))
			.andExpect(status().isNotFound());
	}
}
