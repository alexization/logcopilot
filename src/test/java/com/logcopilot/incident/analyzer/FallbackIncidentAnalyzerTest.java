package com.logcopilot.incident.analyzer;

import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackIncidentAnalyzerTest {

	private final FallbackIncidentAnalyzer fallbackAnalyzer = new FallbackIncidentAnalyzer();

	@Test
	@DisplayName("Fallback analyzer는 rule report의 민감정보를 마스킹해서 반환한다")
	void fromRuleSanitizesSensitiveValues() {
		AnalysisReport ruleReport = new AnalysisReport(
			"summary token=abc",
			List.of(new Hypothesis(
				"cause password=pw-1",
				0.62,
				List.of("secret=sec-1", "{\"token\":\"tok-1\"}")
			)),
			List.of("Rotate password=pw-1"),
			List.of("limitation token=abc")
		);

		AnalysisReport result = fallbackAnalyzer.fromRule(ruleReport, "llm_failure");
		String flattened = result.summary()
			+ " "
			+ result.hypotheses().get(0).cause()
			+ " "
			+ String.join(" ", result.hypotheses().get(0).evidence())
			+ " "
			+ String.join(" ", result.nextActions())
			+ " "
			+ String.join(" ", result.limitations());

		assertThat(flattened)
			.contains("[REDACTED]")
			.doesNotContain("token=abc")
			.doesNotContain("pw-1")
			.doesNotContain("sec-1")
			.doesNotContain("tok-1");
	}
}
