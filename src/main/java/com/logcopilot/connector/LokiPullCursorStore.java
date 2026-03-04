package com.logcopilot.connector;

/**
 * Stores committed Loki pull cursors per project.
 * Implementations should preserve monotonic progress semantics.
 */
public interface LokiPullCursorStore {

	/**
	 * Returns the latest committed cursor for a project.
	 * Implementations should return {@code 0} when no cursor exists.
	 */
	long readCursor(String projectId);

	/**
	 * Commits the next cursor for a project.
	 * Implementations should keep cursor monotonic (non-decreasing) and normalize negative values.
	 */
	void commit(String projectId, long nextCursor);
}
