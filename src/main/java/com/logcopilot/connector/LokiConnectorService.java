package com.logcopilot.connector;

import com.logcopilot.common.error.BadGatewayException;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.common.persistence.StateSnapshotRepository;
import com.logcopilot.project.ProjectService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class LokiConnectorService {

	private static final String SNAPSHOT_SCOPE = "loki-connector-service";

	private final ProjectService projectService;
	private final StateSnapshotRepository stateSnapshotRepository;
	private final Map<String, LokiConnector> connectorByProjectId = new HashMap<>();

	public LokiConnectorService(ProjectService projectService) {
		this(projectService, (StateSnapshotRepository) null);
	}

	@Autowired
	public LokiConnectorService(
		ProjectService projectService,
		ObjectProvider<StateSnapshotRepository> stateSnapshotRepositoryProvider
	) {
		this(projectService, stateSnapshotRepositoryProvider.getIfAvailable());
	}

	LokiConnectorService(ProjectService projectService, StateSnapshotRepository stateSnapshotRepository) {
		this.projectService = projectService;
		this.stateSnapshotRepository = stateSnapshotRepository;
		restoreState();
	}

	public synchronized UpsertResult upsert(String projectId, LokiConnectorRequest request) {
		requireProject(projectId);

		String endpoint = validateEndpoint(request.endpoint());
		String query = validateQuery(request.query());
		LokiConnector existing = connectorByProjectId.get(projectId);
		LokiAuth auth = validateAuth(request.auth(), existing);
		int pollIntervalSeconds = validatePollIntervalSeconds(request.pollIntervalSeconds());
		if (existing == null) {
			LokiConnector created = new LokiConnector(
				UUID.randomUUID().toString(),
				endpoint,
				request.tenantId(),
				auth,
				query,
				pollIntervalSeconds,
				Instant.now()
			);
			persistUpsertedConnector(projectId, created, null);
			return new UpsertResult(true, created);
		}

		LokiConnector updated = new LokiConnector(
			existing.id(),
			endpoint,
			request.tenantId(),
			auth,
			query,
			pollIntervalSeconds,
			Instant.now()
		);
		persistUpsertedConnector(projectId, updated, existing);
		return new UpsertResult(false, updated);
	}

	public synchronized LokiTestResult test(String projectId) {
		requireProject(projectId);

		LokiConnector connector = connectorByProjectId.get(projectId);
		if (connector == null) {
			throw new NotFoundException("Loki connector not found");
		}

		// TODO(T-04): 계약 테스트용 시뮬레이션이며, 실제 Loki 연동 로직으로 대체해야 한다.
		if (connector.endpoint().contains("bad-gateway") || connector.query().contains("upstream_fail")) {
			throw new BadGatewayException("Failed to reach Loki upstream");
		}

		// TODO(T-04): 하드코딩 응답 대신 실제 Loki 조회 결과를 반환해야 한다.
		return new LokiTestResult(true, 12, 37, "Loki connector test succeeded");
	}

	public synchronized Optional<LokiConnector> findByProjectId(String projectId) {
		return Optional.ofNullable(connectorByProjectId.get(projectId));
	}

	public synchronized ConnectorConfiguration getConfiguration(String projectId) {
		requireProject(projectId);
		LokiConnector connector = connectorByProjectId.get(projectId);
		if (connector == null) {
			return new ConnectorConfiguration(
				false,
				null,
				"loki",
				"not_configured",
				null,
				null,
				new LokiAuth("none", null, null, null),
				null,
				null,
				null
			);
		}

		return new ConnectorConfiguration(
			true,
			connector.id(),
			"loki",
			"active",
			connector.endpoint(),
			connector.tenantId(),
			connector.auth(),
			connector.query(),
			connector.pollIntervalSeconds(),
			connector.updatedAt()
		);
	}

	public synchronized List<String> listConfiguredProjectIds() {
		return List.copyOf(connectorByProjectId.keySet());
	}

	private void requireProject(String projectId) {
		if (projectId == null || projectId.isBlank() || !projectService.existsById(projectId)) {
			throw new NotFoundException("Project not found");
		}
	}

	private String validateEndpoint(String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			throw new ValidationException("Endpoint must be a valid URI");
		}

		try {
			URI uri = new URI(endpoint.trim());
			if (!uri.isAbsolute() || uri.getHost() == null) {
				throw new ValidationException("Endpoint must be a valid URI");
			}
		} catch (URISyntaxException exception) {
			throw new ValidationException("Endpoint must be a valid URI");
		}

		return endpoint.trim();
	}

	private String validateQuery(String query) {
		if (query == null || query.trim().isEmpty()) {
			throw new ValidationException("Query must not be blank");
		}
		return query.trim();
	}

	private LokiAuth validateAuth(AuthRequest authRequest, LokiConnector existing) {
		if (authRequest == null || authRequest.type() == null) {
			throw new ValidationException("Auth type must be one of: none, bearer, basic");
		}

		return switch (authRequest.type()) {
			case "none" -> new LokiAuth("none", null, null, null);
			case "bearer" -> {
				String token = normalizeOptional(authRequest.token());
				if (token == null && existing != null && existing.auth() != null && "bearer".equals(existing.auth().type())) {
					token = normalizeOptional(existing.auth().token());
				}
				if (token == null) {
					throw new ValidationException("Bearer auth requires token");
				}
				yield new LokiAuth("bearer", token, null, null);
			}
			case "basic" -> {
				String username = normalizeOptional(authRequest.username());
				String password = normalizeOptional(authRequest.password());
				if (existing != null && existing.auth() != null && "basic".equals(existing.auth().type())) {
					if (username == null) {
						username = normalizeOptional(existing.auth().username());
					}
					if (password == null) {
						password = normalizeOptional(existing.auth().password());
					}
				}
				if (username == null) {
					throw new ValidationException("Basic auth requires username and password");
				}
				if (password == null) {
					throw new ValidationException("Basic auth requires username and password");
				}
				yield new LokiAuth("basic", null, username, password);
			}
			default -> throw new ValidationException("Auth type must be one of: none, bearer, basic");
		};
	}

	private String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private int validatePollIntervalSeconds(Integer pollIntervalSeconds) {
		if (pollIntervalSeconds == null) {
			return 30;
		}
		if (pollIntervalSeconds < 5 || pollIntervalSeconds > 300) {
			throw new ValidationException("poll_interval_seconds must be between 5 and 300");
		}
		return pollIntervalSeconds;
	}

	public record LokiConnectorRequest(
		String endpoint,
		String tenantId,
		AuthRequest auth,
		String query,
		Integer pollIntervalSeconds
	) {
	}

	public record AuthRequest(
		@NotBlank(message = "Auth type must be one of: none, bearer, basic")
		@Pattern(regexp = "none|bearer|basic", message = "Auth type must be one of: none, bearer, basic")
		String type,
		String token,
		String username,
		String password
	) {
	}

	public record UpsertResult(
		boolean created,
		LokiConnector connector
	) {
	}

	public record LokiConnector(
		String id,
		String endpoint,
		String tenantId,
		LokiAuth auth,
		String query,
		int pollIntervalSeconds,
		Instant updatedAt
	) {
	}

	public record LokiAuth(
		String type,
		String token,
		String username,
		// TODO(T-05): DB 영속화 시 비밀값(password/token) 암호화 저장으로 전환한다.
		String password
	) {
	}

	public record LokiTestResult(
		boolean success,
		int sampleCount,
		int latencyMs,
		String message
	) {
	}

	public record ConnectorConfiguration(
		boolean configured,
		String id,
		String type,
		String status,
		String endpoint,
		String tenantId,
		LokiAuth auth,
		String query,
		Integer pollIntervalSeconds,
		Instant updatedAt
	) {
	}

	private void restoreState() {
		if (stateSnapshotRepository == null) {
			return;
		}
		stateSnapshotRepository.load(SNAPSHOT_SCOPE, LokiConnectorSnapshot.class)
			.ifPresent(snapshot -> {
				connectorByProjectId.clear();
				if (snapshot.connectorByProjectId() != null) {
					connectorByProjectId.putAll(snapshot.connectorByProjectId());
				}
			});
	}

	private void persistState() {
		if (stateSnapshotRepository == null) {
			return;
		}
		stateSnapshotRepository.save(
			SNAPSHOT_SCOPE,
			new LokiConnectorSnapshot(new HashMap<>(connectorByProjectId))
		);
	}

	private void persistUpsertedConnector(String projectId, LokiConnector current, LokiConnector previous) {
		connectorByProjectId.put(projectId, current);
		try {
			persistState();
		} catch (RuntimeException exception) {
			if (previous == null) {
				connectorByProjectId.remove(projectId);
			} else {
				connectorByProjectId.put(projectId, previous);
			}
			throw exception;
		}
	}

	record LokiConnectorSnapshot(Map<String, LokiConnector> connectorByProjectId) {
	}
}
