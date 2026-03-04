package com.logcopilot.connector;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class NoopLokiPullClient implements LokiPullClient {

	@Override
	public PullBatch pull(String projectId, LokiConnectorService.LokiConnector connector, long cursor) {
		return new PullBatch(
			"loki-pull-" + projectId + "-" + cursor,
			cursor,
			List.of()
		);
	}
}
