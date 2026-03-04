package com.logcopilot.connector;

import com.logcopilot.ingest.domain.CanonicalLogEvent;

import java.util.List;

public interface LokiPullClient {

	PullBatch pull(String projectId, LokiConnectorService.LokiConnector connector, long cursor);

	record PullBatch(
		String batchId,
		long nextCursor,
		List<CanonicalLogEvent> events
	) {
	}
}
