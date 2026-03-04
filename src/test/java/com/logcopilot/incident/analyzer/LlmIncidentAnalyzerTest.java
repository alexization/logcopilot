package com.logcopilot.incident.analyzer;

import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import com.logcopilot.llm.LlmAccountService;
import com.logcopilot.llm.LlmOAuthProperties;
import com.logcopilot.policy.PolicyService;
import com.logcopilot.project.ProjectDto;
import com.logcopilot.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmIncidentAnalyzerTest {

	private final ProjectService projectService = new ProjectService();
	private final PolicyService policyService = new PolicyService(projectService);
	private final LlmAccountService llmAccountService = new LlmAccountService(
		projectService,
		LlmOAuthProperties.defaultProperties()
	);
	private final LlmIncidentAnalyzer analyzer = new LlmIncidentAnalyzer(llmAccountService, policyService);

	@Test
	@DisplayName("LlmIncidentAnalyzer는 LLM 분석 전에 rule 기반 텍스트를 redaction 적용한다")
	void analyzeRedactsRuleTextBeforeReturningLlmReport() {
		ProjectDto project = projectService.create("llm-redaction", "prod");
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
		llmAccountService.upsertApiKey(
			project.id(),
			new LlmAccountService.ApiKeyUpsertCommand(
				"openai",
				"main",
				"sk-openai-1",
				"gpt-4o-mini",
				null
			)
		);

		AnalysisReport ruleReport = sensitiveRuleReport();
		AnalysisReport result = analyzer.analyze(
			new IncidentReanalyzeCommand(
				"incident-1",
				project.id(),
				"api",
				"follow-up token=abc",
				ruleReport
			),
			ruleReport
		);

		String joinedEvidence = String.join(" ", result.hypotheses().get(0).evidence());
		assertThat(joinedEvidence)
			.contains("token=[REDACTED]")
			.contains("password=[REDACTED]")
			.contains("secret=[REDACTED]")
			.doesNotContain("token=abc")
			.doesNotContain("password=pw-1")
			.doesNotContain("secret=sec-1");
		assertThat(String.join(" ", result.nextActions()))
			.doesNotContain("password=pw-1")
			.contains("password=[REDACTED]");
	}

	@Test
	@DisplayName("LlmIncidentAnalyzer는 redaction policy가 없으면 LLM 분석을 거부한다")
	void analyzeRejectsWhenRedactionPolicyMissing() {
		ProjectDto project = projectService.create("llm-redaction-missing", "prod");
		llmAccountService.upsertApiKey(
			project.id(),
			new LlmAccountService.ApiKeyUpsertCommand(
				"openai",
				"main",
				"sk-openai-1",
				"gpt-4o-mini",
				null
			)
		);

		AnalysisReport ruleReport = sensitiveRuleReport();
		IncidentReanalyzeCommand command = new IncidentReanalyzeCommand(
			"incident-1",
			project.id(),
			"api",
			"follow-up token=abc",
			ruleReport
		);

		assertThatThrownBy(() -> analyzer.analyze(command, ruleReport))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Redaction policy is not configured");
	}

	private AnalysisReport sensitiveRuleReport() {
		return new AnalysisReport(
			"summary token=abc",
			List.of(new Hypothesis(
				"cause password=pw-1",
				0.72,
				List.of(
					"token=abc",
					"password=pw-1",
					"secret=sec-1"
				)
			)),
			List.of("Rotate password=pw-1"),
			List.of("rule baseline")
		);
	}
}
