package com.logcopilot.ingest;

import com.logcopilot.common.error.ValidationException;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.ingest.domain.EventDeduplicationPolicy;
import com.logcopilot.ingest.domain.IngestAcceptedResult;
import com.logcopilot.ingest.domain.IngestEventsCommand;
import com.logcopilot.ingest.domain.IngestRequestValidator;
import com.logcopilot.incident.IncidentService;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

	@Mock
	private ProjectService projectService;

	@Mock
	private IngestIdempotencyStore idempotencyStore;

	@Mock
	private IngestRequestValidator ingestRequestValidator;

	@Mock
	private EventDeduplicationPolicy eventDeduplicationPolicy;

	@Mock
	private IncidentService incidentService;

	@InjectMocks
	private IngestService ingestService;

	@Test
	@DisplayName("IngestService는 같은 idempotency key가 있으면 캐시 결과를 반환한다")
	void returnsCachedAcceptedResultWhenIdempotencyKeyExists() {
		IngestEventsCommand request = validRequest();
		IngestAcceptedResult cached = new IngestAcceptedResult(true, "ing-1", 2, 1);
		when(idempotencyStore.computeIfAbsent(eq("push:key-1"), any())).thenReturn(cached);

		IngestAcceptedResult result = ingestService.ingestEvents("key-1", request);

		assertThat(result).isEqualTo(cached);
		verify(projectService, never()).existsById(any());
		verify(ingestRequestValidator, never()).validate(any(), anyBoolean());
		verify(incidentService, never()).recordIngestedEvents(any(), any());
	}

	@Test
	@DisplayName("IngestService는 신규 idempotency key면 검증 후 결과를 계산한다")
	void validatesAndBuildsAcceptedResultOnFirstRequest() {
		IngestEventsCommand request = validRequest();
		when(idempotencyStore.computeIfAbsent(eq("push:key-1"), any())).thenAnswer(invocation -> {
			Function<String, IngestAcceptedResult> mapper = invocation.getArgument(1);
			return mapper.apply("push:key-1");
		});
		when(projectService.existsById("project-1")).thenReturn(true);
		doNothing().when(ingestRequestValidator).validate(request, true);
		when(eventDeduplicationPolicy.countDeduplicatedEvents(request.events())).thenReturn(1);

		IngestAcceptedResult result = ingestService.ingestEvents("key-1", request);

		assertThat(result.accepted()).isTrue();
		assertThat(result.receivedEvents()).isEqualTo(2);
		assertThat(result.deduplicatedEvents()).isEqualTo(1);
		assertThat(result.ingestionId()).isNotBlank();
		verify(projectService).existsById("project-1");
		verify(ingestRequestValidator).validate(request, true);
		verify(eventDeduplicationPolicy).countDeduplicatedEvents(request.events());
		verify(incidentService).recordIngestedEvents("project-1", request.events());
	}

	@Test
	@DisplayName("IngestService는 프로젝트가 존재하지 않을 때 검증 예외를 전파한다")
	void propagatesValidationExceptionWhenProjectMissing() {
		IngestEventsCommand request = validRequest();
		when(idempotencyStore.computeIfAbsent(eq("push:key-1"), any())).thenAnswer(invocation -> {
			Function<String, IngestAcceptedResult> mapper = invocation.getArgument(1);
			return mapper.apply("push:key-1");
		});
		when(projectService.existsById("project-1")).thenReturn(false);
		doThrow(new ValidationException("project_id must reference an existing project"))
			.when(ingestRequestValidator).validate(request, false);

		assertThatThrownBy(() -> ingestService.ingestEvents("key-1", request))
			.isInstanceOf(ValidationException.class)
			.hasMessage("project_id must reference an existing project");
		verify(eventDeduplicationPolicy, never()).countDeduplicatedEvents(any());
		verify(incidentService, never()).recordIngestedEvents(any(), any());
	}

	@Test
	@DisplayName("IngestService는 요청 검증 실패 예외를 그대로 전파한다")
	void propagatesValidationExceptionWhenRequestInvalid() {
		IngestEventsCommand request = validRequest();
		when(idempotencyStore.computeIfAbsent(eq("push:key-1"), any())).thenAnswer(invocation -> {
			Function<String, IngestAcceptedResult> mapper = invocation.getArgument(1);
			return mapper.apply("push:key-1");
		});
		when(projectService.existsById("project-1")).thenReturn(true);
		doThrow(new ValidationException("events size must be between 1 and 5000"))
			.when(ingestRequestValidator).validate(request, true);

		assertThatThrownBy(() -> ingestService.ingestEvents("key-1", request))
			.isInstanceOf(ValidationException.class)
			.hasMessage("events size must be between 1 and 5000");
		verify(eventDeduplicationPolicy, never()).countDeduplicatedEvents(any());
		verify(incidentService, never()).recordIngestedEvents(any(), any());
	}

	@Test
	@DisplayName("IngestService는 pull ingest에서 project_id 누락 시 fail-fast 한다")
	void failsFastWhenPulledProjectIdMissing() {
		IngestEventsCommand request = new IngestEventsCommand(
			" ",
			"loki",
			"batch-1",
			List.of()
		);

		assertThatThrownBy(() -> ingestService.ingestPulledEvents(request))
			.isInstanceOf(ValidationException.class)
			.hasMessage("project_id is required for pulled ingest");
		verify(idempotencyStore, never()).computeIfAbsent(any(), any());
	}

	@Test
	@DisplayName("IngestService는 pull ingest에서 batch_id 누락 시 fail-fast 한다")
	void failsFastWhenPulledBatchIdMissing() {
		IngestEventsCommand request = new IngestEventsCommand(
			"project-1",
			"loki",
			" ",
			List.of()
		);

		assertThatThrownBy(() -> ingestService.ingestPulledEvents(request))
			.isInstanceOf(ValidationException.class)
			.hasMessage("batch_id is required for pulled ingest");
		verify(idempotencyStore, never()).computeIfAbsent(any(), any());
	}

	@Test
	@DisplayName("IngestService는 pull 이벤트를 push와 동일 canonical pipeline으로 처리한다")
	void ingestsPulledEventsThroughSameCanonicalPipeline() {
		IngestEventsCommand request = validRequest();
		when(idempotencyStore.computeIfAbsent(eq("pull:project-1:batch-1"), any())).thenAnswer(invocation -> {
			Function<String, IngestAcceptedResult> mapper = invocation.getArgument(1);
			return mapper.apply("pull:project-1:batch-1");
		});
		when(projectService.existsById("project-1")).thenReturn(true);
		doNothing().when(ingestRequestValidator).validate(request, true);
		when(eventDeduplicationPolicy.countDeduplicatedEvents(request.events())).thenReturn(1);

		IngestAcceptedResult result = ingestService.ingestPulledEvents(request);

		assertThat(result.accepted()).isTrue();
		assertThat(result.receivedEvents()).isEqualTo(2);
		assertThat(result.deduplicatedEvents()).isEqualTo(1);
		assertThat(result.ingestionId()).isNotBlank();
		verify(idempotencyStore).computeIfAbsent(eq("pull:project-1:batch-1"), any());
		verify(projectService).existsById("project-1");
		verify(ingestRequestValidator).validate(request, true);
		verify(eventDeduplicationPolicy).countDeduplicatedEvents(request.events());
		verify(incidentService).recordIngestedEvents("project-1", request.events());
	}

	@Test
	@DisplayName("IngestService는 동일 pull batch를 재처리할 때 idempotency로 중복 incident 생성을 방지한다")
	void avoidsDuplicateIncidentCreationForSamePullBatch() {
		IngestEventsCommand request = validRequest();
		Map<String, IngestAcceptedResult> cachedByKey = new HashMap<>();
		when(idempotencyStore.computeIfAbsent(eq("pull:project-1:batch-1"), any())).thenAnswer(invocation -> {
			String key = invocation.getArgument(0);
			Function<String, IngestAcceptedResult> mapper = invocation.getArgument(1);
			return cachedByKey.computeIfAbsent(key, mapper);
		});
		when(projectService.existsById("project-1")).thenReturn(true);
		doNothing().when(ingestRequestValidator).validate(request, true);
		when(eventDeduplicationPolicy.countDeduplicatedEvents(request.events())).thenReturn(1);

		IngestAcceptedResult first = ingestService.ingestPulledEvents(request);
		IngestAcceptedResult second = ingestService.ingestPulledEvents(request);

		assertThat(second.ingestionId()).isEqualTo(first.ingestionId());
		verify(incidentService, times(1)).recordIngestedEvents("project-1", request.events());
	}

	private IngestEventsCommand validRequest() {
		return new IngestEventsCommand(
			"project-1",
			"loki",
			"batch-1",
			List.of(
				new CanonicalLogEvent("event-1", "2026-03-03T03:00:00Z", "api", "error", "boom", null, null, null, Map.of()),
				new CanonicalLogEvent("event-1", "2026-03-03T03:00:01Z", "api", "error", "dup", null, null, null, Map.of())
			)
		);
	}
}
