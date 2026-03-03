package com.logcopilot.ingest.domain;

import java.util.Arrays;

public enum LogSeverity {
	DEBUG("debug"),
	INFO("info"),
	WARN("warn"),
	ERROR("error"),
	FATAL("fatal");

	private final String value;

	LogSeverity(String value) {
		this.value = value;
	}

	public static boolean isSupported(String severity) {
		if (severity == null || severity.isBlank()) {
			return false;
		}
		return Arrays.stream(values()).anyMatch(it -> it.value.equals(severity));
	}
}
