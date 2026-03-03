package com.logcopilot.incident.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnalysisReport(
	String summary,
	List<Hypothesis> hypotheses,
	@JsonProperty("next_actions")
	List<String> nextActions,
	List<String> limitations
) {
}
