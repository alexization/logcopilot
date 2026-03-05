package com.logcopilot.bootstrap;

import com.logcopilot.common.auth.TokenLifecycleService;
import com.logcopilot.common.error.ConflictException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.common.persistence.StateSnapshotRepository;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BootstrapService {

	private static final String SNAPSHOT_SCOPE = "bootstrap-service";

	private final ProjectService projectService;
	private final TokenLifecycleService tokenLifecycleService;
	private final StateSnapshotRepository stateSnapshotRepository;

	private boolean bootstrapped;
	private String initializedAt;

	public BootstrapService(
		ProjectService projectService,
		TokenLifecycleService tokenLifecycleService
	) {
		this(projectService, tokenLifecycleService, (StateSnapshotRepository) null);
	}

	@Autowired
	public BootstrapService(
		ProjectService projectService,
		TokenLifecycleService tokenLifecycleService,
		ObjectProvider<StateSnapshotRepository> stateSnapshotRepositoryProvider
	) {
		this(projectService, tokenLifecycleService, stateSnapshotRepositoryProvider.getIfAvailable());
	}

	BootstrapService(
		ProjectService projectService,
		TokenLifecycleService tokenLifecycleService,
		StateSnapshotRepository stateSnapshotRepository
	) {
		this.projectService = projectService;
		this.tokenLifecycleService = tokenLifecycleService;
		this.stateSnapshotRepository = stateSnapshotRepository;
		restoreState();
	}

	public synchronized BootstrapStatus status() {
		return new BootstrapStatus(bootstrapped, initializedAt);
	}

	public synchronized BootstrapInitialized initialize(InitializeCommand command) {
		if (command == null) {
			throw new ValidationException("Request body must not be null");
		}
		if (bootstrapped) {
			throw new ConflictException("Bootstrap already completed");
		}

		ProjectDto project = null;
		TokenLifecycleService.IssuedToken operator = null;
		TokenLifecycleService.IssuedToken ingest = null;
		try {
			project = projectService.create(command.projectName(), command.environment());
			operator = tokenLifecycleService.issueToken(
				new TokenLifecycleService.IssueCommand(command.operatorTokenName(), "operator")
			);
			ingest = tokenLifecycleService.issueToken(
				new TokenLifecycleService.IssueCommand(command.ingestTokenName(), "ingest")
			);
		} catch (RuntimeException exception) {
			rollbackPartialInitialization(project, operator, ingest, exception);
			throw exception;
		}

		bootstrapped = true;
		initializedAt = Instant.now().toString();
		persistState();
		return new BootstrapInitialized(
			bootstrapped,
			initializedAt,
			project,
			operator.tokenInfo(),
			operator.plainToken(),
			ingest.tokenInfo(),
			ingest.plainToken()
		);
	}

	private void rollbackPartialInitialization(
		ProjectDto project,
		TokenLifecycleService.IssuedToken operator,
		TokenLifecycleService.IssuedToken ingest,
		RuntimeException rootCause
	) {
		revokeIssuedToken(ingest, rootCause);
		revokeIssuedToken(operator, rootCause);
		if (project != null) {
			try {
				projectService.delete(project.id());
			} catch (RuntimeException cleanupException) {
				rootCause.addSuppressed(cleanupException);
			}
		}
	}

	private void revokeIssuedToken(TokenLifecycleService.IssuedToken issuedToken, RuntimeException rootCause) {
		if (issuedToken == null || issuedToken.tokenInfo() == null) {
			return;
		}
		try {
			tokenLifecycleService.forceRevokeToken(issuedToken.tokenInfo().id(), "bootstrap-initialize-rollback");
		} catch (RuntimeException cleanupException) {
			rootCause.addSuppressed(cleanupException);
		}
	}

	private void restoreState() {
		if (stateSnapshotRepository != null) {
			stateSnapshotRepository.load(SNAPSHOT_SCOPE, BootstrapSnapshot.class)
				.ifPresent(snapshot -> {
					bootstrapped = snapshot.bootstrapped();
					initializedAt = snapshot.initializedAt();
				});
		}
		if (!bootstrapped && !tokenLifecycleService.listTokens().isEmpty()) {
			bootstrapped = true;
		}
	}

	private void persistState() {
		if (stateSnapshotRepository == null) {
			return;
		}
		stateSnapshotRepository.save(SNAPSHOT_SCOPE, new BootstrapSnapshot(bootstrapped, initializedAt));
	}

	record BootstrapSnapshot(
		boolean bootstrapped,
		String initializedAt
	) {
	}

	public record InitializeCommand(
		String projectName,
		String environment,
		String operatorTokenName,
		String ingestTokenName
	) {
	}

	public record BootstrapStatus(
		boolean bootstrapped,
		String initializedAt
	) {
	}

	public record BootstrapInitialized(
		boolean bootstrapped,
		String initializedAt,
		ProjectDto project,
		TokenLifecycleService.TokenInfo operatorTokenInfo,
		String operatorTokenValue,
		TokenLifecycleService.TokenInfo ingestTokenInfo,
		String ingestTokenValue
	) {
	}
}
