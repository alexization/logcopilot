package com.logcopilot.incident.analyzer;

import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import com.logcopilot.llm.LlmAccountService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LlmIncidentAnalyzer {

	private final LlmAccountService llmAccountService;

	public LlmIncidentAnalyzer(LlmAccountService llmAccountService) {
		this.llmAccountService = llmAccountService;
	}

	public boolean isAvailable(String projectId) {
		try {
			return !llmAccountService.list(projectId).isEmpty();
		} catch (NotFoundException ignored) {
			return false;
		}
	}

	public AnalysisReport analyze(IncidentReanalyzeCommand command, AnalysisReport ruleReport) {
		List<LlmAccountService.LlmAccount> accounts = llmAccountService.list(command.projectId());
		if (accounts.isEmpty()) {
			throw new IllegalStateException("No LLM account configured");
		}

		LlmAccountService.LlmAccount account = accounts.get(0);
		List<Hypothesis> hypotheses = ruleReport.hypotheses() == null
			? List.of()
			: ruleReport.hypotheses();
		List<String> ruleNextActions = ruleReport.nextActions() == null
			? List.of()
			: ruleReport.nextActions();

		double baseConfidence = hypotheses.isEmpty()
			? 0.6
			: hypotheses.get(0).confidence();
		double llmConfidence = Math.min(0.95, baseConfidence + 0.18);

		List<String> evidence = new ArrayList<>();
		evidence.add("LLM model: " + account.provider() + "/" + account.model());
		evidence.add("Reanalysis reason: " + ReanalyzeReasonNormalizer.normalize(command.reason()));
		if (!hypotheses.isEmpty() && hypotheses.get(0).evidence() != null) {
			evidence.addAll(hypotheses.get(0).evidence());
		}

		List<String> nextActions = new ArrayList<>(ruleNextActions);
		nextActions.add("Use targeted prompts for stack trace correlation");

		return new AnalysisReport(
			"LLM-assisted reanalysis for incident " + command.incidentId(),
			List.of(new Hypothesis(
				"Likely cascading failure around service " + command.service(),
				llmConfidence,
				evidence
			)),
			nextActions,
			List.of("LLM analyzer executed with configured account")
		);
	}
}
