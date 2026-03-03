package com.logcopilot.incident.analyzer;

import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultIncidentAnalyzerChainTest {

	@Test
	@DisplayName("Analyzer chain은 rule 분석 후 LLM이 가능하면 LLM 결과를 반환한다")
	void analyzeUsesLlmReportWhenAvailable() {
		RuleBasedIncidentAnalyzer ruleAnalyzer = mock(RuleBasedIncidentAnalyzer.class);
		LlmIncidentAnalyzer llmAnalyzer = mock(LlmIncidentAnalyzer.class);
		FallbackIncidentAnalyzer fallbackAnalyzer = mock(FallbackIncidentAnalyzer.class);
		DefaultIncidentAnalyzerChain chain = new DefaultIncidentAnalyzerChain(
			ruleAnalyzer,
			llmAnalyzer,
			fallbackAnalyzer
		);

		IncidentReanalyzeCommand command = command();
		AnalysisReport ruleReport = report("rule");
		AnalysisReport llmReport = report("llm");
		when(ruleAnalyzer.analyze(command)).thenReturn(ruleReport);
		when(llmAnalyzer.isAvailable("project-1")).thenReturn(true);
		when(llmAnalyzer.analyze(command, ruleReport)).thenReturn(llmReport);

		AnalysisReport result = chain.analyze(command);

		assertThat(result).isEqualTo(llmReport);
		InOrder order = inOrder(ruleAnalyzer, llmAnalyzer);
		order.verify(ruleAnalyzer).analyze(command);
		order.verify(llmAnalyzer).isAvailable("project-1");
		order.verify(llmAnalyzer).analyze(command, ruleReport);
		verifyNoInteractions(fallbackAnalyzer);
	}

	@Test
	@DisplayName("Analyzer chain은 LLM 계정이 없으면 fallback 리포트를 반환한다")
	void analyzeUsesFallbackWhenLlmUnavailable() {
		RuleBasedIncidentAnalyzer ruleAnalyzer = mock(RuleBasedIncidentAnalyzer.class);
		LlmIncidentAnalyzer llmAnalyzer = mock(LlmIncidentAnalyzer.class);
		FallbackIncidentAnalyzer fallbackAnalyzer = mock(FallbackIncidentAnalyzer.class);
		DefaultIncidentAnalyzerChain chain = new DefaultIncidentAnalyzerChain(
			ruleAnalyzer,
			llmAnalyzer,
			fallbackAnalyzer
		);

		IncidentReanalyzeCommand command = command();
		AnalysisReport ruleReport = report("rule");
		AnalysisReport fallbackReport = report("fallback");
		when(ruleAnalyzer.analyze(command)).thenReturn(ruleReport);
		when(llmAnalyzer.isAvailable("project-1")).thenReturn(false);
		when(fallbackAnalyzer.fromRule(ruleReport, "llm_unavailable")).thenReturn(fallbackReport);

		AnalysisReport result = chain.analyze(command);

		assertThat(result).isEqualTo(fallbackReport);
		verify(llmAnalyzer, never()).analyze(command, ruleReport);
		verify(fallbackAnalyzer).fromRule(ruleReport, "llm_unavailable");
	}

	@Test
	@DisplayName("Analyzer chain은 LLM 분석 예외 시 fallback 리포트를 반환한다")
	void analyzeUsesFallbackWhenLlmThrows() {
		RuleBasedIncidentAnalyzer ruleAnalyzer = mock(RuleBasedIncidentAnalyzer.class);
		LlmIncidentAnalyzer llmAnalyzer = mock(LlmIncidentAnalyzer.class);
		FallbackIncidentAnalyzer fallbackAnalyzer = mock(FallbackIncidentAnalyzer.class);
		DefaultIncidentAnalyzerChain chain = new DefaultIncidentAnalyzerChain(
			ruleAnalyzer,
			llmAnalyzer,
			fallbackAnalyzer
		);

		IncidentReanalyzeCommand command = command();
		AnalysisReport ruleReport = report("rule");
		AnalysisReport fallbackReport = report("fallback");
		when(ruleAnalyzer.analyze(command)).thenReturn(ruleReport);
		when(llmAnalyzer.isAvailable("project-1")).thenReturn(true);
		when(llmAnalyzer.analyze(command, ruleReport)).thenThrow(new IllegalStateException("boom"));
		when(fallbackAnalyzer.fromRule(ruleReport, "llm_failure")).thenReturn(fallbackReport);

		AnalysisReport result = chain.analyze(command);

		assertThat(result).isEqualTo(fallbackReport);
		verify(fallbackAnalyzer).fromRule(ruleReport, "llm_failure");
	}

	private IncidentReanalyzeCommand command() {
		return new IncidentReanalyzeCommand(
			"incident-1",
			"project-1",
			"api",
			"follow-up",
			report("previous")
		);
	}

	private AnalysisReport report(String summarySuffix) {
		return new AnalysisReport(
			"summary-" + summarySuffix,
			List.of(new Hypothesis("cause", 0.7, List.of("evidence"))),
			List.of("next"),
			List.of("limitation")
		);
	}
}
