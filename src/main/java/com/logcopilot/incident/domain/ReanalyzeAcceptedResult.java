package com.logcopilot.incident.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReanalyzeAcceptedResult(
	boolean accepted,
	@JsonProperty("job_id")
	String jobId
) {
}
