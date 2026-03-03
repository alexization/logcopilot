package com.logcopilot.ingest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalLogEventTest {

	@Test
	@DisplayName("CanonicalLogEvent는 attributes를 방어적으로 복사한다")
	void copiesAttributesDefensively() {
		Map<String, Object> mutableAttributes = new HashMap<>();
		mutableAttributes.put("key", "before");

		CanonicalLogEvent event = new CanonicalLogEvent(
			"event-1",
			"2026-03-03T03:00:00Z",
			"api",
			"error",
			"boom",
			null,
			null,
			null,
			mutableAttributes
		);

		mutableAttributes.put("key", "after");

		assertThat(event.attributes()).containsEntry("key", "before");
		assertThatThrownBy(() -> event.attributes().put("new", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("CanonicalLogEvent는 attributes가 null이면 빈 맵으로 초기화한다")
	void replacesNullAttributesWithEmptyMap() {
		CanonicalLogEvent event = new CanonicalLogEvent(
			"event-1",
			"2026-03-03T03:00:00Z",
			"api",
			"error",
			"boom",
			null,
			null,
			null,
			null
		);

		assertThat(event.attributes()).isEmpty();
	}
}
