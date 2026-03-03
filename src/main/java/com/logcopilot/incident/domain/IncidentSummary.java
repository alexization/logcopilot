package com.logcopilot.incident.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record IncidentSummary(
	String id,
	@JsonProperty("project_id")
	String projectId,
	IncidentStatus status,
	String service,
	@JsonProperty("severity_score")
	int severityScore,
	@JsonProperty("event_count")
	int eventCount,
	@JsonProperty("first_seen")
	Instant firstSeen,
	@JsonProperty("last_seen")
	Instant lastSeen
) {
}
