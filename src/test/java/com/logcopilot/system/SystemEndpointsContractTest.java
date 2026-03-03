package com.logcopilot.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SystemEndpointsContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthzReturnsOkStatus() throws Exception {
		mockMvc.perform(get("/healthz"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void readyzReturnsReadyStatusAndChecks() throws Exception {
		mockMvc.perform(get("/readyz"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ready"))
			.andExpect(jsonPath("$.checks").isMap())
			.andExpect(jsonPath("$.checks.application").value("up"));
	}

	@Test
	void systemInfoRejectsMissingBearerToken() throws Exception {
		mockMvc.perform(get("/v1/system/info"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	void systemInfoRejectsBlankBearerToken() throws Exception {
		mockMvc.perform(get("/v1/system/info")
				.header("Authorization", "Bearer    "))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("unauthorized"))
			.andExpect(jsonPath("$.error.message").value("Missing or invalid bearer token"));
	}

	@Test
	void systemInfoReturnsRuntimeInfoWhenAuthorized() throws Exception {
		mockMvc.perform(get("/v1/system/info")
				.header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.version").value("1.0.0-mvp"))
			.andExpect(jsonPath("$.data.storage_mode").value("sqlite"))
			.andExpect(jsonPath("$.data.queue_mode").value("in_process"))
			.andExpect(jsonPath("$.data.features").isMap());
	}
}
