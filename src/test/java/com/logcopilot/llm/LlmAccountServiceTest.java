package com.logcopilot.llm;

import com.logcopilot.common.error.BadRequestException;
import com.logcopilot.common.error.ConflictException;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmAccountServiceTest {

	private final ProjectService projectService = new ProjectService();
	private final LlmAccountService llmAccountService = new LlmAccountService(projectService);

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
			start.state()
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
			"wrong-state"
		))
			.isInstanceOf(ConflictException.class)
			.hasMessage("Invalid or expired oauth state");
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
}
