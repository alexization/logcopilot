package com.logcopilot.incident.analyzer;

public final class ReanalyzeReasonNormalizer {

	private ReanalyzeReasonNormalizer() {
	}

	public static String normalize(String reason) {
		if (reason == null || reason.isBlank()) {
			return "manual trigger";
		}
		return reason.trim();
	}
}
