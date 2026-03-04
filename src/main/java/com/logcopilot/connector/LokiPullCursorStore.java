package com.logcopilot.connector;

public interface LokiPullCursorStore {

	long readCursor(String projectId);

	void commit(String projectId, long nextCursor);
}
