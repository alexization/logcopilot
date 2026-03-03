package com.logcopilot.incident.analyzer;

import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FallbackIncidentAnalyzer {

	public AnalysisReport fromRule(AnalysisReport ruleReport, String reasonCode) {
		List<Hypothesis> hypotheses = ruleReport.hypotheses() == null
			? List.of()
			: List.copyOf(ruleReport.hypotheses());
		List<String> nextActions = ruleReport.nextActions() == null
			? List.of()
			: List.copyOf(ruleReport.nextActions());

		List<String> limitations = new ArrayList<>();
		if (ruleReport.limitations() != null) {
			limitations.addAll(ruleReport.limitations());
		}
		limitations.add("LLM analysis unavailable; fallback report generated (" + reasonCode + ")");

		return new AnalysisReport(
			ruleReport.summary(),
			hypotheses,
			nextActions,
			limitations
		);
	}
}
