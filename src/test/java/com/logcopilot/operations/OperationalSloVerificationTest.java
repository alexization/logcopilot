package com.logcopilot.operations;

import com.logcopilot.LogcopilotApplication;
import com.logcopilot.common.metrics.SloMetricsCalculator;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.incident.IncidentService;
import com.logcopilot.incident.domain.IncidentSummary;
import com.logcopilot.llm.LlmOAuthProperties;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalSloVerificationTest {

	private static final int SAMPLE_SIZE = 120;
	private static final long INCIDENT_P95_TARGET_MS = 60_000L;
	private static final long ANALYSIS_P95_TARGET_MS = 120_000L;
	private static final double INGEST_SUCCESS_RATE_TARGET = 99.5;

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("SQLite on-path 기준으로 incident/analyze/ingest SLO 목표를 만족한다")
	void verifiesOperationalSloTargets() {
		Path dbPath = tempDir.resolve("logcopilot-t22-slo.sqlite");
		deleteIfExists(dbPath);
		String encryptionSecret = "ephemeral-" + UUID.randomUUID();

		try (ConfigurableApplicationContext context = startContext(dbPath, encryptionSecret)) {
			ProjectService projectService = context.getBean(ProjectService.class);
			IncidentService incidentService = context.getBean(IncidentService.class);
			Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
			assertThat(port).isNotNull();
			String baseUrl = "http://localhost:" + port;
			ProjectDto project = projectService.create("t22-slo-project-" + UUID.randomUUID(), "prod");
			String projectId = project.id();

			List<Long> incidentCreationLatencies = measureIncidentCreationLatencies(incidentService, projectId);
			long incidentP95 = SloMetricsCalculator.percentileMillis(incidentCreationLatencies, 95.0);

			List<Long> analysisLatencies = measureAnalysisLatencies(incidentService, projectId);
			long analysisP95 = SloMetricsCalculator.percentileMillis(analysisLatencies, 95.0);

			IngestMeasurement ingestMeasurement = measureIngestSuccess(baseUrl, projectId);
			double ingestSuccessRate = SloMetricsCalculator.successRatePercent(
				ingestMeasurement.successCount(),
				ingestMeasurement.totalCount()
			);

			System.out.printf(
				"T22_SLO incident_p95_ms=%d analysis_p95_ms=%d ingest_success_rate=%.2f%%%n",
				incidentP95,
				analysisP95,
				ingestSuccessRate
			);

			assertThat(incidentP95).isLessThan(INCIDENT_P95_TARGET_MS);
			assertThat(analysisP95).isLessThan(ANALYSIS_P95_TARGET_MS);
			assertThat(ingestSuccessRate).isGreaterThanOrEqualTo(INGEST_SUCCESS_RATE_TARGET);
		}
	}

	private List<Long> measureIncidentCreationLatencies(IncidentService incidentService, String projectId) {
		List<Long> latencies = new ArrayList<>(SAMPLE_SIZE);
		for (int index = 0; index < SAMPLE_SIZE; index++) {
			long startedAt = System.nanoTime();
			incidentService.recordIngestedEvents(
				projectId,
				List.of(sampleEvent("incident-create", index, "svc-incident-" + index))
			);
			latencies.add(elapsedMillis(startedAt));
		}
		return latencies;
	}

	private List<Long> measureAnalysisLatencies(IncidentService incidentService, String projectId) {
		List<IncidentSummary> incidents = incidentService.list(projectId, null, null, null, 200).data();
		assertThat(incidents.size()).isGreaterThanOrEqualTo(SAMPLE_SIZE);

		List<Long> latencies = new ArrayList<>(SAMPLE_SIZE);
		for (int index = 0; index < SAMPLE_SIZE; index++) {
			IncidentSummary incident = incidents.get(index);
			long startedAt = System.nanoTime();
			incidentService.reanalyzeIncident(incident.id(), "t22-slo-check-" + index);
			latencies.add(elapsedMillis(startedAt));
		}
		return latencies;
	}

	private IngestMeasurement measureIngestSuccess(String baseUrl, String projectId) {
		HttpClient httpClient = HttpClient.newHttpClient();
		int successCount = 0;
		for (int index = 0; index < SAMPLE_SIZE; index++) {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/v1/ingest/events"))
				.header("Authorization", "Bearer ingest-token")
				.header("Idempotency-Key", "t22-idem-" + index)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(ingestEventsRequestBody(projectId, index)))
				.build();
			try {
				HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 202) {
					successCount++;
				}
			} catch (Exception exception) {
				throw new IllegalStateException("Failed to call ingest endpoint for SLO verification", exception);
			}
		}
		return new IngestMeasurement(successCount, SAMPLE_SIZE);
	}

	private String ingestEventsRequestBody(String projectId, int index) {
		return """
			{
			  "project_id": "%s",
			  "source": "loki",
			  "batch_id": "batch-%d",
			  "events": [
			    {
			      "event_id": "ingest-evt-%d",
			      "timestamp": "2026-03-04T00:00:%02dZ",
			      "service": "svc-ingest-%d",
			      "severity": "error",
			      "message": "failure-ingest-%d",
			      "trace_id": null,
			      "error_code": null,
			      "stack_trace": null,
			      "attributes": {
			        "phase": "ingest",
			        "index": "%d"
			      }
			    }
			  ]
			}
			""".formatted(
				projectId,
				index,
				index,
				index % 60,
				index,
				index,
				index
			);
	}

	private ConfigurableApplicationContext startContext(Path dbPath, String encryptionSecret) {
		return new SpringApplicationBuilder(LogcopilotApplication.class)
			.run(
				"--server.port=0",
				"--spring.task.scheduling.enabled=false",
				"--logcopilot.persistence.enabled=true",
				"--logcopilot.persistence.sqlite-path=" + dbPath.toAbsolutePath(),
				"--logcopilot.persistence.encryption-key=" + encryptionSecret,
				"--logcopilot.llm.oauth.mode=" + LlmOAuthProperties.Mode.STUB.name().toLowerCase()
			);
	}

	private void deleteIfExists(Path dbPath) {
		try {
			Files.deleteIfExists(dbPath);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to clean sqlite file: " + dbPath, exception);
		}
	}

	private CanonicalLogEvent sampleEvent(String phase, int index, String service) {
		return new CanonicalLogEvent(
			phase + "-evt-" + index,
			"2026-03-04T00:00:%02dZ".formatted(index % 60),
			service,
			"error",
			"failure-" + phase + "-" + index,
			null,
			null,
			null,
			Map.of("phase", phase, "index", String.valueOf(index))
		);
	}

	private long elapsedMillis(long startedAtNanos) {
		return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
	}

	private record IngestMeasurement(int successCount, int totalCount) {
	}
}
