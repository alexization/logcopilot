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

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IngestEndpointsContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("POST /v1/ingest/events 는 유효한 요청이면 202를 반환한다")
	void ingestEventsReturns202OnAcceptedRequest() throws Exception {
		String projectId = createProjectId("ingest-accepted");

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(
					projectId,
					"loki",
					"batch-1",
					List.of(
						event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "first failure"),
						event("evt-2", "2026-03-03T03:00:30Z", "api", "warn", "second warning")
					)
				)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.accepted").value(true))
			.andExpect(jsonPath("$.data.ingestion_id").isString())
			.andExpect(jsonPath("$.data.received_events").value(2))
			.andExpect(jsonPath("$.data.deduplicated_events").value(0));
	}

	@Test
	@DisplayName("POST /v1/ingest/events 는 같은 Idempotency-Key 재요청 시 동일 결과를 반환한다")
	void ingestEventsReturnsSameResultForDuplicateIdempotencyKey() throws Exception {
		String projectId = createProjectId("ingest-idempotency");
		String idempotencyKey = "idem-" + UUID.randomUUID();
		String body = ingestEventsRequestBody(
			projectId,
			"loki",
			"batch-1",
			List.of(
				event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "first failure"),
				event("evt-1", "2026-03-03T03:00:01Z", "api", "error", "duplicate failure")
			)
		);

		MvcResult first = mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.received_events").value(2))
			.andExpect(jsonPath("$.data.deduplicated_events").value(1))
			.andReturn();

		String firstIngestionId = jsonValue(first, "/data/ingestion_id");

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.ingestion_id").value(firstIngestionId))
			.andExpect(jsonPath("$.data.received_events").value(2))
			.andExpect(jsonPath("$.data.deduplicated_events").value(1));
	}

	@Test
	@DisplayName("POST /v1/ingest/events 는 인증 누락 시 401을 반환한다")
	void ingestEventsRejectsMissingBearerToken() throws Exception {
		String projectId = createProjectId("ingest-auth");

		mockMvc.perform(post("/v1/ingest/events")
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(
					projectId,
					"loki",
					"batch-1",
					List.of(event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "failure"))
				)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	@DisplayName("POST /v1/ingest/events 는 source 값이 잘못되면 422를 반환한다")
	void ingestEventsReturns422OnInvalidSource() throws Exception {
		String projectId = createProjectId("ingest-validation");

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(
					projectId,
					"unknown",
					"batch-1",
					List.of(event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "failure"))
				)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("source must be one of: loki, otlp, custom"));
	}

	@Test
	@DisplayName("POST /v1/ingest/events 는 source가 null이면 422를 반환한다")
	void ingestEventsReturns422WhenSourceIsNull() throws Exception {
		String projectId = createProjectId("ingest-source-null");

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(
					projectId,
					null,
					"batch-1",
					List.of(event("evt-1", "2026-03-03T03:00:00Z", "api", "error", "failure"))
				)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("source must be one of: loki, otlp, custom"));
	}

	@Test
	@DisplayName("POST /v1/ingest/events 는 severity가 null이면 422를 반환한다")
	void ingestEventsReturns422WhenSeverityIsNull() throws Exception {
		String projectId = createProjectId("ingest-severity-null");

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(ingestEventsRequestBody(
					projectId,
					"loki",
					"batch-1",
					List.of(event("evt-1", "2026-03-03T03:00:00Z", "api", null, "failure"))
				)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("severity must be one of: debug, info, warn, error, fatal"));
	}

	@Test
	@DisplayName("POST /v1/ingest/events 는 events가 null이면 422를 반환한다")
	void ingestEventsReturns422WhenEventsIsNull() throws Exception {
		String projectId = createProjectId("ingest-events-null");
		String body = """
			{
			  "project_id": "%s",
			  "source": "loki",
			  "batch_id": "batch-1",
			  "events": null
			}
			""".formatted(projectId);

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("events size must be between 1 and 5000"));
	}

	@Test
	@DisplayName("POST /v1/ingest/events 는 events 항목에 null이 포함되면 422를 반환한다")
	void ingestEventsReturns422WhenEventsContainNullItem() throws Exception {
		String projectId = createProjectId("ingest-events-null-item");
		String body = """
			{
			  "project_id": "%s",
			  "source": "loki",
			  "batch_id": "batch-1",
			  "events": [null]
			}
			""".formatted(projectId);

		mockMvc.perform(post("/v1/ingest/events")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("validation_error"))
			.andExpect(jsonPath("$.error.message").value("events must not contain null items"));
	}

	@Test
	@DisplayName("POST /v1/ingest/otlp/logs 는 MVP 기본에서 501을 반환한다")
	void ingestOtlpLogsReturns501WhenReserved() throws Exception {
		mockMvc.perform(post("/v1/ingest/otlp/logs")
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "idem-" + UUID.randomUUID())
				.contentType("application/x-protobuf")
				.content(new byte[] {0x0A, 0x01, 0x01}))
			.andExpect(status().isNotImplemented())
			.andExpect(jsonPath("$.error.code").value("not_implemented"))
			.andExpect(jsonPath("$.error.message").value("OTLP ingest endpoint is reserved in MVP"));
	}

	private String createProjectId(String namePrefix) throws Exception {
		String projectName = namePrefix + "-" + UUID.randomUUID();

		MvcResult result = mockMvc.perform(post("/v1/projects")
				.header("Authorization", "Bearer test-token")
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

	private String ingestEventsRequestBody(
		String projectId,
		String source,
		String batchId,
		List<Map<String, Object>> events
	) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("project_id", projectId);
		body.put("source", source);
		body.put("batch_id", batchId);
		body.put("events", events);
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
		return event;
	}

	private String jsonValue(MvcResult result, String pointer) throws Exception {
		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		return root.at(pointer).asText();
	}
}
