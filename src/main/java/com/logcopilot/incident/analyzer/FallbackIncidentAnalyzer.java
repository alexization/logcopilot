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
		String sanitizedReasonCode = sanitizeReasonCode(reasonCode);
		limitations.add("LLM analysis unavailable; fallback report generated (" + sanitizedReasonCode + ")");

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

	private String sanitizeReasonCode(String reasonCode) {
		if (reasonCode == null || reasonCode.isBlank()) {
			return "unknown";
		}
		String sanitized = reasonCode.trim()
			.replaceAll("[\\r\\n\\t]", "_")
			.replaceAll("[^a-zA-Z0-9_-]", "_");
		if (sanitized.length() > 64) {
			return sanitized.substring(0, 64);
		}
		return sanitized;
	}
}
