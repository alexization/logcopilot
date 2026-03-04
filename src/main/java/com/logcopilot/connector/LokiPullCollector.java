package com.logcopilot.connector;

import com.logcopilot.ingest.IngestService;
import com.logcopilot.ingest.domain.IngestAcceptedResult;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.ingest.domain.IngestEventsCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LokiPullCollector {

	private static final Logger logger = LoggerFactory.getLogger(LokiPullCollector.class);

	private final LokiConnectorService lokiConnectorService;
	private final LokiPullClient lokiPullClient;
	private final LokiPullCursorStore lokiPullCursorStore;
	private final IngestService ingestService;
	private final Map<String, Instant> lastPulledAtByProjectId = new HashMap<>();

	public LokiPullCollector(
		LokiConnectorService lokiConnectorService,
		LokiPullClient lokiPullClient,
		LokiPullCursorStore lokiPullCursorStore,
		IngestService ingestService
	) {
		this.lokiConnectorService = lokiConnectorService;
		this.lokiPullClient = lokiPullClient;
		this.lokiPullCursorStore = lokiPullCursorStore;
		this.ingestService = ingestService;
	}

	@Scheduled(fixedDelayString = "${logcopilot.loki.pull.fixed-delay-ms:30000}")
	public void collectConfiguredProjects() {
		for (String projectId : lokiConnectorService.listConfiguredProjectIds()) {
			collectProject(projectId);
		}
	}

	synchronized void collectProject(String projectId) {
		Optional<LokiConnectorService.LokiConnector> connector = lokiConnectorService.findByProjectId(projectId);
		if (connector.isEmpty()) {
			return;
		}

		Instant now = Instant.now();
		if (!isDue(projectId, connector.get().pollIntervalSeconds(), now)) {
			return;
		}

		long committedCursor = lokiPullCursorStore.readCursor(projectId);
		try {
			LokiPullClient.PullBatch pullBatch = lokiPullClient.pull(projectId, connector.get(), committedCursor);
			if (pullBatch == null || pullBatch.events() == null || pullBatch.events().isEmpty()) {
				lastPulledAtByProjectId.put(projectId, now);
				return;
			}

			List<CanonicalLogEvent> events = pullBatch.events();
			String batchId = normalizeBatchId(pullBatch.batchId());
			IngestAcceptedResult acceptedResult = ingestService.ingestPulledEvents(new IngestEventsCommand(
				projectId,
				"loki",
				batchId,
				events
			));
			if (acceptedResult.accepted()) {
				lokiPullCursorStore.commit(projectId, pullBatch.nextCursor());
				lastPulledAtByProjectId.put(projectId, now);
			}
		} catch (RuntimeException exception) {
			logger.warn("Loki pull failed for project_id={}", projectId, exception);
		}
	}

	private boolean isDue(String projectId, int pollIntervalSeconds, Instant now) {
		Instant lastPulledAt = lastPulledAtByProjectId.get(projectId);
		if (lastPulledAt == null) {
			return true;
		}
		return !lastPulledAt.plusSeconds(Math.max(1, pollIntervalSeconds)).isAfter(now);
	}

	private String normalizeBatchId(String batchId) {
		if (batchId == null || batchId.isBlank()) {
			return "pull-" + UUID.randomUUID();
		}
		return batchId;
	}
}
