package com.logcopilot.ingest.domain;

import com.logcopilot.common.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestRequestValidatorTest {

	private final IngestRequestValidator validator = new IngestRequestValidator();

	@Test
	@DisplayName("IngestRequestValidator는 유효한 요청을 통과시킨다")
	void validatesValidRequest() {
		IngestEventsCommand request = validRequest("loki", "error");

		assertThatCode(() -> validator.validate(request, true))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("IngestRequestValidator는 source가 null이면 예외를 던진다")
	void throwsWhenSourceIsNull() {
		IngestEventsCommand request = validRequest(null, "error");

		assertThatThrownBy(() -> validator.validate(request, true))
			.isInstanceOf(ValidationException.class)
			.hasMessage("source must be one of: loki, otlp, custom");
	}

	@Test
	@DisplayName("IngestRequestValidator는 severity가 null이면 예외를 던진다")
	void throwsWhenSeverityIsNull() {
		IngestEventsCommand request = validRequest("loki", null);

		assertThatThrownBy(() -> validator.validate(request, true))
			.isInstanceOf(ValidationException.class)
			.hasMessage("severity must be one of: debug, info, warn, error, fatal");
	}

	@Test
	@DisplayName("IngestRequestValidator는 events가 null이면 예외를 던진다")
	void throwsWhenEventsIsNull() {
		IngestEventsCommand request = new IngestEventsCommand("project-1", "loki", "batch-1", null);

		assertThatThrownBy(() -> validator.validate(request, true))
			.isInstanceOf(ValidationException.class)
			.hasMessage("events size must be between 1 and 5000");
	}

	@Test
	@DisplayName("IngestRequestValidator는 project가 없으면 예외를 던진다")
	void throwsWhenProjectDoesNotExist() {
		IngestEventsCommand request = validRequest("loki", "error");

		assertThatThrownBy(() -> validator.validate(request, false))
			.isInstanceOf(ValidationException.class)
			.hasMessage("project_id must reference an existing project");
	}

	@Test
	@DisplayName("IngestRequestValidator는 events가 비어있으면 예외를 던진다")
	void throwsWhenEventsIsEmpty() {
		IngestEventsCommand request = new IngestEventsCommand("project-1", "loki", "batch-1", List.of());

		assertThatThrownBy(() -> validator.validate(request, true))
			.isInstanceOf(ValidationException.class)
			.hasMessage("events size must be between 1 and 5000");
	}

	@Test
	@DisplayName("IngestRequestValidator는 events가 5000개를 초과하면 예외를 던진다")
	void throwsWhenEventsSizeExceedsLimit() {
		List<CanonicalLogEvent> events = List.of(new CanonicalLogEvent(
			"event-1",
			"2026-03-03T03:00:00Z",
			"api",
			"error",
			"boom",
			null,
			null,
			null,
			Map.of()
		));
		List<CanonicalLogEvent> tooManyEvents = java.util.stream.Stream.generate(() -> events.get(0))
			.limit(5001)
			.toList();
		IngestEventsCommand request = new IngestEventsCommand("project-1", "loki", "batch-1", tooManyEvents);

		assertThatThrownBy(() -> validator.validate(request, true))
			.isInstanceOf(ValidationException.class)
			.hasMessage("events size must be between 1 and 5000");
	}

	@Test
	@DisplayName("IngestRequestValidator는 source가 허용값이 아니면 예외를 던진다")
	void throwsWhenSourceIsInvalid() {
		IngestEventsCommand request = validRequest("invalid", "error");

		assertThatThrownBy(() -> validator.validate(request, true))
			.isInstanceOf(ValidationException.class)
			.hasMessage("source must be one of: loki, otlp, custom");
	}

	@Test
	@DisplayName("IngestRequestValidator는 severity가 허용값이 아니면 예외를 던진다")
	void throwsWhenSeverityIsInvalid() {
		IngestEventsCommand request = validRequest("loki", "unknown");

		assertThatThrownBy(() -> validator.validate(request, true))
			.isInstanceOf(ValidationException.class)
			.hasMessage("severity must be one of: debug, info, warn, error, fatal");
	}

	@Test
	@DisplayName("IngestRequestValidator는 batchId가 비어있으면 예외를 던진다")
	void throwsWhenBatchIdIsBlank() {
		IngestEventsCommand request = new IngestEventsCommand(
			"project-1",
			"loki",
			" ",
			List.of(validEvent("error"))
		);

		assertThatThrownBy(() -> validator.validate(request, true))
			.isInstanceOf(ValidationException.class)
			.hasMessage("batch_id must not be blank");
	}

	@Test
	@DisplayName("IngestRequestValidator는 projectId가 비어있으면 예외를 던진다")
	void throwsWhenProjectIdIsBlank() {
		IngestEventsCommand request = new IngestEventsCommand(
			" ",
			"loki",
			"batch-1",
			List.of(validEvent("error"))
		);

		assertThatThrownBy(() -> validator.validate(request, true))
			.isInstanceOf(ValidationException.class)
			.hasMessage("project_id is required");
	}

	private IngestEventsCommand validRequest(String source, String severity) {
		return new IngestEventsCommand("project-1", source, "batch-1", List.of(validEvent(severity)));
	}

	private CanonicalLogEvent validEvent(String severity) {
		return new CanonicalLogEvent("event-1", "2026-03-03T03:00:00Z", "api", severity, "boom", null, null, null, Map.of());
	}
}
