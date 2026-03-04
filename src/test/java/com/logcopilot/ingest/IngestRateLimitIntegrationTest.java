package com.logcopilot.ingest;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"logcopilot.ingest.rate-limit.max-requests=2",
	"logcopilot.ingest.rate-limit.window=PT10M"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IngestRateLimitIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("POST /v1/ingest/events 는 토큰별 요청량 제한을 넘으면 429를 반환한다")
	void ingestEventsReturns429WhenRateLimitExceeded() throws Exception {
		String projectId = createProjectId("ingest-rate-limit");

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(projectId, "batch-1")))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(projectId, "batch-2")))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(projectId, "batch-3")))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error.code").value("too_many_requests"))
			.andExpect(jsonPath("$.error.message").value("Ingest rate limit exceeded"))
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	@DisplayName("POST /v1/ingest/events 는 같은 Idempotency-Key 재시도 시 rate-limit quota를 추가 소모하지 않는다")
	void ingestEventsDoesNotConsumeQuotaForSameIdempotencyKeyRetry() throws Exception {
		String projectId = createProjectId("ingest-rate-limit-idempotency");

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(projectId, "batch-1")))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(projectId, "batch-1")))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(projectId, "batch-2")))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(projectId, "batch-3")))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error.code").value("too_many_requests"))
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	@DisplayName("POST /v1/ingest/otlp/logs 는 토큰별 요청량 제한을 넘으면 429를 반환한다")
	void ingestOtlpLogsReturns429WhenRateLimitExceeded() throws Exception {
		createProjectId("ingest-rate-limit-otlp");

		mockMvc.perform(post("/v1/ingest/otlp/logs")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "otlp-1")
				.contentType("application/x-protobuf")
				.content(new byte[] {0x0A, 0x01, 0x01}))
			.andExpect(status().isNotImplemented());

		mockMvc.perform(post("/v1/ingest/otlp/logs")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "otlp-2")
				.contentType("application/x-protobuf")
				.content(new byte[] {0x0A, 0x01, 0x02}))
			.andExpect(status().isNotImplemented());

		mockMvc.perform(post("/v1/ingest/otlp/logs")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "otlp-3")
				.contentType("application/x-protobuf")
				.content(new byte[] {0x0A, 0x01, 0x03}))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error.code").value("too_many_requests"))
			.andExpect(jsonPath("$.error.message").value("Ingest rate limit exceeded"))
			.andExpect(header().exists("Retry-After"));
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

	private String ingestEventsRequestBody(String projectId, String batchId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("project_id", projectId);
		body.put("source", "loki");
		body.put("batch_id", batchId);
		body.put("events", List.of(event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "failure")));
		return objectMapper.writeValueAsString(body);
	}

	private Map<String, Object> event(
		String eventId,
		String timestamp,
		String service,
		String severity,
		String message
	) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("event_id", eventId);
		event.put("timestamp", timestamp);
		event.put("service", service);
		event.put("severity", severity);
		event.put("message", message);
		event.put("trace_id", null);
		event.put("error_code", null);
		event.put("stack_trace", null);
		event.put("attributes", Map.of());
		return event;
	}

	private String jsonValue(MvcResult result, String pointer) throws Exception {
		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode node = root.at(pointer);
		assertThat(node.isMissingNode()).isFalse();
		if (node.isNull()) {
			return null;
		}
		return node.asText();
	}
}
