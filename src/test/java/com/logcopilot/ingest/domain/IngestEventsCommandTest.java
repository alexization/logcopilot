package com.logcopilot.ingest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestEventsCommandTest {

	@Test
	@DisplayName("IngestEventsCommand는 events 리스트를 방어적으로 복사한다")
	void copiesEventsDefensively() {
		List<CanonicalLogEvent> mutableEvents = new ArrayList<>();
		mutableEvents.add(validEvent("event-1"));

		IngestEventsCommand command = new IngestEventsCommand("project-1", "loki", "batch-1", mutableEvents);

		mutableEvents.add(validEvent("event-2"));

		assertThat(command.events()).hasSize(1);
		assertThatThrownBy(() -> command.events().add(validEvent("event-3")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("IngestEventsCommand는 events가 null이면 빈 리스트를 사용한다")
	void replacesNullEventsWithEmptyList() {
		IngestEventsCommand command = new IngestEventsCommand("project-1", "loki", "batch-1", null);

		assertThat(command.events()).isEmpty();
	}

	@Test
	@DisplayName("IngestEventsCommand는 null 이벤트 요소를 보존한다")
	void keepsNullEventElementsForValidator() {
		List<CanonicalLogEvent> events = new ArrayList<>();
		events.add(null);
		events.add(validEvent("event-1"));

		IngestEventsCommand command = new IngestEventsCommand("project-1", "loki", "batch-1", events);

		assertThat(command.events()).hasSize(2);
		assertThat(command.events().get(0)).isNull();
	}

	private CanonicalLogEvent validEvent(String eventId) {
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
