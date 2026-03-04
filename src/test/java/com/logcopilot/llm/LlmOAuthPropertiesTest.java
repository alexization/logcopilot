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

	@Test
	@DisplayName("LlmOAuthProperties는 0 이하 maxStateEntries를 허용하지 않는다")
	void rejectsNonPositiveMaxStateEntries() {
		LlmOAuthProperties properties = LlmOAuthProperties.defaultProperties();

		assertThatThrownBy(() -> properties.setMaxStateEntries(0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxStateEntries must be positive");
	}

	@Test
	@DisplayName("LlmOAuthProperties는 LIVE 모드에서 https와 비로컬 callback URL을 요구한다")
	void rejectsLocalCallbackUrlWhenLiveModeEnabled() {
		LlmOAuthProperties properties = LlmOAuthProperties.defaultProperties();
		properties.setMode(LlmOAuthProperties.Mode.LIVE);

		assertThatThrownBy(properties::validateResolvedConfiguration)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("LIVE mode requires https callbackBaseUrl with non-local host");
	}

	@Test
	@DisplayName("LlmOAuthProperties는 LIVE 모드에서 유효한 callback URL을 허용한다")
	void acceptsLiveModeWithSecureRemoteCallbackUrl() {
		LlmOAuthProperties properties = LlmOAuthProperties.defaultProperties();
		properties.setCallbackBaseUrl("https://logcopilot.example.com");

		properties.setMode(LlmOAuthProperties.Mode.LIVE);

		assertThat(properties.getMode()).isEqualTo(LlmOAuthProperties.Mode.LIVE);
	}

	@Test
	@DisplayName("LlmOAuthProperties는 LIVE 모드 설정 후 callback URL을 설정해도 유효 조합을 허용한다")
	void acceptsLiveModeWithSecureRemoteCallbackUrlWhenModeSetFirst() {
		LlmOAuthProperties properties = LlmOAuthProperties.defaultProperties();
		properties.setMode(LlmOAuthProperties.Mode.LIVE);

		properties.setCallbackBaseUrl("https://logcopilot.example.com");
		properties.validateResolvedConfiguration();

		assertThat(properties.getMode()).isEqualTo(LlmOAuthProperties.Mode.LIVE);
		assertThat(properties.getCallbackBaseUrl()).isEqualTo("https://logcopilot.example.com");
	}
}
