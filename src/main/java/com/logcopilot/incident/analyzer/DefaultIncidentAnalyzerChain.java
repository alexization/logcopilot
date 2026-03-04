package com.logcopilot.incident.analyzer;

import com.logcopilot.common.security.SensitiveDataSanitizer;
import com.logcopilot.incident.domain.AnalysisReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultIncidentAnalyzerChain implements IncidentAnalyzer {

	private static final Logger logger = LoggerFactory.getLogger(DefaultIncidentAnalyzerChain.class);

	private final RuleBasedIncidentAnalyzer ruleBasedIncidentAnalyzer;
	private final LlmIncidentAnalyzer llmIncidentAnalyzer;
	private final FallbackIncidentAnalyzer fallbackIncidentAnalyzer;

	public DefaultIncidentAnalyzerChain(
		RuleBasedIncidentAnalyzer ruleBasedIncidentAnalyzer,
		LlmIncidentAnalyzer llmIncidentAnalyzer,
		FallbackIncidentAnalyzer fallbackIncidentAnalyzer
	) {
		this.ruleBasedIncidentAnalyzer = ruleBasedIncidentAnalyzer;
		this.llmIncidentAnalyzer = llmIncidentAnalyzer;
		this.fallbackIncidentAnalyzer = fallbackIncidentAnalyzer;
	}

	@Override
	public AnalysisReport analyze(IncidentReanalyzeCommand command) {
		AnalysisReport ruleReport = ruleBasedIncidentAnalyzer.analyze(command);
		if (!llmIncidentAnalyzer.isAvailable(command.projectId())) {
			return fallbackIncidentAnalyzer.fromRule(ruleReport, "llm_unavailable");
		}

		try {
			return llmIncidentAnalyzer.analyze(command, ruleReport);
		} catch (RuntimeException exception) {
			logger.warn(
				"LLM analyzer failed for incident: {} (error={})",
				command.incidentId(),
				SensitiveDataSanitizer.sanitize(exception.getMessage())
			);
			return fallbackIncidentAnalyzer.fromRule(ruleReport, "llm_failure");
		}
	}
}
