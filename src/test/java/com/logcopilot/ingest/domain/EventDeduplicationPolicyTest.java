package com.logcopilot.ingest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventDeduplicationPolicyTest {

	private final EventDeduplicationPolicy policy = new EventDeduplicationPolicy();

	@Test
	@DisplayName("EventDeduplicationPolicy는 중복 event_id 개수를 계산한다")
	void countsDuplicatedEventIds() {
		List<CanonicalLogEvent> events = List.of(
			event("event-1"),
			event("event-2"),
			event("event-1")
		);

		int deduplicated = policy.countDeduplicatedEvents(events);

		assertThat(deduplicated).isEqualTo(1);
	}

	@Test
	@DisplayName("EventDeduplicationPolicy는 중복이 없으면 0을 반환한다")
	void returnsZeroWhenAllEventIdsUnique() {
		List<CanonicalLogEvent> events = List.of(event("event-1"), event("event-2"));

		int deduplicated = policy.countDeduplicatedEvents(events);

		assertThat(deduplicated).isZero();
	}

	private CanonicalLogEvent event(String eventId) {
		return new CanonicalLogEvent(
			eventId,
			"2026-03-03T03:00:00Z",
			"api",
			"error",
			"boom",
			null,
			null,
			null,
			Map.of()
		);
	}
}
