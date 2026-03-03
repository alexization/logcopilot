package com.logcopilot.incident.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IncidentStatus {
	OPEN("open"),
	INVESTIGATING("investigating"),
	RESOLVED("resolved"),
	IGNORED("ignored");

	private final String value;

	IncidentStatus(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
