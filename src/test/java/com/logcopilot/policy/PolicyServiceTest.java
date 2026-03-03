package com.logcopilot.policy;

import com.logcopilot.common.error.BadRequestException;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyServiceTest {

	private final ProjectService projectService = new ProjectService();
	private final PolicyService policyService = new PolicyService(projectService);

	@Test
	@DisplayName("PolicyService는 export policy를 프로젝트별로 갱신한다")
	void updateExportPolicyUpdatesPerProject() {
		ProjectDto project = projectService.create("policy-export", "prod");

		PolicyService.ExportPolicyResult result = policyService.updateExportPolicy(
			project.id(),
			new PolicyService.ExportPolicyCommand("level2_byom_with_telemetry")
		);

		assertThat(result.level()).isEqualTo("level2_byom_with_telemetry");
		assertThat(result.updatedAt()).isNotNull();
	}

	@Test
	@DisplayName("PolicyService는 redaction policy를 갱신하고 rules_count를 반환한다")
	void updateRedactionPolicyReturnsRulesCount() {
		ProjectDto project = projectService.create("policy-redaction", "prod");

		PolicyService.RedactionPolicyResult result = policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(
				true,
				List.of(
					new PolicyService.RedactionRuleCommand("email", "[^\\s]+@[^\\s]+", "[REDACTED_EMAIL]"),
					new PolicyService.RedactionRuleCommand("token", "token=[^\\s]+", "token=[REDACTED]")
				)
			)
		);

		assertThat(result.enabled()).isTrue();
		assertThat(result.rulesCount()).isEqualTo(2);
		assertThat(result.updatedAt()).isNotNull();
	}

	@Test
	@DisplayName("PolicyService는 존재하지 않는 프로젝트 요청이면 BadRequestException을 던진다")
	void updatePolicyThrowsWhenProjectMissing() {
		assertThatThrownBy(() -> policyService.updateExportPolicy(
			"missing-project",
			new PolicyService.ExportPolicyCommand("level1_byom_only")
		))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("Project not found");
	}

	@Test
	@DisplayName("PolicyService는 중첩 수량자 정규식을 차단한다")
	void updateRedactionPolicyRejectsNestedQuantifierPattern() {
		ProjectDto project = projectService.create("policy-redaction-guard", "prod");

		assertThatThrownBy(() -> policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(
				true,
				List.of(new PolicyService.RedactionRuleCommand("redos", "(a+)+", "[MASKED]"))
			)
		))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("rules[0].pattern contains disallowed nested quantifier");
	}
}
