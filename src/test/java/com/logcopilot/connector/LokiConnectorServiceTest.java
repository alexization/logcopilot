package com.logcopilot.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.logcopilot.common.persistence.StateSnapshotRepository;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LokiConnectorServiceTest {

	private final ProjectService projectService = new ProjectService();

	@Test
	void rollsBackCreateWhenPersistenceFails() {
		LokiConnectorService service = new LokiConnectorService(projectService, new FlakySnapshotRepository(1));
		String projectId = createProjectId();

		assertThatThrownBy(() -> service.upsert(projectId, sampleRequest("{service=\"api\"}")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("forced persistence failure");

		assertThat(service.findByProjectId(projectId)).isEmpty();
	}

	@Test
	void rollsBackUpdateWhenPersistenceFails() {
		LokiConnectorService service = new LokiConnectorService(projectService, new FlakySnapshotRepository(2));
		String projectId = createProjectId();

		LokiConnectorService.UpsertResult created = service.upsert(projectId, sampleRequest("{service=\"api\"}"));
		assertThat(created.created()).isTrue();

		assertThatThrownBy(() -> service.upsert(projectId, sampleRequest("{service=\"worker\"}")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("forced persistence failure");

		LokiConnectorService.LokiConnector restored = service.findByProjectId(projectId).orElseThrow();
		assertThat(restored.query()).isEqualTo("{service=\"api\"}");
	}

	private String createProjectId() {
		return projectService.create("loki-service-test-" + UUID.randomUUID(), "prod").id();
	}

	private LokiConnectorService.LokiConnectorRequest sampleRequest(String query) {
		return new LokiConnectorService.LokiConnectorRequest(
			"https://loki.example.com",
			"tenant-a",
			new LokiConnectorService.AuthRequest("none", null, null, null),
			query,
			30
		);
	}

	private static final class FlakySnapshotRepository implements StateSnapshotRepository {

		private final int failOnSaveCount;
		private final Map<String, Object> snapshots = new HashMap<>();
		private int saveCount;

		private FlakySnapshotRepository(int failOnSaveCount) {
			this.failOnSaveCount = failOnSaveCount;
		}

		@Override
		public void save(String scope, Object snapshot) {
			saveCount++;
			if (saveCount == failOnSaveCount) {
				throw new IllegalStateException("forced persistence failure");
			}
			snapshots.put(scope, snapshot);
		}

		@Override
		public <T> Optional<T> load(String scope, Class<T> type) {
			return Optional.ofNullable(type.cast(snapshots.get(scope)));
		}

		@Override
		public <T> Optional<T> load(String scope, TypeReference<T> typeReference) {
			@SuppressWarnings("unchecked")
			T snapshot = (T) snapshots.get(scope);
			return Optional.ofNullable(snapshot);
		}
	}
}
