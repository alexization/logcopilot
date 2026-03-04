package com.logcopilot.llm;

import com.logcopilot.common.error.BadRequestException;
import com.logcopilot.common.error.ConflictException;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmAccountServiceTest {

	private final ProjectService projectService = new ProjectService();
	private final LlmOAuthProperties oauthProperties = LlmOAuthProperties.defaultProperties();
	private final MutableClock clock = new MutableClock(Instant.parse("2026-03-04T00:00:00Z"));
	private final LlmAccountService llmAccountService = new LlmAccountService(projectService, oauthProperties, clock);

	@Test
	@DisplayName("LlmAccountService는 API key 계정을 생성 후 같은 provider 요청을 갱신한다")
	void upsertApiKeyCreatesThenUpdatesSameAccount() {
		ProjectDto project = projectService.create("llm-project", "prod");

		LlmAccountService.UpsertResult created = llmAccountService.upsertApiKey(
			project.id(),
			new LlmAccountService.ApiKeyUpsertCommand(
				"openai",
				"main",
				"sk-1",
				"gpt-4o-mini",
				null
			)
		);

		LlmAccountService.UpsertResult updated = llmAccountService.upsertApiKey(
			project.id(),
			new LlmAccountService.ApiKeyUpsertCommand(
				"openai",
				"main-updated",
				"sk-2",
				"gpt-4.1-mini",
				"https://api.openai.com/v1"
			)
		);

		assertThat(created.created()).isTrue();
		assertThat(updated.created()).isFalse();
		assertThat(updated.account().id()).isEqualTo(created.account().id());
		assertThat(updated.account().model()).isEqualTo("gpt-4.1-mini");
	}

	@Test
	@DisplayName("LlmAccountService는 OAuth start/callback 성공 시 oauth 계정을 연결한다")
	void startAndCallbackOAuthLinksAccount() {
		ProjectDto project = projectService.create("oauth-project", "prod");

		LlmAccountService.OAuthStartResult start = llmAccountService.startOAuth(project.id(), "gemini");
		LlmAccountService.OAuthCallbackResult callback = llmAccountService.callbackOAuth(
			project.id(),
			"gemini",
			"oauth-code-1",
			start.state(),
			null,
			null
		);

		List<LlmAccountService.LlmAccount> accounts = llmAccountService.list(project.id());
		assertThat(start.authUrl()).contains("gemini");
		assertThat(callback.linked()).isTrue();
		assertThat(callback.accountId()).isNotBlank();
		assertThat(accounts).hasSize(1);
		assertThat(accounts.get(0).authType()).isEqualTo("oauth");
	}

	@Test
	@DisplayName("LlmAccountService는 OAuth state가 유효하지 않으면 ConflictException을 던진다")
	void callbackOAuthThrowsConflictForInvalidState() {
		ProjectDto project = projectService.create("oauth-conflict-project", "prod");
		llmAccountService.startOAuth(project.id(), "openai");

		assertThatThrownBy(() -> llmAccountService.callbackOAuth(
			project.id(),
			"openai",
			"oauth-code-1",
			"wrong-state",
			null,
			null
		))
			.isInstanceOf(ConflictException.class)
			.hasMessage("Invalid or expired oauth state");
	}

	@Test
	@DisplayName("LlmAccountService는 사용된 OAuth state를 재사용하면 ConflictException을 던진다")
	void callbackOAuthRejectsReusedState() {
		ProjectDto project = projectService.create("oauth-reuse-project", "prod");
		LlmAccountService.OAuthStartResult start = llmAccountService.startOAuth(project.id(), "openai");

		llmAccountService.callbackOAuth(project.id(), "openai", "oauth-code-1", start.state(), null, null);

		assertThatThrownBy(() -> llmAccountService.callbackOAuth(
			project.id(),
			"openai",
			"oauth-code-2",
			start.state(),
			null,
			null
		))
			.isInstanceOf(ConflictException.class)
			.hasMessage("Invalid or expired oauth state");
	}

	@Test
	@DisplayName("LlmAccountService는 OAuth state TTL 경계에서 만료된 state를 거부한다")
	void callbackOAuthRejectsExpiredStateAtTtlBoundary() {
		ProjectDto project = projectService.create("oauth-ttl-project", "prod");
		LlmAccountService.OAuthStartResult start = llmAccountService.startOAuth(project.id(), "openai");
		clock.advance(oauthProperties.getStateTtl());

		assertThatThrownBy(() -> llmAccountService.callbackOAuth(
			project.id(),
			"openai",
			"oauth-code-1",
			start.state(),
			null,
			null
		))
			.isInstanceOf(ConflictException.class)
			.hasMessage("Invalid or expired oauth state");
	}

	@Test
	@DisplayName("LlmAccountService는 provider 오류 콜백을 400으로 매핑하고 state를 재사용 불가로 만든다")
	void callbackOAuthMapsProviderErrorAndConsumesState() {
		ProjectDto project = projectService.create("oauth-provider-error-project", "prod");
		LlmAccountService.OAuthStartResult start = llmAccountService.startOAuth(project.id(), "gemini");

		assertThatThrownBy(() -> llmAccountService.callbackOAuth(
			project.id(),
			"gemini",
			null,
			start.state(),
			"access_denied",
			"User denied"
		))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("OAuth provider returned error: access_denied (User denied)");

		assertThatThrownBy(() -> llmAccountService.callbackOAuth(
			project.id(),
			"gemini",
			"oauth-code-2",
			start.state(),
			null,
			null
		))
			.isInstanceOf(ConflictException.class)
			.hasMessage("Invalid or expired oauth state");
	}

	@Test
	@DisplayName("LlmAccountService는 OAuth start 시 provider별 프로퍼티로 auth URL을 구성한다")
	void startOAuthBuildsAuthUrlFromProviderProperties() {
		ProjectDto project = projectService.create("oauth-url-project", "prod");
		LlmAccountService.OAuthStartResult start = llmAccountService.startOAuth(project.id(), "openai");
		URI uri = URI.create(start.authUrl());
		Map<String, String> query = QueryStringParser.parse(uri.getQuery());

		assertThat(uri.getHost()).isEqualTo("stub.openai.example.com");
		assertThat(query.get("client_id")).isEqualTo("openai-local-client");
		assertThat(query.get("scope")).isEqualTo("openid profile");
		assertThat(query.get("redirect_uri"))
			.isEqualTo("http://localhost:8080/v1/projects/%s/llm-oauth/openai/callback".formatted(project.id()));
		assertThat(query.get("state")).isEqualTo(start.state());
	}

	@Test
	@DisplayName("LlmAccountService는 state 저장소 상한 초과 시 가장 오래된 state를 제거한다")
	void startOAuthEvictsOldestStateWhenCapacityExceeded() {
		LlmOAuthProperties localProperties = LlmOAuthProperties.defaultProperties();
		localProperties.setMaxStateEntries(2);
		LlmAccountService localService = new LlmAccountService(projectService, localProperties, clock);
		ProjectDto project = projectService.create("oauth-capacity-project", "prod");
		LlmAccountService.OAuthStartResult first = localService.startOAuth(project.id(), "openai");
		clock.advance(Duration.ofSeconds(1));
		LlmAccountService.OAuthStartResult second = localService.startOAuth(project.id(), "openai");
		clock.advance(Duration.ofSeconds(1));
		LlmAccountService.OAuthStartResult third = localService.startOAuth(project.id(), "openai");

		assertThatThrownBy(() -> localService.callbackOAuth(
			project.id(),
			"openai",
			"oauth-code-1",
			first.state(),
			null,
			null
		))
			.isInstanceOf(ConflictException.class)
			.hasMessage("Invalid or expired oauth state");

		LlmAccountService.OAuthCallbackResult callback = localService.callbackOAuth(
			project.id(),
			"openai",
			"oauth-code-2",
			third.state(),
			null,
			null
		);

		assertThat(callback.linked()).isTrue();
		assertThat(callback.accountId()).isNotBlank();
		assertThat(second.state()).isNotEqualTo(third.state());
	}

	@Test
	@DisplayName("LlmAccountService는 auth URL 생성 실패 시 OAuth state를 저장하지 않는다")
	void startOAuthDoesNotPersistStateWhenAuthorizationUrlBuildFails() {
		LlmOAuthProperties localProperties = LlmOAuthProperties.defaultProperties();
		localProperties.setMaxStateEntries(1);
		localProperties.getOpenai().setStubAuthorizationUri("");
		LlmAccountService localService = new LlmAccountService(projectService, localProperties, clock);
		ProjectDto project = projectService.create("oauth-start-failure-project", "prod");
		LlmAccountService.OAuthStartResult validState = localService.startOAuth(project.id(), "gemini");

		assertThatThrownBy(() -> localService.startOAuth(project.id(), "openai"))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("OAuth provider is not configured");

		LlmAccountService.OAuthCallbackResult callback = localService.callbackOAuth(
			project.id(),
			"gemini",
			"oauth-code-1",
			validState.state(),
			null,
			null
		);
		assertThat(callback.linked()).isTrue();
	}

	@Test
	@DisplayName("LlmAccountService는 존재하지 않는 프로젝트 조회 시 NotFoundException을 던진다")
	void listThrowsNotFoundWhenProjectMissing() {
		assertThatThrownBy(() -> llmAccountService.list("missing-project"))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Project not found");
	}

	@Test
	@DisplayName("LlmAccountService는 provider가 잘못되면 ValidationException 또는 BadRequestException을 던진다")
	void validatesProviderByFlow() {
		ProjectDto project = projectService.create("provider-project", "prod");

		assertThatThrownBy(() -> llmAccountService.upsertApiKey(
			project.id(),
			new LlmAccountService.ApiKeyUpsertCommand(
				"claude",
				"bad",
				"key",
				"model",
				null
			)
		))
			.isInstanceOf(ValidationException.class)
			.hasMessage("provider must be one of: openai, gemini");

		assertThatThrownBy(() -> llmAccountService.startOAuth(project.id(), "claude"))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("provider must be one of: openai, gemini");
	}

	@Test
	@DisplayName("LlmAccountService는 생성/연동 요청에서 프로젝트가 없으면 BadRequestException을 던진다")
	void createAndOAuthFlowsRequireExistingProject() {
		assertThatThrownBy(() -> llmAccountService.upsertApiKey(
			"missing-project",
			new LlmAccountService.ApiKeyUpsertCommand(
				"openai",
				"main",
				"sk-1",
				"gpt-4o-mini",
				null
			)
		))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("Project not found");

		assertThatThrownBy(() -> llmAccountService.startOAuth("missing-project", "openai"))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("Project not found");
	}

	@Test
	@DisplayName("LlmAccountService는 존재하는 account를 삭제하고 재삭제 시 NotFoundException을 던진다")
	void deleteRemovesAccountAndThrowsWhenMissing() {
		ProjectDto project = projectService.create("delete-project", "prod");
		LlmAccountService.UpsertResult created = llmAccountService.upsertApiKey(
			project.id(),
			new LlmAccountService.ApiKeyUpsertCommand(
				"gemini",
				"gemini-main",
				"gsk-1",
				"gemini-2.0-flash",
				null
			)
		);

		llmAccountService.delete(project.id(), created.account().id());
		assertThat(llmAccountService.list(project.id())).isEmpty();

		assertThatThrownBy(() -> llmAccountService.delete(project.id(), created.account().id()))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("LLM account not found");
	}

	@Test
	@DisplayName("LlmAccountService는 API key 요청의 base_url이 유효하지 않으면 ValidationException을 던진다")
	void upsertApiKeyThrowsWhenBaseUrlInvalid() {
		ProjectDto project = projectService.create("uri-project", "prod");

		assertThatThrownBy(() -> llmAccountService.upsertApiKey(
			project.id(),
			new LlmAccountService.ApiKeyUpsertCommand(
				"openai",
				"main",
				"sk-1",
				"gpt-4o-mini",
				"not-a-uri"
			)
		))
			.isInstanceOf(ValidationException.class)
			.hasMessage("base_url must be a valid URI");
	}

	@Test
	@DisplayName("LlmAccountService는 API key 요청의 base_url 스킴이 http/https가 아니면 ValidationException을 던진다")
	void upsertApiKeyThrowsWhenBaseUrlSchemeUnsupported() {
		ProjectDto project = projectService.create("uri-scheme-project", "prod");

		assertThatThrownBy(() -> llmAccountService.upsertApiKey(
			project.id(),
			new LlmAccountService.ApiKeyUpsertCommand(
				"openai",
				"main",
				"sk-1",
				"gpt-4o-mini",
				"ftp://api.openai.com/v1"
			)
		))
			.isInstanceOf(ValidationException.class)
			.hasMessage("base_url must be a valid URI");
	}

	private static final class QueryStringParser {

		private QueryStringParser() {
		}

		static Map<String, String> parse(String query) {
			return query == null || query.isBlank()
				? Map.of()
				: java.util.Arrays.stream(query.split("&"))
					.map(part -> part.split("=", 2))
					.collect(java.util.stream.Collectors.toMap(
						pair -> java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8),
						pair -> pair.length > 1
							? java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8)
							: "",
						(previous, current) -> current
					));
		}
	}

	private static final class MutableClock extends Clock {

		private Instant current;

		private MutableClock(Instant current) {
			this.current = current;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current;
		}

		void advance(Duration duration) {
			current = current.plus(duration);
		}
	}
}
