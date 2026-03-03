package com.logcopilot.incident.analyzer;

import com.logcopilot.incident.domain.AnalysisReport;

public interface IncidentAnalyzer {

	AnalysisReport analyze(IncidentReanalyzeCommand command);
}
