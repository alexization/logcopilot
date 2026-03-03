package com.logcopilot.incident.domain;

import java.util.List;

public record Hypothesis(
	String cause,
	double confidence,
	List<String> evidence
) {
}
