package com.logcopilot.ingest.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum LogSeverity {
	DEBUG("debug"),
	INFO("info"),
	WARN("warn"),
	ERROR("error"),
	FATAL("fatal");

	private static final Set<String> SUPPORTED_VALUES = Arrays.stream(values())
		.map(LogSeverity::value)
		.collect(Collectors.toUnmodifiableSet());

	private final String value;

	LogSeverity(String value) {
		this.value = value;
	}

	public static boolean isSupported(String severity) {
		if (severity == null || severity.isBlank()) {
			return false;
		}
		return SUPPORTED_VALUES.contains(severity);
	}

	private String value() {
		return value;
	}
}
