package com.logcopilot.incident.analyzer;

import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleBasedIncidentAnalyzer {

	public AnalysisReport analyze(IncidentReanalyzeCommand command) {
		String normalizedReason = ReanalyzeReasonNormalizer.normalize(command.reason());
		List<String> evidence = new ArrayList<>();
		evidence.add("Reanalysis reason: " + normalizedReason);

		AnalysisReport previousReport = command.previousReport();
		if (previousReport != null && previousReport.summary() != null && !previousReport.summary().isBlank()) {
			evidence.add("Previous summary: " + previousReport.summary());
		}

		return new AnalysisReport(
			"Rule-based reanalysis for incident " + command.incidentId(),
			List.of(new Hypothesis(
				"Repeated failure signals detected for service " + command.service(),
				0.62,
				evidence
			)),
			List.of(
				"Review recent deployments for service " + command.service(),
				"Correlate dependency latency and error spikes"
			),
			List.of("Rule analyzer baseline only")
		);
	}
}
