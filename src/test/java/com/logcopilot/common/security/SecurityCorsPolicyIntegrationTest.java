package com.logcopilot.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "logcopilot.security.cors.allowed-origins=https://admin.logcopilot.local")
@AutoConfigureMockMvc
class SecurityCorsPolicyIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("허용 origin 의 preflight 요청은 통과하고 CORS 헤더를 반환한다")
	void allowedOriginPreflightReturnsCorsHeaders() throws Exception {
		mockMvc.perform(options("/v1/system/info")
				.header("Origin", "https://admin.logcopilot.local")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "Authorization,Content-Type"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://admin.logcopilot.local"));
	}

	@Test
	@DisplayName("미허용 origin 의 preflight 요청은 차단된다")
	void disallowedOriginPreflightIsRejected() throws Exception {
		mockMvc.perform(options("/v1/system/info")
				.header("Origin", "https://evil.example.com")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "Authorization"))
			.andExpect(status().isForbidden());
	}
}
