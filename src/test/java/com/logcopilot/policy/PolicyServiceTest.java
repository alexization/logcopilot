package com.logcopilot.policy;

import com.logcopilot.common.error.BadRequestException;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

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
	@DisplayName("PolicyService는 redaction rule 200개를 허용한다")
	void updateRedactionPolicyAllowsMaxRules() {
		ProjectDto project = projectService.create("policy-redaction-max", "prod");
		List<PolicyService.RedactionRuleCommand> rules = IntStream.range(0, 200)
			.mapToObj(index -> new PolicyService.RedactionRuleCommand(
				"rule-" + index,
				"token-" + index,
				"[MASKED]"
			))
			.toList();

		PolicyService.RedactionPolicyResult result = policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(true, rules)
		);

		assertThat(result.enabled()).isTrue();
		assertThat(result.rulesCount()).isEqualTo(200);
		assertThat(result.updatedAt()).isNotNull();
	}

	@Test
	@DisplayName("PolicyService는 redaction rule 201개를 거부한다")
	void updateRedactionPolicyRejectsOverMaxRules() {
		ProjectDto project = projectService.create("policy-redaction-over-max", "prod");
		List<PolicyService.RedactionRuleCommand> rules = IntStream.range(0, 201)
			.mapToObj(index -> new PolicyService.RedactionRuleCommand(
				"rule-" + index,
				"token-" + index,
				"[MASKED]"
			))
			.toList();

		assertThatThrownBy(() -> policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(true, rules)
		))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("rules must contain at most 200 items");
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

		assertThatThrownBy(() -> policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(
				true,
				List.of(new PolicyService.RedactionRuleCommand("redos", "(a?)+", "[MASKED]"))
			)
		))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("rules[0].pattern contains disallowed nested quantifier");

		assertThatThrownBy(() -> policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(
				true,
				List.of(new PolicyService.RedactionRuleCommand("redos", "(a{1,3})+", "[MASKED]"))
			)
		))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("rules[0].pattern contains disallowed nested quantifier");

		assertThatThrownBy(() -> policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(
				true,
				List.of(new PolicyService.RedactionRuleCommand("redos", "(a+){2}", "[MASKED]"))
			)
		))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("rules[0].pattern contains disallowed nested quantifier");
	}

	@Test
	@DisplayName("PolicyService는 과도하게 긴 정규식 패턴을 차단한다")
	void updateRedactionPolicyRejectsOverlyLongPattern() {
		ProjectDto project = projectService.create("policy-redaction-pattern-length", "prod");
		String longPattern = "a".repeat(513);

		assertThatThrownBy(() -> policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(
				true,
				List.of(new PolicyService.RedactionRuleCommand("too-long", longPattern, "[MASKED]"))
			)
		))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("rules[0].pattern must be at most 512 characters");
	}

	@Test
	@DisplayName("PolicyService는 redaction policy 규칙으로 LLM 전송 텍스트를 마스킹한다")
	void redactForLlmAppliesPolicyRules() {
		ProjectDto project = projectService.create("policy-redact-llm", "prod");
		policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(
				true,
				List.of(
					new PolicyService.RedactionRuleCommand("token", "token=[^\\s]+", "token=[REDACTED]"),
					new PolicyService.RedactionRuleCommand("password", "password=[^\\s]+", "password=[REDACTED]"),
					new PolicyService.RedactionRuleCommand("secret", "secret=[^\\s]+", "secret=[REDACTED]")
				)
			)
		);

		String redacted = policyService.redactForLlm(
			project.id(),
			"token=abc password=pw-1 secret=sec-1"
		);

		assertThat(redacted)
			.isEqualTo("token=[REDACTED] password=[REDACTED] secret=[REDACTED]");
	}

	@Test
	@DisplayName("PolicyService는 redaction policy가 없으면 LLM 전송 텍스트 redaction을 거부한다")
	void redactForLlmThrowsWhenPolicyMissing() {
		ProjectDto project = projectService.create("policy-redact-llm-missing", "prod");

		assertThatThrownBy(() -> policyService.redactForLlm(project.id(), "token=abc"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Redaction policy is not configured");
	}

	@Test
	@DisplayName("PolicyService는 redaction policy가 비활성화되면 LLM 전송 텍스트 redaction을 거부한다")
	void redactForLlmThrowsWhenPolicyDisabled() {
		ProjectDto project = projectService.create("policy-redact-llm-disabled", "prod");
		policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(
				false,
				List.of(new PolicyService.RedactionRuleCommand("token", "token=[^\\s]+", "token=[REDACTED]"))
			)
		);

		assertThatThrownBy(() -> policyService.redactForLlm(project.id(), "token=abc"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Redaction policy is disabled");
	}

	@Test
	@DisplayName("PolicyService는 redaction policy에 활성 규칙이 없으면 LLM 전송 텍스트 redaction을 거부한다")
	void redactForLlmThrowsWhenNoActiveRules() {
		ProjectDto project = projectService.create("policy-redact-llm-no-rules", "prod");
		policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(true, List.of())
		);

		assertThatThrownBy(() -> policyService.redactForLlm(project.id(), "token=abc"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Redaction policy has no active rules");
	}

	@Test
	@DisplayName("PolicyService는 redaction 결과에 민감정보가 남아 있으면 LLM 전송 텍스트를 거부한다")
	void redactForLlmRejectsUnmaskedSensitiveValues() {
		ProjectDto project = projectService.create("policy-redact-llm-unmasked", "prod");
		policyService.updateRedactionPolicy(
			project.id(),
			new PolicyService.RedactionPolicyCommand(
				true,
				List.of(new PolicyService.RedactionRuleCommand("secret", "secret=[^\\s]+", "secret=[MASKED]"))
			)
		);

		assertThatThrownBy(() -> policyService.redactForLlm(project.id(), "token=abc"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Redaction did not mask all sensitive values");
	}
}
