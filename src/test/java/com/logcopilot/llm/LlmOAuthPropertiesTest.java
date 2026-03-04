package com.logcopilot.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmOAuthPropertiesTest {

	@Test
	@DisplayName("LlmOAuthProperties는 0 이하 state TTL을 허용하지 않는다")
	void rejectsNonPositiveStateTtl() {
		LlmOAuthProperties properties = LlmOAuthProperties.defaultProperties();

		assertThatThrownBy(() -> properties.setStateTtl(Duration.ZERO))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("stateTtl must be positive");
	}

	@Test
	@DisplayName("LlmOAuthProperties는 절대 URI가 아닌 callback base URL을 거부한다")
	void rejectsRelativeCallbackBaseUrl() {
		LlmOAuthProperties properties = LlmOAuthProperties.defaultProperties();

		assertThatThrownBy(() -> properties.setCallbackBaseUrl("/relative/callback"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("callbackBaseUrl must be an absolute URI");
	}

	@Test
	@DisplayName("LlmOAuthProperties는 callback base URL에 http/https 외 스킴을 거부한다")
	void rejectsUnsupportedCallbackBaseUrlScheme() {
		LlmOAuthProperties properties = LlmOAuthProperties.defaultProperties();

		assertThatThrownBy(() -> properties.setCallbackBaseUrl("ftp://example.com"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("callbackBaseUrl must use http or https");
	}

	@Test
	@DisplayName("LlmOAuthProperties는 유효한 callback base URL을 허용한다")
	void acceptsValidCallbackBaseUrl() {
		LlmOAuthProperties properties = LlmOAuthProperties.defaultProperties();

		properties.setCallbackBaseUrl("https://logcopilot.example.com");

		assertThat(properties.getCallbackBaseUrl()).isEqualTo("https://logcopilot.example.com");
	}
}
