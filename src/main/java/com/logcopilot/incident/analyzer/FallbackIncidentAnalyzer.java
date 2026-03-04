package com.logcopilot.incident.analyzer;

import com.logcopilot.common.security.SensitiveDataSanitizer;
import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FallbackIncidentAnalyzer {

	public AnalysisReport fromRule(AnalysisReport ruleReport, String reasonCode) {
		List<Hypothesis> hypotheses = sanitizeHypotheses(ruleReport.hypotheses());
		List<String> nextActions = sanitizeTextList(ruleReport.nextActions());

		List<String> limitations = new ArrayList<>(sanitizeTextList(ruleReport.limitations()));
		limitations.add("LLM analysis unavailable; fallback report generated (" + reasonCode + ")");

		return new AnalysisReport(
			SensitiveDataSanitizer.sanitize(ruleReport.summary()),
			hypotheses,
			nextActions,
			limitations
		);
	}

	private List<Hypothesis> sanitizeHypotheses(List<Hypothesis> hypotheses) {
		if (hypotheses == null) {
			return List.of();
		}

		return hypotheses.stream()
			.map(hypothesis -> new Hypothesis(
				SensitiveDataSanitizer.sanitize(hypothesis.cause()),
				hypothesis.confidence(),
				sanitizeTextList(hypothesis.evidence())
			))
			.toList();
	}

	private List<String> sanitizeTextList(List<String> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream()
			.map(SensitiveDataSanitizer::sanitize)
			.toList();
	}
}
