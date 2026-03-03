package com.logcopilot.ingest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestAcceptedResultTest {

	@Test
	@DisplayName("IngestAcceptedResult는 유효한 불변식을 허용한다")
	void allowsValidInvariant() {
		assertThatCode(() -> new IngestAcceptedResult(true, "ing-1", 2, 1))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("IngestAcceptedResult는 음수 카운트를 거부한다")
	void rejectsNegativeCounts() {
		assertThatThrownBy(() -> new IngestAcceptedResult(true, "ing-1", -1, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Event counts must be non-negative");
	}

	@Test
	@DisplayName("IngestAcceptedResult는 deduplicatedEvents가 receivedEvents보다 크면 거부한다")
	void rejectsInvalidDeduplicatedCount() {
		assertThatThrownBy(() -> new IngestAcceptedResult(true, "ing-1", 1, 2))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("deduplicatedEvents cannot exceed receivedEvents");
	}

	@Test
	@DisplayName("IngestAcceptedResult는 ingestionId가 비어있으면 거부한다")
	void rejectsBlankIngestionId() {
		assertThatThrownBy(() -> new IngestAcceptedResult(true, " ", 1, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("ingestionId must not be blank");
	}
}
