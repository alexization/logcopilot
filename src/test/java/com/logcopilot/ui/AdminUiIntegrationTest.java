package com.logcopilot.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("admin 진입점은 인증 없이 접근 가능한 UI shell을 반환한다")
	void adminEntryPointReturnsShell() throws Exception {
		mockMvc.perform(get("/admin"))
			.andExpect(status().isOk())
			.andExpect(forwardedUrl("/admin/index.html"));

		mockMvc.perform(get("/admin/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("LogCopilot Admin Console")))
			.andExpect(content().string(containsString("data-nav=\"projects\"")))
			.andExpect(content().string(containsString("name=\"viewport\"")));
	}

	@Test
	@DisplayName("admin shell 응답은 기본 보안 헤더를 포함한다")
	void adminShellIncludesSecurityHeaders() throws Exception {
		mockMvc.perform(get("/admin"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("Referrer-Policy", "no-referrer"))
			.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
	}

	@Test
	@DisplayName("admin 정적 리소스가 제공된다")
	void adminStaticAssetsAreServed() throws Exception {
		mockMvc.perform(get("/admin/app.js"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", containsString("javascript")));

		mockMvc.perform(get("/admin/styles.css"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/css"));
	}

	@Test
	@DisplayName("admin shell 은 반응형/접근성 베이스 마크업을 포함한다")
	void adminShellContainsResponsiveAndAccessibilityBasics() throws Exception {
		mockMvc.perform(get("/admin/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("aria-label=\"main-navigation\"")))
			.andExpect(content().string(containsString("aria-live=\"polite\"")))
			.andExpect(content().string(containsString("tabindex=\"0\"")));

		mockMvc.perform(get("/admin/styles.css"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("@media (max-width: 900px)")))
			.andExpect(content().string(containsString("@media (max-width: 620px)")))
			.andExpect(content().string(containsString(":focus-visible")));
	}

	@Test
	@DisplayName("admin shell 은 T-20 운영 워크스페이스 골격을 포함한다")
	void adminShellContainsOperationalWorkspaceScaffold() throws Exception {
		mockMvc.perform(get("/admin/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-nav=\"projects\"")))
			.andExpect(content().string(containsString("data-nav=\"connectors\"")))
			.andExpect(content().string(containsString("data-nav=\"llm\"")))
			.andExpect(content().string(containsString("data-nav=\"policies\"")))
			.andExpect(content().string(containsString("data-nav=\"alerts\"")))
			.andExpect(content().string(containsString("data-nav=\"incidents\"")))
			.andExpect(content().string(containsString("data-nav=\"audit\"")))
			.andExpect(content().string(containsString("id=\"active-project-id\"")))
			.andExpect(content().string(containsString("id=\"section-feedback\"")))
			.andExpect(content().string(containsString("id=\"section-body\"")));
	}

	@Test
	@DisplayName("admin app script 는 T-20 운영 API 경로와 렌더러를 포함한다")
	void adminAppScriptContainsOperationalEndpointsAndRenderers() throws Exception {
		mockMvc.perform(get("/admin/app.js"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("renderProjectsSection")))
			.andExpect(content().string(containsString("renderConnectorsSection")))
			.andExpect(content().string(containsString("renderLlmSection")))
			.andExpect(content().string(containsString("renderPoliciesSection")))
			.andExpect(content().string(containsString("renderAlertsSection")))
			.andExpect(content().string(containsString("renderIncidentsSection")))
			.andExpect(content().string(containsString("renderAuditSection")))
			.andExpect(content().string(containsString("loadConnectorSettings")))
			.andExpect(content().string(containsString("loadPolicySettings")))
			.andExpect(content().string(containsString("loadAlertSettings")))
			.andExpect(content().string(containsString("refreshRequestId")))
			.andExpect(content().string(containsString("isRefreshContextCurrent")))
			.andExpect(content().string(containsString("hasFocusedFieldInSection")))
			.andExpect(content().string(containsString("window.open(")))
			.andExpect(content().string(containsString("maskOAuthStartResult")))
			.andExpect(content().string(containsString("/v1/bootstrap/status")))
			.andExpect(content().string(containsString("/v1/bootstrap/initialize")))
			.andExpect(content().string(containsString("/v1/tokens")))
			.andExpect(content().string(containsString("/v1/tokens/")))
			.andExpect(content().string(containsString("/rotate")))
			.andExpect(content().string(containsString("/revoke")))
			.andExpect(content().string(containsString("/v1/projects")))
			.andExpect(content().string(containsString("/connectors/loki")))
			.andExpect(content().string(containsString("getLokiConnector")))
			.andExpect(content().string(containsString("/llm-accounts/api-key")))
			.andExpect(content().string(containsString("/policies/export")))
			.andExpect(content().string(containsString("getExportPolicy")))
			.andExpect(content().string(containsString("getRedactionPolicy")))
			.andExpect(content().string(containsString("/alerts/slack")))
			.andExpect(content().string(containsString("/alerts/email")))
			.andExpect(content().string(containsString("getSlackAlert")))
			.andExpect(content().string(containsString("getEmailAlert")))
			.andExpect(content().string(containsString("/v1/incidents/")))
			.andExpect(content().string(containsString("/audit-logs")));
	}
}
