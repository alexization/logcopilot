package com.logcopilot.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataSanitizerTest {

	@Test
	@DisplayName("sanitize는 JSON 숫자/불리언/null 민감값도 마스킹한다")
	void sanitizeMasksJsonLiteralValues() {
		String raw = "{\"token\":123,\"password\":true,\"secret\":null}";

		String sanitized = SensitiveDataSanitizer.sanitize(raw);

		assertThat(sanitized)
			.contains("\"token\":\"[REDACTED]\"")
			.contains("\"password\":\"[REDACTED]\"")
			.contains("\"secret\":\"[REDACTED]\"")
			.doesNotContain(":123")
			.doesNotContain(":true")
			.doesNotContain(":null");
	}

	@Test
	@DisplayName("containsUnmaskedSensitiveValue는 JSON 숫자/불리언/null 민감값을 감지한다")
	void containsUnmaskedSensitiveValueDetectsJsonLiteralValues() {
		assertThat(SensitiveDataSanitizer.containsUnmaskedSensitiveValue("{\"token\":123}"))
			.isTrue();
		assertThat(SensitiveDataSanitizer.containsUnmaskedSensitiveValue("{\"password\":false}"))
			.isTrue();
		assertThat(SensitiveDataSanitizer.containsUnmaskedSensitiveValue("{\"secret\":null}"))
			.isTrue();
		assertThat(SensitiveDataSanitizer.containsUnmaskedSensitiveValue("{\"token\":\"[REDACTED]\"}"))
			.isFalse();
	}
}
