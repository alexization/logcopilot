package com.logcopilot.connector;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class InMemoryLokiPullCursorStore implements LokiPullCursorStore {

	private final Map<String, Long> cursorByProjectId = new HashMap<>();

	@Override
	public synchronized long readCursor(String projectId) {
		return cursorByProjectId.getOrDefault(projectId, 0L);
	}

	@Override
	public synchronized void commit(String projectId, long nextCursor) {
		long normalizedCursor = Math.max(0L, nextCursor);
		cursorByProjectId.merge(projectId, normalizedCursor, Math::max);
	}
}
