package com.logcopilot.common.security;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveDataSanitizer {

	private static final Pattern QUOTED_KEY_VALUE_PATTERN = Pattern.compile(
		"(?i)(\"(?:token|password|secret)\"\\s*:\\s*\")([^\"]*)(\")"
	);
	private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
		"(?i)(\\b(?:token|password|secret)\\b\\s*[:=]\\s*)([^\\s,;}]+)"
	);
	private static final Pattern BEARER_PATTERN = Pattern.compile(
		"(?i)(\\bbearer\\s+)([^\\s,;]+)"
	);

	private SensitiveDataSanitizer() {
	}

	public static String sanitize(String text) {
		if (text == null || text.isBlank()) {
			return text;
		}

		String sanitized = QUOTED_KEY_VALUE_PATTERN.matcher(text)
			.replaceAll("$1[REDACTED]$3");
		sanitized = KEY_VALUE_PATTERN.matcher(sanitized)
			.replaceAll("$1[REDACTED]");
		return BEARER_PATTERN.matcher(sanitized)
			.replaceAll("$1[REDACTED]");
	}

	public static boolean containsUnmaskedSensitiveValue(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		return hasUnmaskedValue(QUOTED_KEY_VALUE_PATTERN.matcher(text))
			|| hasUnmaskedValue(KEY_VALUE_PATTERN.matcher(text))
			|| hasUnmaskedValue(BEARER_PATTERN.matcher(text));
	}

	private static boolean hasUnmaskedValue(Matcher matcher) {
		while (matcher.find()) {
			String value = matcher.group(2);
			if (!isMaskedValue(value)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isMaskedValue(String value) {
		if (value == null) {
			return true;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return normalized.equals("[redacted]")
			|| normalized.equals("[masked]")
			|| normalized.equals("redacted")
			|| normalized.equals("masked")
			|| normalized.equals("<redacted>")
			|| normalized.matches("\\*{3,}");
	}
}
