package com.logcopilot.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLokiPullCursorStoreTest {

	@Test
	@DisplayName("InMemoryLokiPullCursorStore는 초기 cursor를 0으로 반환한다")
	void returnsZeroWhenCursorMissing() {
		InMemoryLokiPullCursorStore store = new InMemoryLokiPullCursorStore();

		assertThat(store.readCursor("project-1")).isZero();
	}

	@Test
	@DisplayName("InMemoryLokiPullCursorStore는 더 큰 cursor만 commit해 역행을 방지한다")
	void keepsLargestCommittedCursor() {
		InMemoryLokiPullCursorStore store = new InMemoryLokiPullCursorStore();

		store.commit("project-1", 10L);
		store.commit("project-1", 5L);
		store.commit("project-1", 12L);

		assertThat(store.readCursor("project-1")).isEqualTo(12L);
	}

	@Test
	@DisplayName("InMemoryLokiPullCursorStore는 음수 cursor commit 입력을 0으로 정규화한다")
	void normalizesNegativeCursorCommit() {
		InMemoryLokiPullCursorStore store = new InMemoryLokiPullCursorStore();

		store.commit("project-1", 7L);
		store.commit("project-1", -1L);

		assertThat(store.readCursor("project-1")).isEqualTo(7L);
	}
}
