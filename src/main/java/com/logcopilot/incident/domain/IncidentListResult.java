package com.logcopilot.incident.domain;

import java.util.List;

public record IncidentListResult(
	List<IncidentSummary> data,
	String nextCursor
) {
}
