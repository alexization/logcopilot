package com.logcopilot.connector;

import com.logcopilot.ingest.IngestService;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.ingest.domain.IngestAcceptedResult;
import com.logcopilot.ingest.domain.IngestEventsCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LokiPullCollectorTest {

	@Mock
	private LokiConnectorService lokiConnectorService;

	@Mock
	private LokiPullClient lokiPullClient;

	@Mock
	private LokiPullCursorStore lokiPullCursorStore;

	@Mock
	private IngestService ingestService;

	@InjectMocks
	private LokiPullCollector lokiPullCollector;

	@Test
	@DisplayName("LokiPullCollector는 pull 성공 시 canonical ingest 후 cursor를 commit한다")
	void commitsCursorAfterSuccessfulIngest() {
		LokiConnectorService.LokiConnector connector = connector();
		LokiPullClient.PullBatch pullBatch = new LokiPullClient.PullBatch(
			"batch-1",
			15L,
			List.of(event("evt-1"))
		);

		when(lokiConnectorService.findByProjectId("project-1")).thenReturn(Optional.of(connector));
		when(lokiPullCursorStore.readCursor("project-1")).thenReturn(10L);
		when(lokiPullClient.pull("project-1", connector, 10L)).thenReturn(pullBatch);
		when(ingestService.ingestPulledEvents(any(IngestEventsCommand.class)))
			.thenReturn(new IngestAcceptedResult(true, "ing-1", 1, 0));

		lokiPullCollector.collectProject("project-1");

		ArgumentCaptor<IngestEventsCommand> commandCaptor = ArgumentCaptor.forClass(IngestEventsCommand.class);
		verify(ingestService).ingestPulledEvents(commandCaptor.capture());
		verify(lokiPullCursorStore).commit("project-1", 15L);

		IngestEventsCommand forwarded = commandCaptor.getValue();
		assertThat(forwarded.projectId()).isEqualTo("project-1");
		assertThat(forwarded.source()).isEqualTo("loki");
		assertThat(forwarded.batchId()).isEqualTo("batch-1");
		assertThat(forwarded.events()).containsExactly(event("evt-1"));
	}

	@Test
	@DisplayName("LokiPullCollector는 ingest 실패 시 cursor를 commit하지 않는다")
	void doesNotCommitCursorWhenIngestFails() {
		LokiConnectorService.LokiConnector connector = connector();
		LokiPullClient.PullBatch pullBatch = new LokiPullClient.PullBatch(
			"batch-1",
			21L,
			List.of(event("evt-1"))
		);
		when(lokiConnectorService.findByProjectId("project-1")).thenReturn(Optional.of(connector));
		when(lokiPullCursorStore.readCursor("project-1")).thenReturn(20L);
		when(lokiPullClient.pull("project-1", connector, 20L)).thenReturn(pullBatch);
		doThrow(new RuntimeException("ingest failed"))
			.when(ingestService).ingestPulledEvents(any(IngestEventsCommand.class));

		lokiPullCollector.collectProject("project-1");

		verify(lokiPullCursorStore, never()).commit(eq("project-1"), anyLong());
	}

	@Test
	@DisplayName("LokiPullCollector는 ingest accepted=false면 cursor를 commit하지 않는다")
	void doesNotCommitCursorWhenIngestNotAccepted() {
		LokiConnectorService.LokiConnector connector = connector();
		LokiPullClient.PullBatch pullBatch = new LokiPullClient.PullBatch(
			"batch-1",
			21L,
			List.of(event("evt-1"))
		);
		when(lokiConnectorService.findByProjectId("project-1")).thenReturn(Optional.of(connector));
		when(lokiPullCursorStore.readCursor("project-1")).thenReturn(20L);
		when(lokiPullClient.pull("project-1", connector, 20L)).thenReturn(pullBatch);
		when(ingestService.ingestPulledEvents(any(IngestEventsCommand.class)))
			.thenReturn(new IngestAcceptedResult(false, "ing-1", 1, 0));

		lokiPullCollector.collectProject("project-1");

		verify(lokiPullCursorStore, never()).commit(eq("project-1"), anyLong());
	}

	@Test
	@DisplayName("LokiPullCollector는 실패 후 재시도에서 이전 cursor를 다시 사용해 pull한다")
	void retriesFromSameCursorAfterFailure() {
		LokiConnectorService.LokiConnector connector = connector();
		LokiPullClient.PullBatch pullBatch = new LokiPullClient.PullBatch(
			"batch-2",
			4L,
			List.of(event("evt-1"), event("evt-1"))
		);
		when(lokiConnectorService.findByProjectId("project-1")).thenReturn(Optional.of(connector));
		when(lokiPullCursorStore.readCursor("project-1")).thenReturn(0L);
		when(lokiPullClient.pull("project-1", connector, 0L)).thenReturn(pullBatch);
		when(ingestService.ingestPulledEvents(any(IngestEventsCommand.class)))
			.thenThrow(new RuntimeException("ingest failed"))
			.thenReturn(new IngestAcceptedResult(true, "ing-1", 2, 1));

		lokiPullCollector.collectProject("project-1");
		lokiPullCollector.collectProject("project-1");

		verify(lokiPullClient, times(2)).pull("project-1", connector, 0L);
		verify(lokiPullCursorStore).commit("project-1", 4L);
	}

	@Test
	@DisplayName("LokiPullCollector는 등록되지 않은 프로젝트면 pull을 수행하지 않는다")
	void skipsWhenConnectorIsMissing() {
		when(lokiConnectorService.findByProjectId("project-1")).thenReturn(Optional.empty());

		lokiPullCollector.collectProject("project-1");

		verify(lokiPullCursorStore, never()).readCursor(any());
		verify(lokiPullClient, never()).pull(any(), any(), anyLong());
		verify(ingestService, never()).ingestPulledEvents(any());
	}

	@Test
	@DisplayName("LokiPullCollector는 connector poll_interval_seconds 이내에는 재수집하지 않는다")
	void respectsConnectorPollInterval() {
		LokiConnectorService.LokiConnector connector = connector();
		LokiPullClient.PullBatch pullBatch = new LokiPullClient.PullBatch(
			"batch-3",
			30L,
			List.of(event("evt-3"))
		);
		when(lokiConnectorService.findByProjectId("project-1")).thenReturn(Optional.of(connector));
		when(lokiPullCursorStore.readCursor("project-1")).thenReturn(0L);
		when(lokiPullClient.pull("project-1", connector, 0L)).thenReturn(pullBatch);
		when(ingestService.ingestPulledEvents(any(IngestEventsCommand.class)))
			.thenReturn(new IngestAcceptedResult(true, "ing-3", 1, 0));

		lokiPullCollector.collectProject("project-1");
		lokiPullCollector.collectProject("project-1");

		verify(lokiPullClient, times(1)).pull("project-1", connector, 0L);
		verify(lokiPullCursorStore, times(1)).commit("project-1", 30L);
	}

	@Test
	@DisplayName("LokiPullCollector는 이벤트가 없는 성공 pull에서도 next cursor를 commit한다")
	void commitsCursorForEmptySuccessfulPullBatch() {
		LokiConnectorService.LokiConnector connector = connector();
		LokiPullClient.PullBatch pullBatch = new LokiPullClient.PullBatch(
			"batch-empty",
			25L,
			List.of()
		);
		when(lokiConnectorService.findByProjectId("project-1")).thenReturn(Optional.of(connector));
		when(lokiPullCursorStore.readCursor("project-1")).thenReturn(20L);
		when(lokiPullClient.pull("project-1", connector, 20L)).thenReturn(pullBatch);

		lokiPullCollector.collectProject("project-1");

		verify(ingestService, never()).ingestPulledEvents(any());
		verify(lokiPullCursorStore).commit("project-1", 25L);
	}

	private LokiConnectorService.LokiConnector connector() {
		return new LokiConnectorService.LokiConnector(
			"connector-1",
			"https://loki.example.com",
			"tenant-a",
			new LokiConnectorService.LokiAuth("none", null, null, null),
			"{service=\"api\"}",
			30,
			Instant.parse("2026-03-04T03:00:00Z")
		);
	}

	private CanonicalLogEvent event(String eventId) {
		return new CanonicalLogEvent(
			eventId,
			"2026-03-04T03:00:00Z",
			"api",
			"error",
			"boom",
			null,
			null,
			null,
			Map.of("cluster", "prod")
		);
	}
}
