package com.logcopilot.ingest.domain;

import java.util.Arrays;

public enum IngestSource {
	LOKI("loki"),
	OTLP("otlp"),
	CUSTOM("custom");

	private final String value;

	IngestSource(String value) {
		this.value = value;
	}

	public static boolean isSupported(String source) {
		if (source == null || source.isBlank()) {
			return false;
		}
		return Arrays.stream(values()).anyMatch(it -> it.value.equals(source));
	}
}
