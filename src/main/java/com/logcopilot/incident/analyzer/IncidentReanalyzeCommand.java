package com.logcopilot.incident.analyzer;

import com.logcopilot.incident.domain.AnalysisReport;

public record IncidentReanalyzeCommand(
	String incidentId,
	String projectId,
	String service,
	String reason,
	AnalysisReport previousReport
) {
}
