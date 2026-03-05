package com.logcopilot.bootstrap;

import com.logcopilot.common.persistence.StateSnapshotRepository;
import com.logcopilot.common.auth.TokenLifecycleService;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BootstrapServiceTest {

	@Test
	@DisplayName("복원 시 활성 토큰이 없으면 bootstrapped=false를 유지한다")
	void restoreStateKeepsUnbootstrappedWhenOnlyRevokedTokensExist() {
		ProjectService projectService = mock(ProjectService.class);
		TokenLifecycleService tokenLifecycleService = mock(TokenLifecycleService.class);
		when(tokenLifecycleService.listTokens()).thenReturn(List.of(
			new TokenLifecycleService.TokenInfo(
				"token-1",
				"legacy",
				"operator",
				"revoked",
				"2026-03-05T00:00:00Z",
				null,
				"2026-03-05T00:01:00Z",
				"rollback"
			)
		));

		BootstrapService service = new BootstrapService(
			projectService,
			tokenLifecycleService,
			(StateSnapshotRepository) null
		);

		assertThat(service.status().bootstrapped()).isFalse();
	}

	@Test
	@DisplayName("복원 시 활성 토큰이 있으면 bootstrapped=true로 복구한다")
	void restoreStateMarksBootstrappedWhenActiveTokenExists() {
		ProjectService projectService = mock(ProjectService.class);
		TokenLifecycleService tokenLifecycleService = mock(TokenLifecycleService.class);
		when(tokenLifecycleService.listTokens()).thenReturn(List.of(
			new TokenLifecycleService.TokenInfo(
				"token-1",
				"legacy",
				"operator",
				"active",
				"2026-03-05T00:00:00Z",
				null,
				null,
				null
			)
		));

		BootstrapService service = new BootstrapService(
			projectService,
			tokenLifecycleService,
			(StateSnapshotRepository) null
		);

		assertThat(service.status().bootstrapped()).isTrue();
	}
}
