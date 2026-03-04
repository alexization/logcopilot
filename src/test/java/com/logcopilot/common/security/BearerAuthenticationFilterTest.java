package com.logcopilot.common.security;

import com.logcopilot.common.auth.BearerTokenValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BearerAuthenticationFilterTest {

	@Test
	@DisplayName("컨텍스트 경로가 있어도 admin 경로는 인증 필터를 우회한다")
	void shouldSkipFilterForAdminPathWithContextPath() {
		BearerAuthenticationFilter filter = newFilter();

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logcopilot/admin");
		request.setContextPath("/logcopilot");

		assertThat(invokeShouldNotFilter(filter, request)).isTrue();
	}

	@Test
	@DisplayName("컨텍스트 경로가 있어도 OAuth callback 경로는 인증 필터를 우회한다")
	void shouldSkipFilterForOauthCallbackWithContextPath() {
		BearerAuthenticationFilter filter = newFilter();

		MockHttpServletRequest request = new MockHttpServletRequest(
			"GET",
			"/logcopilot/v1/projects/project-1/llm-oauth/openai/callback"
		);
		request.setContextPath("/logcopilot");

		assertThat(invokeShouldNotFilter(filter, request)).isTrue();
	}

	@Test
	@DisplayName("컨텍스트 경로가 있어도 보호 경로는 인증 필터를 우회하지 않는다")
	void shouldFilterProtectedPathWithContextPath() {
		BearerAuthenticationFilter filter = newFilter();

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logcopilot/v1/system/info");
		request.setContextPath("/logcopilot");

		assertThat(invokeShouldNotFilter(filter, request)).isFalse();
	}

	private BearerAuthenticationFilter newFilter() {
		return new BearerAuthenticationFilter(
			new BearerTokenValidator(),
			mock(AuthenticationEntryPoint.class)
		);
	}

	private boolean invokeShouldNotFilter(BearerAuthenticationFilter filter, MockHttpServletRequest request) {
		Boolean shouldNotFilter = ReflectionTestUtils.invokeMethod(filter, "shouldNotFilter", request);
		return Boolean.TRUE.equals(shouldNotFilter);
	}
}
