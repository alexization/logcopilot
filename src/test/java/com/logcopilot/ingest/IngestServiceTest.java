package com.logcopilot.ingest;

import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.ingest.domain.EventDeduplicationPolicy;
import com.logcopilot.ingest.domain.IngestAcceptedResult;
import com.logcopilot.ingest.domain.IngestEventsCommand;
import com.logcopilot.ingest.domain.IngestRequestValidator;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
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

	@InjectMocks
	private IngestService ingestService;

	@Test
	@DisplayName("IngestService는 같은 idempotency key가 있으면 캐시 결과를 반환한다")
	void returnsCachedAcceptedResultWhenIdempotencyKeyExists() {
		IngestEventsCommand request = validRequest();
		IngestAcceptedResult cached = new IngestAcceptedResult(true, "ing-1", 2, 1);
		when(idempotencyStore.find("key-1")).thenReturn(Optional.of(cached));

		IngestAcceptedResult result = ingestService.ingestEvents("key-1", request);

		assertThat(result).isEqualTo(cached);
		verify(projectService, never()).existsById(any());
		verify(ingestRequestValidator, never()).validate(any(), eq(true));
	}

	@Test
	@DisplayName("IngestService는 신규 idempotency key면 검증 후 결과를 저장한다")
	void validatesAndSavesAcceptedResultOnFirstRequest() {
		IngestEventsCommand request = validRequest();
		when(idempotencyStore.find("key-1")).thenReturn(Optional.empty());
		when(projectService.existsById("project-1")).thenReturn(true);
		doNothing().when(ingestRequestValidator).validate(request, true);
		when(eventDeduplicationPolicy.countDeduplicatedEvents(request.events())).thenReturn(1);

		IngestAcceptedResult result = ingestService.ingestEvents("key-1", request);

		assertThat(result.accepted()).isTrue();
		assertThat(result.receivedEvents()).isEqualTo(2);
		assertThat(result.deduplicatedEvents()).isEqualTo(1);
		assertThat(result.ingestionId()).isNotBlank();
		verify(idempotencyStore).save(eq("key-1"), eq(result));
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
