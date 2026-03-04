package com.logcopilot.common.auth;

import com.logcopilot.common.error.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BearerTokenValidatorTest {

	private final BearerTokenValidator validator = new BearerTokenValidator();

	@Test
	@DisplayName("BearerTokenValidator는 검증된 API 토큰을 반환한다")
	void returnsValidatedApiTokenWhenAuthorizationHeaderIsValid() {
		BearerTokenValidator.ValidatedToken token = validator.validate("Bearer test-token");

		assertThat(token.value()).isEqualTo("test-token");
		assertThat(token.type()).isEqualTo(BearerTokenValidator.TokenType.API);
	}

	@Test
	@DisplayName("BearerTokenValidator는 검증된 ingest 토큰을 반환한다")
	void returnsValidatedIngestTokenWhenAuthorizationHeaderIsValid() {
		BearerTokenValidator.ValidatedToken token = validator.validate("Bearer ingest-token");

		assertThat(token.value()).isEqualTo("ingest-token");
		assertThat(token.type()).isEqualTo(BearerTokenValidator.TokenType.INGEST);
	}

	@Test
	@DisplayName("BearerTokenValidator는 Authorization 헤더가 누락되면 예외를 던진다")
	void throwsWhenAuthorizationHeaderMissing() {
		assertThatThrownBy(() -> validator.validate(null))
			.isInstanceOf(UnauthorizedException.class)
			.hasMessage("Missing or invalid bearer token");
	}

	@Test
	@DisplayName("BearerTokenValidator는 Bearer 스킴이 아니면 예외를 던진다")
	void throwsWhenAuthorizationSchemeIsInvalid() {
		assertThatThrownBy(() -> validator.validate("Basic token"))
			.isInstanceOf(UnauthorizedException.class)
			.hasMessage("Missing or invalid bearer token");
	}

	@Test
	@DisplayName("BearerTokenValidator는 토큰 값이 비어있으면 예외를 던진다")
	void throwsWhenBearerTokenIsBlank() {
		assertThatThrownBy(() -> validator.validate("Bearer   "))
			.isInstanceOf(UnauthorizedException.class)
			.hasMessage("Missing or invalid bearer token");
	}

	@Test
	@DisplayName("BearerTokenValidator는 토큰 뒤에 추가 세그먼트가 있으면 예외를 던진다")
	void throwsWhenAuthorizationHasMultipleSegments() {
		assertThatThrownBy(() -> validator.validate("Bearer token extra"))
			.isInstanceOf(UnauthorizedException.class)
			.hasMessage("Missing or invalid bearer token");
	}

	@Test
	@DisplayName("BearerTokenValidator는 등록되지 않은 토큰이면 예외를 던진다")
	void throwsWhenTokenIsNotRegistered() {
		assertThatThrownBy(() -> validator.validate("Bearer unknown-token"))
			.isInstanceOf(UnauthorizedException.class)
			.hasMessage("Missing or invalid bearer token");
	}
}
