package com.logcopilot.incident.analyzer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultIncidentAnalyzerLoggingTest {

	@Test
	@DisplayName("Analyzer chain은 LLM 실패 로그에서 token/password/secret 원문을 남기지 않는다")
	void analyzeMasksSensitiveValuesInFailureLogs() {
		RuleBasedIncidentAnalyzer ruleAnalyzer = mock(RuleBasedIncidentAnalyzer.class);
		LlmIncidentAnalyzer llmAnalyzer = mock(LlmIncidentAnalyzer.class);
		FallbackIncidentAnalyzer fallbackAnalyzer = mock(FallbackIncidentAnalyzer.class);
		DefaultIncidentAnalyzerChain chain = new DefaultIncidentAnalyzerChain(
			ruleAnalyzer,
			llmAnalyzer,
			fallbackAnalyzer
		);

		IncidentReanalyzeCommand command = new IncidentReanalyzeCommand(
			"incident-1",
			"project-1",
			"api",
			"follow-up",
			report("previous")
		);
		AnalysisReport ruleReport = report("rule");
		when(ruleAnalyzer.analyze(command)).thenReturn(ruleReport);
		when(llmAnalyzer.isAvailable("project-1")).thenReturn(true);
		when(llmAnalyzer.analyze(command, ruleReport)).thenThrow(
			new IllegalStateException(
				"provider failed token=tok-1 {\"password\":\"pw-1\",\"secret\":\"sec-1\"} Bearer bearer-1"
			)
		);
		when(fallbackAnalyzer.fromRule(ruleReport, "llm_failure")).thenReturn(report("fallback"));

		Logger logger = (Logger) LoggerFactory.getLogger(DefaultIncidentAnalyzerChain.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			chain.analyze(command);
		} finally {
			logger.detachAppender(appender);
		}

		assertThat(appender.list).isNotEmpty();
		ILoggingEvent warn = appender.list.get(0);
		assertThat(warn.getFormattedMessage())
			.contains("LLM analyzer failed")
			.contains("[REDACTED]")
			.doesNotContain("tok-1")
			.doesNotContain("pw-1")
			.doesNotContain("sec-1")
			.doesNotContain("bearer-1");
		assertThat(warn.getThrowableProxy()).isNull();
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
