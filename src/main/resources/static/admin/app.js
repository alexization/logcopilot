(function () {
	"use strict";

	const SECTION_TEXT = {
		overview: {
			title: "개요",
			description: "bootstrap 상태, 시스템 개요, 토큰 수명주기를 관리합니다."
		},
		projects: {
			title: "프로젝트",
			description: "프로젝트 생성/조회와 활성 프로젝트 선택을 수행합니다."
		},
		connectors: {
			title: "커넥터",
			description: "Loki 커넥터 설정/테스트를 수행합니다."
		},
		llm: {
			title: "LLM 계정",
			description: "API Key/OAuth 시작, 계정 목록/삭제를 수행합니다."
		},
		policies: {
			title: "정책",
			description: "Export/Redaction 정책을 업데이트합니다."
		},
		alerts: {
			title: "알림",
			description: "Slack/Email 알림 채널을 구성합니다."
		},
		incidents: {
			title: "인시던트",
			description: "목록/상세 조회 및 재분석 요청을 처리합니다."
		},
		audit: {
			title: "감사 로그",
			description: "cursor/limit 기반 감사 로그 조회와 실패 UX를 제공합니다."
		}
	};

	const state = {
		activeNav: "overview",
		token: "",
		toastTimer: null,
		bootstrapStatus: {
			bootstrapped: true,
			initialized_at: null
		},
		tokens: [],
		projects: [],
		activeProjectId: "",
		llmAccounts: [],
		incidents: [],
		incidentMeta: null,
		incidentFilter: {
			status: "",
			service: "",
			cursor: "",
			limit: "50"
		},
		selectedIncident: null,
		auditLogs: [],
		auditMeta: null,
		auditFilter: {
			action: "",
			actor: "",
			cursor: "",
			limit: "50"
		}
	};

	const elements = {
		navItems: Array.from(document.querySelectorAll("[data-nav]")),
		tokenInput: document.getElementById("api-token-input"),
		connectButton: document.getElementById("connect-button"),
		clearButton: document.getElementById("clear-button"),
		refreshButton: document.getElementById("refresh-button"),
		sectionTitle: document.getElementById("section-title"),
		sectionDescription: document.getElementById("section-description"),
		activeProjectId: document.getElementById("active-project-id"),
		sectionFeedback: document.getElementById("section-feedback"),
		sectionBody: document.getElementById("section-body"),
		authStatus: document.getElementById("auth-status"),
		preview: document.getElementById("response-preview"),
		toast: document.getElementById("toast")
	};

	if (!validateRequiredElements()) {
		return;
	}

	const apiClient = createApiClient(getStoredToken);

	wireGlobalEvents();
	bootstrap();

	async function bootstrap() {
		try {
			await refreshBootstrapStatus();
		} catch (error) {
			setPreview({
				status: "error",
				message: "bootstrap 상태 조회 실패: " + errorMessage(error)
			});
		}
		updateAuthStatus();
		activateNav(state.activeNav);
	}

	function wireGlobalEvents() {
		elements.navItems.forEach((item) => {
			item.addEventListener("click", () => {
				activateNav(item.dataset.nav || "overview");
			});
		});

		elements.connectButton.addEventListener("click", async () => {
			if (!state.bootstrapStatus.bootstrapped) {
				showToast("먼저 bootstrap을 완료해 주세요.");
				return;
			}
			const token = elements.tokenInput.value.trim();
			if (!token) {
				showToast("API 토큰을 입력해 주세요.");
				return;
			}
			state.token = token;
			updateAuthStatus();
			setSectionFeedback("토큰이 설정되었습니다. 운영 API를 호출할 수 있습니다.", "success");
			await refreshCurrentSection();
		});

		elements.clearButton.addEventListener("click", () => {
			state.token = "";
			elements.tokenInput.value = "";
			clearCachedData();
			updateAuthStatus();
			setPreview("토큰이 제거되었습니다.");
			activateNav(state.activeNav);
		});

		elements.refreshButton.addEventListener("click", () => {
			refreshCurrentSection();
		});
	}

	function clearCachedData() {
		state.tokens = [];
		state.projects = [];
		updateActiveProject("");
	}

	function validateRequiredElements() {
		const missing = [];
		if (elements.navItems.length === 0) {
			missing.push("navItems");
		}

		const requiredKeys = [
			"tokenInput",
			"connectButton",
			"clearButton",
			"refreshButton",
			"sectionTitle",
			"sectionDescription",
			"activeProjectId",
			"sectionFeedback",
			"sectionBody",
			"authStatus",
			"preview",
			"toast"
		];

		requiredKeys.forEach((key) => {
			if (!elements[key]) {
				missing.push(key);
			}
		});

		if (missing.length > 0) {
			console.error("[AdminUI] Required element missing:", missing.join(", "));
			return false;
		}
		return true;
	}

	function activateNav(nav) {
		state.activeNav = SECTION_TEXT[nav] ? nav : "overview";
		elements.navItems.forEach((item) => {
			item.classList.toggle("active", item.dataset.nav === state.activeNav);
		});

		const section = SECTION_TEXT[state.activeNav];
		elements.sectionTitle.textContent = section.title;
		elements.sectionDescription.textContent = section.description;
		setSectionFeedback(section.description, "info");
		renderActiveProject();
		renderActiveSection();
	}

	function renderActiveSection() {
		switch (state.activeNav) {
			case "overview":
				renderOverviewSection();
				break;
			case "projects":
				renderProjectsSection();
				break;
			case "connectors":
				renderConnectorsSection();
				break;
			case "llm":
				renderLlmSection();
				break;
			case "policies":
				renderPoliciesSection();
				break;
			case "alerts":
				renderAlertsSection();
				break;
			case "incidents":
				renderIncidentsSection();
				break;
			case "audit":
				renderAuditSection();
				break;
			default:
				renderOverviewSection();
		}
	}

	function renderOverviewSection() {
		const hasToken = Boolean(getStoredToken());
		const bootstrapCompleted = Boolean(state.bootstrapStatus && state.bootstrapStatus.bootstrapped);
		if (!bootstrapCompleted) {
			setSectionBody(
				"<article class=\"workspace-card\">" +
					"<h3>초기 bootstrap</h3>" +
					"<p class=\"muted\">빈 상태에서는 bootstrap을 먼저 실행해야 운영 토큰이 발급됩니다.</p>" +
					"<form id=\"bootstrap-form\" class=\"form-grid\">" +
						"<label for=\"bootstrap-project-name\">기본 프로젝트 이름</label>" +
						"<input id=\"bootstrap-project-name\" type=\"text\" maxlength=\"100\" placeholder=\"bootstrap-core\" required>" +
						"<label for=\"bootstrap-environment\">환경</label>" +
						"<select id=\"bootstrap-environment\">" +
							"<option value=\"prod\">prod</option>" +
							"<option value=\"staging\">staging</option>" +
							"<option value=\"dev\">dev</option>" +
						"</select>" +
						"<label for=\"bootstrap-operator-token-name\">운영자 토큰 이름</label>" +
						"<input id=\"bootstrap-operator-token-name\" type=\"text\" maxlength=\"80\" placeholder=\"ui-admin\">" +
						"<label for=\"bootstrap-ingest-token-name\">ingest 토큰 이름</label>" +
						"<input id=\"bootstrap-ingest-token-name\" type=\"text\" maxlength=\"80\" placeholder=\"collector\">" +
						"<div class=\"inline-actions\">" +
							"<button type=\"submit\">bootstrap 실행</button>" +
							"<button type=\"button\" class=\"secondary\" id=\"bootstrap-status-refresh\">상태 새로고침</button>" +
						"</div>" +
					"</form>" +
					"<pre class=\"preview preview-inline\" id=\"bootstrap-result\">아직 bootstrap이 완료되지 않았습니다.</pre>" +
				"</article>"
			);

			const bootstrapForm = queryInSection("#bootstrap-form");
			const bootstrapStatusRefresh = queryInSection("#bootstrap-status-refresh");
			const bootstrapResult = queryInSection("#bootstrap-result");

			if (bootstrapForm) {
				bootstrapForm.addEventListener("submit", async (event) => {
					event.preventDefault();
					try {
						const payload = {
							project_name: valueOf("#bootstrap-project-name").trim(),
							environment: valueOf("#bootstrap-environment").trim() || "prod",
							operator_token_name: valueOf("#bootstrap-operator-token-name").trim() || null,
							ingest_token_name: valueOf("#bootstrap-ingest-token-name").trim() || null
						};
						const response = await apiClient.initializeBootstrap(payload);
						if (bootstrapResult) {
							bootstrapResult.textContent = formatJson(response);
						}
						state.bootstrapStatus = response && response.data
							? {
								bootstrapped: Boolean(response.data.bootstrapped),
								initialized_at: response.data.initialized_at || null
							}
							: { bootstrapped: true, initialized_at: null };
						const operatorToken = response && response.data && response.data.operator_token
							? response.data.operator_token.token
							: "";
						if (operatorToken) {
							state.token = operatorToken;
							elements.tokenInput.value = operatorToken;
						}
						await refreshProjects();
						await refreshTokens();
						updateAuthStatus();
						setSectionFeedback("bootstrap이 완료되었습니다. 운영자 토큰이 자동 설정되었습니다.", "success");
						setPreview(response);
						renderOverviewSection();
					} catch (error) {
						handleSectionError("bootstrap 실행에 실패했습니다.", error);
					}
				});
			}

			if (bootstrapStatusRefresh) {
				bootstrapStatusRefresh.addEventListener("click", async () => {
					try {
						const response = await refreshBootstrapStatus();
						if (bootstrapResult) {
							bootstrapResult.textContent = formatJson(response);
						}
						setSectionFeedback("bootstrap 상태를 갱신했습니다.", "success");
						renderOverviewSection();
					} catch (error) {
						handleSectionError("bootstrap 상태 조회에 실패했습니다.", error);
					}
				});
			}

			setSectionFeedback("초기 bootstrap을 완료해 주세요.", "info");
			setPreview({
				bootstrap_status: state.bootstrapStatus
			});
			return;
		}

		setSectionBody(
			"<div class=\"workspace-grid\">" +
				"<article class=\"workspace-card\">" +
					"<h3>운영 상태</h3>" +
					"<p class=\"muted\">bootstrap 완료 이후 토큰이 설정되어야 API 작업이 가능합니다.</p>" +
					"<p><strong>토큰 상태:</strong> " + (hasToken ? "설정됨" : "미설정") + "</p>" +
					"<p><strong>bootstrap 완료 시각:</strong> " + escapeHtml(formatDateTime(state.bootstrapStatus.initialized_at)) + "</p>" +
					"<button type=\"button\" class=\"secondary\" id=\"overview-load-button\">개요 데이터 조회</button>" +
				"</article>" +
				"<article class=\"workspace-card\">" +
					"<h3>프로젝트 스냅샷</h3>" +
					renderProjectListTable(state.projects, true) +
				"</article>" +
				"<article class=\"workspace-card\">" +
					"<h3>토큰 수명주기</h3>" +
					"<form id=\"issue-token-form\" class=\"form-grid\">" +
						"<label for=\"issue-token-name\">토큰 이름</label>" +
						"<input id=\"issue-token-name\" type=\"text\" maxlength=\"80\" placeholder=\"ci-agent\">" +
						"<label for=\"issue-token-role\">역할</label>" +
						"<select id=\"issue-token-role\">" +
							"<option value=\"operator\">operator</option>" +
							"<option value=\"api\" selected>api</option>" +
							"<option value=\"ingest\">ingest</option>" +
						"</select>" +
						"<div class=\"inline-actions\">" +
							"<button type=\"submit\">토큰 발급</button>" +
							"<button type=\"button\" class=\"secondary\" id=\"refresh-tokens-button\">목록 새로고침</button>" +
						"</div>" +
					"</form>" +
					renderTokensTable(state.tokens) +
				"</article>" +
			"</div>"
		);

		const loadButton = queryInSection("#overview-load-button");
		const issueTokenForm = queryInSection("#issue-token-form");
		const refreshTokensButton = queryInSection("#refresh-tokens-button");
		const rotateButtons = Array.from(queryInSectionAll(".rotate-token-button"));
		const revokeButtons = Array.from(queryInSectionAll(".revoke-token-button"));
		if (loadButton) {
			loadButton.addEventListener("click", () => {
				refreshCurrentSection();
			});
		}

		if (!hasToken) {
			setSectionFeedback("API 토큰을 입력하면 시스템/프로젝트 데이터를 조회할 수 있습니다.", "info");
			setPreview("API 조회를 위해 토큰을 먼저 설정해 주세요.");
			return;
		}

		if (issueTokenForm) {
			issueTokenForm.addEventListener("submit", async (event) => {
				event.preventDefault();
				try {
					requireToken();
					const payload = {
						name: valueOf("#issue-token-name").trim() || null,
						role: valueOf("#issue-token-role").trim() || "api"
					};
					const response = await apiClient.issueToken(payload);
					await refreshTokens();
					setSectionFeedback("토큰을 발급했습니다.", "success");
					setPreview(response);
					renderOverviewSection();
				} catch (error) {
					handleSectionError("토큰 발급에 실패했습니다.", error);
				}
			});
		}

		if (refreshTokensButton) {
			refreshTokensButton.addEventListener("click", async () => {
				try {
					const response = await refreshTokens();
					setSectionFeedback("토큰 목록을 갱신했습니다.", "success");
					setPreview(response);
					renderOverviewSection();
				} catch (error) {
					handleSectionError("토큰 목록 조회에 실패했습니다.", error);
				}
			});
		}

		rotateButtons.forEach((button) => {
			button.addEventListener("click", async () => {
				const tokenId = button.getAttribute("data-token-id") || "";
				if (!tokenId) {
					return;
				}
				try {
					const response = await apiClient.rotateToken(tokenId);
					await refreshTokens();
					setSectionFeedback("토큰을 회전했습니다. 새 토큰은 Preview에서 확인하세요.", "success");
					setPreview(response);
					renderOverviewSection();
				} catch (error) {
					handleSectionError("토큰 회전에 실패했습니다.", error);
				}
			});
		});

		revokeButtons.forEach((button) => {
			button.addEventListener("click", async () => {
				const tokenId = button.getAttribute("data-token-id") || "";
				if (!tokenId) {
					return;
				}
				try {
					const response = await apiClient.revokeToken(tokenId, { reason: "revoked-from-admin-ui" });
					await refreshTokens();
					setSectionFeedback("토큰을 폐기했습니다.", "success");
					setPreview(response);
					renderOverviewSection();
				} catch (error) {
					handleSectionError("토큰 폐기에 실패했습니다.", error);
				}
			});
		});

		setSectionHint("새로고침 또는 '개요 데이터 조회'로 최신 상태를 확인하세요.");
	}

	function renderProjectsSection() {
		setSectionBody(
			"<div class=\"workspace-grid\">" +
				"<article class=\"workspace-card\">" +
					"<h3>프로젝트 생성</h3>" +
					"<form id=\"create-project-form\" class=\"form-grid\">" +
						"<label for=\"project-name-input\">이름</label>" +
						"<input id=\"project-name-input\" type=\"text\" maxlength=\"100\" placeholder=\"payments-prod\" required>" +
						"<label for=\"project-environment-select\">환경</label>" +
						"<select id=\"project-environment-select\">" +
							"<option value=\"prod\">prod</option>" +
							"<option value=\"staging\">staging</option>" +
							"<option value=\"dev\">dev</option>" +
						"</select>" +
						"<div class=\"inline-actions\">" +
							"<button type=\"submit\">프로젝트 생성</button>" +
							"<button type=\"button\" class=\"secondary\" id=\"refresh-projects-button\">목록 새로고침</button>" +
						"</div>" +
					"</form>" +
				"</article>" +
				"<article class=\"workspace-card\">" +
					"<h3>프로젝트 목록</h3>" +
					renderProjectListTable(state.projects, false) +
				"</article>" +
			"</div>"
		);

		const createForm = queryInSection("#create-project-form");
		const refreshButton = queryInSection("#refresh-projects-button");
		const selectButtons = Array.from(queryInSectionAll(".select-project-button"));

		if (createForm) {
			createForm.addEventListener("submit", async (event) => {
				event.preventDefault();
				if (!getStoredToken()) {
					showToast("API 토큰을 먼저 설정해 주세요.");
					return;
				}

				const nameInput = queryInSection("#project-name-input");
				const environmentSelect = queryInSection("#project-environment-select");
				const name = nameInput ? nameInput.value.trim() : "";
				const environment = environmentSelect ? environmentSelect.value : "prod";

				if (!name) {
					showToast("프로젝트 이름을 입력해 주세요.");
					return;
				}

				try {
					const created = await apiClient.createProject({
						name,
						environment
					});
					await refreshProjects();
					if (created && created.data && created.data.id) {
						updateActiveProject(created.data.id);
						renderActiveProject();
					}
					setSectionFeedback("프로젝트가 생성되었습니다.", "success");
					setPreview(created);
					renderProjectsSection();
				} catch (error) {
					handleSectionError("프로젝트 생성에 실패했습니다.", error);
				}
			});
		}

		if (refreshButton) {
			refreshButton.addEventListener("click", async () => {
				try {
					const projects = await refreshProjects();
					setSectionFeedback("프로젝트 목록을 갱신했습니다.", "success");
					setPreview(projects);
					renderProjectsSection();
				} catch (error) {
					handleSectionError("프로젝트 목록 조회에 실패했습니다.", error);
				}
			});
		}

		selectButtons.forEach((button) => {
			button.addEventListener("click", () => {
				const projectId = button.getAttribute("data-project-id") || "";
				updateActiveProject(projectId);
				renderActiveProject();
				setSectionFeedback("활성 프로젝트를 선택했습니다: " + projectId, "success");
				renderProjectsSection();
			});
		});

		setSectionHint("프로젝트를 생성하거나 기존 프로젝트를 활성화하세요.");
	}

	function renderConnectorsSection() {
		const projectId = state.activeProjectId;
		if (!projectId) {
			renderProjectRequiredMessage("커넥터 설정");
			return;
		}

		setSectionBody(
			"<article class=\"workspace-card\">" +
				"<h3>Loki 커넥터 설정</h3>" +
				"<p class=\"muted\">프로젝트: <code>" + escapeHtml(projectId) + "</code></p>" +
				"<form id=\"connector-upsert-form\" class=\"form-grid\">" +
					"<label for=\"connector-endpoint\">Endpoint</label>" +
					"<input id=\"connector-endpoint\" type=\"url\" placeholder=\"https://loki.internal\" required>" +
					"<label for=\"connector-tenant\">Tenant ID (선택)</label>" +
					"<input id=\"connector-tenant\" type=\"text\" placeholder=\"tenant-a\">" +
					"<label for=\"connector-query\">Query</label>" +
					"<input id=\"connector-query\" type=\"text\" placeholder=\"{service=\\\"api\\\"} |= \\\"error\\\"\" required>" +
					"<label for=\"connector-poll\">Poll Interval (sec)</label>" +
					"<input id=\"connector-poll\" type=\"number\" min=\"5\" max=\"300\" value=\"30\">" +
					"<label for=\"connector-auth-type\">Auth Type</label>" +
					"<select id=\"connector-auth-type\">" +
						"<option value=\"none\">none</option>" +
						"<option value=\"bearer\">bearer</option>" +
						"<option value=\"basic\">basic</option>" +
					"</select>" +
					"<label for=\"connector-auth-token\">Bearer Token</label>" +
					"<input id=\"connector-auth-token\" type=\"password\" autocomplete=\"off\">" +
					"<label for=\"connector-auth-username\">Basic Username</label>" +
					"<input id=\"connector-auth-username\" type=\"text\">" +
					"<label for=\"connector-auth-password\">Basic Password</label>" +
					"<input id=\"connector-auth-password\" type=\"password\" autocomplete=\"off\">" +
					"<div class=\"inline-actions\">" +
						"<button type=\"submit\">저장/갱신</button>" +
						"<button type=\"button\" class=\"secondary\" id=\"connector-test-button\">연결 테스트</button>" +
					"</div>" +
				"</form>" +
				"<pre id=\"connectors-result\" class=\"preview preview-inline\">작업 결과가 여기에 표시됩니다.</pre>" +
			"</article>"
		);

		const authTypeSelect = queryInSection("#connector-auth-type");
		const tokenInput = queryInSection("#connector-auth-token");
		const usernameInput = queryInSection("#connector-auth-username");
		const passwordInput = queryInSection("#connector-auth-password");
		const form = queryInSection("#connector-upsert-form");
		const testButton = queryInSection("#connector-test-button");
		const resultBox = queryInSection("#connectors-result");

		function syncConnectorAuthFields() {
			const authType = authTypeSelect ? authTypeSelect.value : "none";
			const useBearer = authType === "bearer";
			const useBasic = authType === "basic";

			if (tokenInput) {
				tokenInput.disabled = !useBearer;
				tokenInput.required = useBearer;
			}
			if (usernameInput) {
				usernameInput.disabled = !useBasic;
				usernameInput.required = useBasic;
			}
			if (passwordInput) {
				passwordInput.disabled = !useBasic;
				passwordInput.required = useBasic;
			}
		}

		if (authTypeSelect) {
			authTypeSelect.addEventListener("change", syncConnectorAuthFields);
		}
		syncConnectorAuthFields();

		if (form) {
			form.addEventListener("submit", async (event) => {
				event.preventDefault();
				try {
					requireToken();
					const endpoint = valueOf("#connector-endpoint").trim();
					const tenant = valueOf("#connector-tenant").trim();
					const query = valueOf("#connector-query").trim();
					const pollInterval = valueOf("#connector-poll").trim();
					const authType = valueOf("#connector-auth-type").trim() || "none";

					const auth = { type: authType };
					if (authType === "bearer") {
						auth.token = valueOf("#connector-auth-token").trim();
					}
					if (authType === "basic") {
						auth.username = valueOf("#connector-auth-username").trim();
						auth.password = valueOf("#connector-auth-password").trim();
					}

					const payload = {
						endpoint,
						tenant_id: tenant || null,
						auth,
						query,
						poll_interval_seconds: pollInterval ? Number(pollInterval) : null
					};

					const response = await apiClient.upsertLokiConnector(projectId, payload);
					if (resultBox) {
						resultBox.textContent = formatJson(response);
					}
					setSectionFeedback("Loki 커넥터 설정이 저장되었습니다.", "success");
					setPreview(response);
				} catch (error) {
					handleSectionError("커넥터 설정 저장에 실패했습니다.", error);
				}
			});
		}

		if (testButton) {
			testButton.addEventListener("click", async () => {
				try {
					requireToken();
					const response = await apiClient.testLokiConnector(projectId);
					if (resultBox) {
						resultBox.textContent = formatJson(response);
					}
					setSectionFeedback("커넥터 테스트가 완료되었습니다.", "success");
					setPreview(response);
				} catch (error) {
					handleSectionError("커넥터 테스트에 실패했습니다.", error);
				}
			});
		}

		setSectionHint("커넥터 저장 후 테스트로 연결 상태를 점검하세요.");
	}

	function renderLlmSection() {
		const projectId = state.activeProjectId;
		if (!projectId) {
			renderProjectRequiredMessage("LLM 계정 관리");
			return;
		}

		setSectionBody(
			"<div class=\"workspace-grid\">" +
				"<article class=\"workspace-card\">" +
					"<h3>LLM API Key 계정</h3>" +
					"<form id=\"llm-api-key-form\" class=\"form-grid\">" +
						"<label for=\"llm-provider\">Provider</label>" +
						"<select id=\"llm-provider\">" +
							"<option value=\"openai\">openai</option>" +
							"<option value=\"gemini\">gemini</option>" +
						"</select>" +
						"<label for=\"llm-label\">Label (선택)</label>" +
						"<input id=\"llm-label\" type=\"text\" maxlength=\"80\" placeholder=\"production-openai\">" +
						"<label for=\"llm-api-key\">API Key</label>" +
						"<input id=\"llm-api-key\" type=\"password\" autocomplete=\"off\" required>" +
						"<label for=\"llm-model\">Model</label>" +
						"<input id=\"llm-model\" type=\"text\" value=\"gpt-4o-mini\" required>" +
						"<label for=\"llm-base-url\">Base URL (선택)</label>" +
						"<input id=\"llm-base-url\" type=\"url\" placeholder=\"https://api.openai.com\">" +
						"<div class=\"inline-actions\">" +
							"<button type=\"submit\">API Key 저장</button>" +
							"<button type=\"button\" class=\"secondary\" id=\"llm-refresh-button\">목록 새로고침</button>" +
						"</div>" +
					"</form>" +
					"<div class=\"inline-actions\">" +
						"<button type=\"button\" class=\"secondary oauth-start-button\" data-provider=\"openai\">OpenAI OAuth 시작</button>" +
						"<button type=\"button\" class=\"secondary oauth-start-button\" data-provider=\"gemini\">Gemini OAuth 시작</button>" +
					"</div>" +
					"<pre id=\"llm-result\" class=\"preview preview-inline\">작업 결과가 여기에 표시됩니다.</pre>" +
				"</article>" +
				"<article class=\"workspace-card\">" +
					"<h3>등록 계정</h3>" +
					renderLlmAccountsTable(state.llmAccounts) +
				"</article>" +
			"</div>"
		);

		const form = queryInSection("#llm-api-key-form");
		const refreshButton = queryInSection("#llm-refresh-button");
		const oauthButtons = Array.from(queryInSectionAll(".oauth-start-button"));
		const deleteButtons = Array.from(queryInSectionAll(".delete-llm-account"));
		const resultBox = queryInSection("#llm-result");

		if (form) {
			form.addEventListener("submit", async (event) => {
				event.preventDefault();
				try {
					requireToken();
					const payload = {
						provider: valueOf("#llm-provider").trim(),
						label: valueOf("#llm-label").trim() || null,
						api_key: valueOf("#llm-api-key").trim(),
						model: valueOf("#llm-model").trim(),
						base_url: valueOf("#llm-base-url").trim() || null
					};

					const response = await apiClient.upsertLlmApiKey(projectId, payload);
					await refreshLlmAccounts();
					if (resultBox) {
						resultBox.textContent = formatJson(response);
					}
					setSectionFeedback("LLM 계정이 저장되었습니다.", "success");
					setPreview(response);
					renderLlmSection();
				} catch (error) {
					handleSectionError("LLM 계정 저장에 실패했습니다.", error);
				}
			});
		}

		if (refreshButton) {
			refreshButton.addEventListener("click", async () => {
				try {
					const response = await refreshLlmAccounts();
					setSectionFeedback("LLM 계정 목록을 갱신했습니다.", "success");
					setPreview(response);
					renderLlmSection();
				} catch (error) {
					handleSectionError("LLM 계정 목록 조회에 실패했습니다.", error);
				}
			});
		}

		oauthButtons.forEach((button) => {
			button.addEventListener("click", async () => {
				const popup = window.open("", "_blank", "noopener,noreferrer");
				try {
					requireToken();
					const provider = button.getAttribute("data-provider") || "openai";
					const response = await apiClient.startLlmOAuth(projectId, provider);
					const masked = maskOAuthStartResult(response);
					const authUrl = validateOAuthAuthUrl(response && response.data ? response.data.auth_url : null);
					if (resultBox) {
						resultBox.textContent = formatJson(masked);
					}
					if (popup && !popup.closed) {
						popup.location.replace(authUrl);
						setSectionFeedback(provider + " OAuth 인증 페이지를 새 창으로 열었습니다.", "success");
					} else {
						setSectionFeedback(provider + " OAuth 팝업이 차단되어 URL을 미리보기로만 표시했습니다.", "error");
					}
					setPreview(masked);
				} catch (error) {
					if (popup && !popup.closed) {
						popup.close();
					}
					handleSectionError("OAuth 시작 URL 생성에 실패했습니다.", error);
				}
			});
		});

		deleteButtons.forEach((button) => {
			button.addEventListener("click", async () => {
				const accountId = button.getAttribute("data-account-id") || "";
				if (!accountId) {
					return;
				}
				try {
					requireToken();
					await apiClient.deleteLlmAccount(projectId, accountId);
					await refreshLlmAccounts();
					setSectionFeedback("LLM 계정을 삭제했습니다.", "success");
					setPreview({ deleted: accountId });
					renderLlmSection();
				} catch (error) {
					handleSectionError("LLM 계정 삭제에 실패했습니다.", error);
				}
			});
		});

		setSectionHint("API Key/OAuth 시작 후 목록에서 계정을 관리하세요.");
	}

	function renderPoliciesSection() {
		const projectId = state.activeProjectId;
		if (!projectId) {
			renderProjectRequiredMessage("정책 설정");
			return;
		}

		setSectionBody(
			"<div class=\"workspace-grid\">" +
				"<article class=\"workspace-card\">" +
					"<h3>Export 정책</h3>" +
					"<form id=\"export-policy-form\" class=\"form-grid\">" +
						"<label for=\"export-level\">Export Level</label>" +
						"<select id=\"export-level\">" +
							"<option value=\"level0_rule_only\">level0_rule_only</option>" +
							"<option value=\"level1_byom_only\" selected>level1_byom_only</option>" +
							"<option value=\"level2_byom_with_telemetry\">level2_byom_with_telemetry</option>" +
						"</select>" +
						"<div class=\"inline-actions\">" +
							"<button type=\"submit\">Export 정책 저장</button>" +
						"</div>" +
					"</form>" +
				"</article>" +
				"<article class=\"workspace-card\">" +
					"<h3>Redaction 정책</h3>" +
					"<form id=\"redaction-policy-form\" class=\"form-grid\">" +
						"<label class=\"checkbox-row\">" +
							"<input id=\"redaction-enabled\" type=\"checkbox\" checked>" +
							"<span>Redaction 활성화</span>" +
						"</label>" +
						"<label for=\"redaction-rules\">규칙 (한 줄당 name|pattern|replace_with)</label>" +
						"<textarea id=\"redaction-rules\" rows=\"5\" placeholder=\"api_key|api[_-]?key\\\s*[:=]\\\s*[^\\\\s]+|api_key=***\"></textarea>" +
						"<div class=\"inline-actions\">" +
							"<button type=\"submit\">Redaction 정책 저장</button>" +
						"</div>" +
					"</form>" +
					"<pre id=\"policies-result\" class=\"preview preview-inline\">작업 결과가 여기에 표시됩니다.</pre>" +
				"</article>" +
			"</div>"
		);

		const exportForm = queryInSection("#export-policy-form");
		const redactionForm = queryInSection("#redaction-policy-form");
		const resultBox = queryInSection("#policies-result");

		if (exportForm) {
			exportForm.addEventListener("submit", async (event) => {
				event.preventDefault();
				try {
					requireToken();
					const payload = {
						level: valueOf("#export-level")
					};
					const response = await apiClient.updateExportPolicy(projectId, payload);
					if (resultBox) {
						resultBox.textContent = formatJson(response);
					}
					setSectionFeedback("Export 정책을 저장했습니다.", "success");
					setPreview(response);
				} catch (error) {
					handleSectionError("Export 정책 저장에 실패했습니다.", error);
				}
			});
		}

		if (redactionForm) {
			redactionForm.addEventListener("submit", async (event) => {
				event.preventDefault();
				try {
					requireToken();
					const enabledInput = queryInSection("#redaction-enabled");
					const rulesText = valueOf("#redaction-rules");
					const payload = {
						enabled: Boolean(enabledInput && enabledInput.checked),
						rules: parseRedactionRules(rulesText)
					};
					const response = await apiClient.updateRedactionPolicy(projectId, payload);
					if (resultBox) {
						resultBox.textContent = formatJson(response);
					}
					setSectionFeedback("Redaction 정책을 저장했습니다.", "success");
					setPreview(response);
				} catch (error) {
					handleSectionError("Redaction 정책 저장에 실패했습니다.", error);
				}
			});
		}

		setSectionHint("정책 저장 시 결과가 즉시 표시됩니다.");
	}

	function renderAlertsSection() {
		const projectId = state.activeProjectId;
		if (!projectId) {
			renderProjectRequiredMessage("알림 설정");
			return;
		}

		setSectionBody(
			"<div class=\"workspace-grid\">" +
				"<article class=\"workspace-card\">" +
					"<h3>Slack 알림</h3>" +
					"<form id=\"slack-alert-form\" class=\"form-grid\">" +
						"<label for=\"slack-webhook\">Webhook URL</label>" +
						"<input id=\"slack-webhook\" type=\"url\" placeholder=\"https://hooks.slack.com/services/...\" required>" +
						"<label for=\"slack-channel\">Channel</label>" +
						"<input id=\"slack-channel\" type=\"text\" placeholder=\"#alerts\" required>" +
						"<label for=\"slack-min-confidence\">min_confidence</label>" +
						"<input id=\"slack-min-confidence\" type=\"number\" min=\"0\" max=\"1\" step=\"0.01\" value=\"0.7\">" +
						"<div class=\"inline-actions\">" +
							"<button type=\"submit\">Slack 설정 저장</button>" +
						"</div>" +
					"</form>" +
				"</article>" +
				"<article class=\"workspace-card\">" +
					"<h3>Email 알림</h3>" +
					"<form id=\"email-alert-form\" class=\"form-grid\">" +
						"<label for=\"email-from\">From</label>" +
						"<input id=\"email-from\" type=\"email\" placeholder=\"noreply@logcopilot.io\" required>" +
						"<label for=\"email-recipients\">Recipients (comma/newline)</label>" +
						"<textarea id=\"email-recipients\" rows=\"2\" placeholder=\"ops@logcopilot.io, sre@logcopilot.io\" required></textarea>" +
						"<label for=\"smtp-host\">SMTP Host</label>" +
						"<input id=\"smtp-host\" type=\"text\" placeholder=\"smtp.example.com\" required>" +
						"<label for=\"smtp-port\">SMTP Port</label>" +
						"<input id=\"smtp-port\" type=\"number\" min=\"1\" max=\"65535\" value=\"587\" required>" +
						"<label for=\"smtp-username\">SMTP Username</label>" +
						"<input id=\"smtp-username\" type=\"text\" required>" +
						"<label for=\"smtp-password\">SMTP Password</label>" +
						"<input id=\"smtp-password\" type=\"password\" autocomplete=\"off\" required>" +
						"<label class=\"checkbox-row\">" +
							"<input id=\"smtp-starttls\" type=\"checkbox\" checked>" +
							"<span>STARTTLS 사용</span>" +
						"</label>" +
						"<label for=\"email-min-confidence\">min_confidence</label>" +
						"<input id=\"email-min-confidence\" type=\"number\" min=\"0\" max=\"1\" step=\"0.01\" value=\"0.7\">" +
						"<div class=\"inline-actions\">" +
							"<button type=\"submit\">Email 설정 저장</button>" +
						"</div>" +
					"</form>" +
					"<pre id=\"alerts-result\" class=\"preview preview-inline\">작업 결과가 여기에 표시됩니다.</pre>" +
				"</article>" +
			"</div>"
		);

		const slackForm = queryInSection("#slack-alert-form");
		const emailForm = queryInSection("#email-alert-form");
		const resultBox = queryInSection("#alerts-result");

		if (slackForm) {
			slackForm.addEventListener("submit", async (event) => {
				event.preventDefault();
				try {
					requireToken();
					const payload = {
						webhook_url: valueOf("#slack-webhook").trim(),
						channel: valueOf("#slack-channel").trim(),
						min_confidence: parseFloatSafe(valueOf("#slack-min-confidence"))
					};
					const response = await apiClient.configureSlack(projectId, payload);
					if (resultBox) {
						resultBox.textContent = formatJson(response);
					}
					setSectionFeedback("Slack 알림 설정을 저장했습니다.", "success");
					setPreview(response);
				} catch (error) {
					handleSectionError("Slack 알림 설정에 실패했습니다.", error);
				}
			});
		}

		if (emailForm) {
			emailForm.addEventListener("submit", async (event) => {
				event.preventDefault();
				try {
					requireToken();
					const recipients = parseRecipients(valueOf("#email-recipients"));
					const payload = {
						from: valueOf("#email-from").trim(),
						recipients,
						smtp: {
							host: valueOf("#smtp-host").trim(),
							port: parseIntSafe(valueOf("#smtp-port")),
							username: valueOf("#smtp-username").trim(),
							password: valueOf("#smtp-password").trim(),
							starttls: isChecked("#smtp-starttls")
						},
						min_confidence: parseFloatSafe(valueOf("#email-min-confidence"))
					};
					const response = await apiClient.configureEmail(projectId, payload);
					if (resultBox) {
						resultBox.textContent = formatJson(response);
					}
					setSectionFeedback("Email 알림 설정을 저장했습니다.", "success");
					setPreview(response);
				} catch (error) {
					handleSectionError("Email 알림 설정에 실패했습니다.", error);
				}
			});
		}

		setSectionHint("Slack/Email 설정은 즉시 API에 반영됩니다.");
	}

	function renderIncidentsSection() {
		const projectId = state.activeProjectId;
		if (!projectId) {
			renderProjectRequiredMessage("인시던트 조회");
			return;
		}

		const filter = state.incidentFilter;
		const detail = state.selectedIncident;
		const nextCursor = state.incidentMeta && state.incidentMeta.next_cursor
			? state.incidentMeta.next_cursor
			: "없음";

		setSectionBody(
			"<article class=\"workspace-card\">" +
				"<h3>인시던트 목록</h3>" +
				"<form id=\"incident-filter-form\" class=\"form-grid form-grid-inline\">" +
					"<label for=\"incident-status\">status</label>" +
					"<select id=\"incident-status\">" +
						"<option value=\"\">(all)</option>" +
						"<option value=\"open\"" + selected(filter.status, "open") + ">open</option>" +
						"<option value=\"investigating\"" + selected(filter.status, "investigating") + ">investigating</option>" +
						"<option value=\"resolved\"" + selected(filter.status, "resolved") + ">resolved</option>" +
					"</select>" +
					"<label for=\"incident-service\">service</label>" +
					"<input id=\"incident-service\" type=\"text\" value=\"" + escapeHtml(filter.service) + "\" placeholder=\"api\">" +
					"<label for=\"incident-cursor\">cursor</label>" +
					"<input id=\"incident-cursor\" type=\"text\" value=\"" + escapeHtml(filter.cursor) + "\" placeholder=\"0\">" +
					"<label for=\"incident-limit\">limit</label>" +
					"<input id=\"incident-limit\" type=\"number\" min=\"1\" max=\"200\" value=\"" + escapeHtml(filter.limit) + "\">" +
					"<div class=\"inline-actions\">" +
						"<button type=\"submit\">목록 조회</button>" +
					"</div>" +
				"</form>" +
				"<p class=\"muted\">next cursor: <code>" + escapeHtml(nextCursor) + "</code></p>" +
				renderIncidentsTable(state.incidents) +
			"</article>" +
			"<article class=\"workspace-card\">" +
				"<h3>인시던트 상세 / 재분석</h3>" +
				(detail ?
					"<pre class=\"preview preview-inline\" id=\"incident-detail-preview\">" + escapeHtml(formatJson(detail)) + "</pre>" +
					"<form id=\"incident-reanalyze-form\" class=\"form-grid\">" +
						"<label for=\"reanalyze-reason\">reason (선택)</label>" +
						"<textarea id=\"reanalyze-reason\" rows=\"3\" maxlength=\"500\" placeholder=\"최근 장애 대응 후 재분석\"></textarea>" +
						"<div class=\"inline-actions\">" +
							"<button type=\"submit\">재분석 요청</button>" +
						"</div>" +
					"</form>"
					: "<p class=\"empty-state\">목록에서 인시던트를 선택하면 상세와 재분석 폼이 표시됩니다.</p>") +
			"</article>"
		);

		const filterForm = queryInSection("#incident-filter-form");
		const detailButtons = Array.from(queryInSectionAll(".incident-detail-button"));
		const reanalyzeForm = queryInSection("#incident-reanalyze-form");

		if (filterForm) {
			filterForm.addEventListener("submit", async (event) => {
				event.preventDefault();
				state.incidentFilter = {
					status: valueOf("#incident-status").trim(),
					service: valueOf("#incident-service").trim(),
					cursor: valueOf("#incident-cursor").trim(),
					limit: valueOf("#incident-limit").trim() || "50"
				};
				try {
					const response = await loadIncidents();
					setSectionFeedback("인시던트 목록을 조회했습니다.", "success");
					setPreview(response);
					renderIncidentsSection();
				} catch (error) {
					handleSectionError("인시던트 목록 조회에 실패했습니다.", error);
				}
			});
		}

		detailButtons.forEach((button) => {
			button.addEventListener("click", async () => {
				const incidentId = button.getAttribute("data-incident-id") || "";
				if (!incidentId) {
					return;
				}
				try {
					requireToken();
					const detailResponse = await apiClient.getIncident(incidentId);
					state.selectedIncident = detailResponse && detailResponse.data ? detailResponse.data : null;
					setSectionFeedback("인시던트 상세를 조회했습니다.", "success");
					setPreview(detailResponse);
					renderIncidentsSection();
				} catch (error) {
					handleSectionError("인시던트 상세 조회에 실패했습니다.", error);
				}
			});
		});

		if (reanalyzeForm && detail && detail.id) {
			reanalyzeForm.addEventListener("submit", async (event) => {
				event.preventDefault();
				try {
					requireToken();
					const reason = valueOf("#reanalyze-reason").trim();
					const payload = reason ? { reason } : {};
					const response = await apiClient.reanalyzeIncident(detail.id, payload);
					setSectionFeedback("재분석 요청이 접수되었습니다.", "success");
					setPreview(response);
					const refreshedDetail = await apiClient.getIncident(detail.id);
					state.selectedIncident = refreshedDetail && refreshedDetail.data ? refreshedDetail.data : state.selectedIncident;
					renderIncidentsSection();
				} catch (error) {
					handleSectionError("재분석 요청에 실패했습니다.", error);
				}
			});
		}

		setSectionHint("목록 조회 후 행의 '상세' 버튼으로 세부 정보/재분석을 진행하세요.");
	}

	function renderAuditSection() {
		const projectId = state.activeProjectId;
		if (!projectId) {
			renderProjectRequiredMessage("감사 로그 조회");
			return;
		}

		const filter = state.auditFilter;
		const nextCursor = state.auditMeta && state.auditMeta.next_cursor
			? state.auditMeta.next_cursor
			: "없음";

		setSectionBody(
			"<article class=\"workspace-card\">" +
				"<h3>감사 로그 조회</h3>" +
				"<form id=\"audit-query-form\" class=\"form-grid form-grid-inline\">" +
					"<label for=\"audit-action\">action</label>" +
					"<input id=\"audit-action\" type=\"text\" value=\"" + escapeHtml(filter.action) + "\" placeholder=\"alert.slack.configured\">" +
					"<label for=\"audit-actor\">actor</label>" +
					"<input id=\"audit-actor\" type=\"text\" value=\"" + escapeHtml(filter.actor) + "\" placeholder=\"ui-admin\">" +
					"<label for=\"audit-cursor\">cursor</label>" +
					"<input id=\"audit-cursor\" type=\"text\" value=\"" + escapeHtml(filter.cursor) + "\" placeholder=\"0\">" +
					"<label for=\"audit-limit\">limit</label>" +
					"<input id=\"audit-limit\" type=\"number\" min=\"1\" max=\"200\" value=\"" + escapeHtml(filter.limit) + "\">" +
					"<div class=\"inline-actions\">" +
						"<button type=\"submit\">감사 로그 조회</button>" +
					"</div>" +
				"</form>" +
				"<p class=\"muted\">next cursor: <code id=\"audit-next-cursor\">" + escapeHtml(nextCursor) + "</code></p>" +
				renderAuditLogsTable(state.auditLogs) +
			"</article>"
		);

		const queryForm = queryInSection("#audit-query-form");
		if (queryForm) {
			queryForm.addEventListener("submit", async (event) => {
				event.preventDefault();
				state.auditFilter = {
					action: valueOf("#audit-action").trim(),
					actor: valueOf("#audit-actor").trim(),
					cursor: valueOf("#audit-cursor").trim(),
					limit: valueOf("#audit-limit").trim() || "50"
				};
				try {
					const response = await loadAuditLogs();
					setSectionFeedback("감사 로그를 조회했습니다.", "success");
					setPreview(response);
					renderAuditSection();
				} catch (error) {
					handleSectionError("감사 로그 조회에 실패했습니다.", error);
				}
			});
		}

		setSectionHint("cursor/limit로 페이지를 이동할 수 있습니다.");
	}

	function renderProjectRequiredMessage(featureName) {
		setSectionBody(
			"<article class=\"workspace-card\">" +
				"<h3>" + escapeHtml(featureName) + "</h3>" +
				"<p class=\"empty-state\">활성 프로젝트가 없습니다. 프로젝트 화면에서 먼저 프로젝트를 선택해 주세요.</p>" +
				"<div class=\"inline-actions\">" +
					"<button type=\"button\" id=\"goto-projects-button\">프로젝트 화면으로 이동</button>" +
				"</div>" +
			"</article>"
		);
		const gotoProjectsButton = queryInSection("#goto-projects-button");
		if (gotoProjectsButton) {
			gotoProjectsButton.addEventListener("click", () => {
				activateNav("projects");
			});
		}
		setSectionFeedback("이 섹션은 프로젝트 선택이 필요합니다.", "error");
	}

	async function refreshCurrentSection() {
		if (!getStoredToken()) {
			if (state.activeNav === "overview" && !state.bootstrapStatus.bootstrapped) {
				try {
					const bootstrap = await refreshBootstrapStatus();
					renderOverviewSection();
					setPreview(bootstrap);
					setSectionFeedback("초기 bootstrap을 완료해 주세요.", "info");
					return;
				} catch (error) {
					handleSectionError("bootstrap 상태 조회에 실패했습니다.", error);
					return;
				}
			}
			setSectionFeedback("API 토큰을 먼저 설정해 주세요.", "error");
			setPreview("API 조회를 위해 토큰을 먼저 설정해 주세요.");
			return;
		}

		try {
			switch (state.activeNav) {
				case "overview": {
					const overview = await loadOverviewData();
					renderOverviewSection();
					setPreview(overview);
					break;
				}
				case "projects": {
					const projects = await refreshProjects();
					renderProjectsSection();
					setPreview(projects);
					break;
				}
				case "llm": {
					const llmData = await refreshLlmAccounts();
					renderLlmSection();
					setPreview(llmData);
					break;
				}
				case "incidents": {
					const incidents = await loadIncidents();
					renderIncidentsSection();
					setPreview(incidents);
					break;
				}
				case "audit": {
					const audits = await loadAuditLogs();
					renderAuditSection();
					setPreview(audits);
					break;
				}
				default:
					setPreview({
						status: "ok",
						message: "현재 섹션은 폼 액션으로 데이터를 조회/저장합니다.",
						section: state.activeNav,
						active_project_id: state.activeProjectId || null
					});
			}
			setSectionFeedback("최신 데이터를 반영했습니다.", "success");
		} catch (error) {
			handleSectionError("새로고침에 실패했습니다.", error);
		}
	}

	async function loadOverviewData() {
		const bootstrap = await refreshBootstrapStatus();
		if (!state.bootstrapStatus.bootstrapped) {
			return {
				bootstrap
			};
		}
		requireToken();
		const systemInfo = await apiClient.getSystemInfo();
		const projects = await refreshProjects();
		const tokens = await refreshTokens();
		return {
			bootstrap,
			systemInfo,
			projects,
			tokens,
			active_project_id: state.activeProjectId || null
		};
	}

	async function refreshBootstrapStatus() {
		const response = await apiClient.getBootstrapStatus();
		const data = response && response.data ? response.data : {};
		state.bootstrapStatus = {
			bootstrapped: Boolean(data.bootstrapped),
			initialized_at: data.initialized_at || null
		};
		return response;
	}

	async function refreshProjects() {
		requireToken();
		const projects = await apiClient.listProjects();
		state.projects = projects && Array.isArray(projects.data) ? projects.data : [];
		reconcileActiveProject();
		renderActiveProject();
		return projects;
	}

	async function refreshTokens() {
		requireToken();
		const response = await apiClient.listTokens();
		state.tokens = response && Array.isArray(response.data) ? response.data : [];
		return response;
	}

	function reconcileActiveProject() {
		if (state.projects.length === 0) {
			updateActiveProject("");
			return;
		}
		const exists = state.projects.some((project) => project && project.id === state.activeProjectId);
		if (!exists) {
			updateActiveProject(state.projects[0] && state.projects[0].id ? state.projects[0].id : "");
		}
	}

	async function refreshLlmAccounts() {
		requireToken();
		const projectId = requireActiveProject();
		const response = await apiClient.listLlmAccounts(projectId);
		state.llmAccounts = response && Array.isArray(response.data) ? response.data : [];
		return response;
	}

	async function loadIncidents() {
		requireToken();
		const projectId = requireActiveProject();
		const filter = state.incidentFilter;
		const query = {};
		if (filter.status) {
			query.status = filter.status;
		}
		if (filter.service) {
			query.service = filter.service;
		}
		if (filter.cursor) {
			query.cursor = filter.cursor;
		}
		if (filter.limit) {
			query.limit = parseIntSafe(filter.limit);
		}

		const response = await apiClient.listIncidents(projectId, query);
		state.incidents = response && Array.isArray(response.data) ? response.data : [];
		state.incidentMeta = response && response.meta ? response.meta : null;
		state.selectedIncident = null;
		return response;
	}

	async function loadAuditLogs() {
		requireToken();
		const projectId = requireActiveProject();
		const filter = state.auditFilter;
		const query = {};
		if (filter.action) {
			query.action = filter.action;
		}
		if (filter.actor) {
			query.actor = filter.actor;
		}
		if (filter.cursor) {
			query.cursor = filter.cursor;
		}
		if (filter.limit) {
			query.limit = parseIntSafe(filter.limit);
		}

		const response = await apiClient.listAuditLogs(projectId, query);
		state.auditLogs = response && Array.isArray(response.data) ? response.data : [];
		state.auditMeta = response && response.meta ? response.meta : null;
		return response;
	}

	function renderProjectListTable(projects, compact) {
		if (!projects || projects.length === 0) {
			return "<p class=\"empty-state\">아직 프로젝트가 없습니다.</p>";
		}

		const rows = projects.map((project) => {
			const id = safeString(project && project.id);
			const isActive = id && id === state.activeProjectId;
			const actionButton = compact
				? ""
				: "<button type=\"button\" class=\"secondary select-project-button\" data-project-id=\"" +
					escapeHtml(id) +
					"\">" +
					(isActive ? "선택됨" : "활성화") +
					"</button>";

			return "<tr>" +
				"<td><code>" + escapeHtml(id) + "</code></td>" +
				"<td>" + escapeHtml(safeString(project && project.name)) + "</td>" +
				"<td>" + escapeHtml(safeString(project && project.environment)) + "</td>" +
				"<td>" + escapeHtml(formatDateTime(project && project.created_at)) + "</td>" +
				"<td>" + (isActive ? "<span class=\"pill\">활성</span>" : actionButton) + "</td>" +
			"</tr>";
		}).join("");

		return "<div class=\"table-wrap\"><table class=\"data-table\">" +
			"<thead><tr><th>ID</th><th>Name</th><th>Env</th><th>Created</th><th>Action</th></tr></thead>" +
			"<tbody>" + rows + "</tbody>" +
		"</table></div>";
	}

	function renderTokensTable(tokens) {
		if (!tokens || tokens.length === 0) {
			return "<p class=\"empty-state\">등록된 토큰이 없습니다.</p>";
		}

		const rows = tokens.map((token) => {
			const id = safeString(token && token.id);
			const status = safeString(token && token.status);
			const canManage = status === "active";
			return "<tr>" +
				"<td><code>" + escapeHtml(id) + "</code></td>" +
				"<td>" + escapeHtml(safeString(token && token.name)) + "</td>" +
				"<td>" + escapeHtml(safeString(token && token.role)) + "</td>" +
				"<td>" + escapeHtml(status) + "</td>" +
				"<td>" + escapeHtml(formatDateTime(token && token.created_at)) + "</td>" +
				"<td>" + escapeHtml(formatDateTime(token && token.rotated_at)) + "</td>" +
				"<td>" + escapeHtml(formatDateTime(token && token.revoked_at)) + "</td>" +
				"<td>" +
					(canManage
						? "<button type=\"button\" class=\"secondary rotate-token-button\" data-token-id=\"" + escapeHtml(id) + "\">회전</button>"
						: "-") +
					(canManage
						? " <button type=\"button\" class=\"secondary revoke-token-button\" data-token-id=\"" + escapeHtml(id) + "\">폐기</button>"
						: "") +
				"</td>" +
			"</tr>";
		}).join("");

		return "<div class=\"table-wrap\"><table class=\"data-table\">" +
			"<thead><tr><th>ID</th><th>Name</th><th>Role</th><th>Status</th><th>Created</th><th>Rotated</th><th>Revoked</th><th>Action</th></tr></thead>" +
			"<tbody>" + rows + "</tbody>" +
		"</table></div>";
	}

	function renderLlmAccountsTable(accounts) {
		if (!accounts || accounts.length === 0) {
			return "<p class=\"empty-state\">등록된 LLM 계정이 없습니다.</p>";
		}

		const rows = accounts.map((account) => {
			const id = safeString(account && account.id);
			return "<tr>" +
				"<td><code>" + escapeHtml(id) + "</code></td>" +
				"<td>" + escapeHtml(safeString(account && account.provider)) + "</td>" +
				"<td>" + escapeHtml(safeString(account && account.auth_type)) + "</td>" +
				"<td>" + escapeHtml(safeString(account && account.model)) + "</td>" +
				"<td>" + escapeHtml(safeString(account && account.status)) + "</td>" +
				"<td><button type=\"button\" class=\"secondary delete-llm-account\" data-account-id=\"" +
					escapeHtml(id) +
					"\">삭제</button></td>" +
			"</tr>";
		}).join("");

		return "<div class=\"table-wrap\"><table class=\"data-table\">" +
			"<thead><tr><th>ID</th><th>Provider</th><th>Auth</th><th>Model</th><th>Status</th><th>Action</th></tr></thead>" +
			"<tbody>" + rows + "</tbody>" +
		"</table></div>";
	}

	function renderIncidentsTable(incidents) {
		if (!incidents || incidents.length === 0) {
			return "<p class=\"empty-state\">조회된 인시던트가 없습니다.</p>";
		}

		const rows = incidents.map((incident) => {
			const id = safeString(incident && incident.id);
			return "<tr>" +
				"<td><code>" + escapeHtml(id) + "</code></td>" +
				"<td>" + escapeHtml(safeString(incident && incident.status)) + "</td>" +
				"<td>" + escapeHtml(safeString(incident && incident.service)) + "</td>" +
				"<td>" + escapeHtml(String(incident && incident.severity_score != null ? incident.severity_score : "-")) + "</td>" +
				"<td>" + escapeHtml(String(incident && incident.event_count != null ? incident.event_count : "-")) + "</td>" +
				"<td><button type=\"button\" class=\"secondary incident-detail-button\" data-incident-id=\"" +
					escapeHtml(id) +
					"\">상세</button></td>" +
			"</tr>";
		}).join("");

		return "<div class=\"table-wrap\"><table class=\"data-table\">" +
			"<thead><tr><th>ID</th><th>Status</th><th>Service</th><th>Severity</th><th>Events</th><th>Action</th></tr></thead>" +
			"<tbody>" + rows + "</tbody>" +
		"</table></div>";
	}

	function renderAuditLogsTable(logs) {
		if (!logs || logs.length === 0) {
			return "<p class=\"empty-state\">조회된 감사 로그가 없습니다.</p>";
		}

		const rows = logs.map((log) => {
			const metadata = log && log.metadata ? formatJson(log.metadata) : "{}";
			return "<tr>" +
				"<td><code>" + escapeHtml(safeString(log && log.id)) + "</code></td>" +
				"<td>" + escapeHtml(safeString(log && log.actor)) + "</td>" +
				"<td>" + escapeHtml(safeString(log && log.action)) + "</td>" +
				"<td>" + escapeHtml(safeString(log && log.resource_type)) + "</td>" +
				"<td>" + escapeHtml(safeString(log && log.resource_id)) + "</td>" +
				"<td>" + escapeHtml(formatDateTime(log && log.created_at)) + "</td>" +
				"<td><pre class=\"table-pre\">" + escapeHtml(metadata) + "</pre></td>" +
			"</tr>";
		}).join("");

		return "<div class=\"table-wrap\"><table class=\"data-table\">" +
			"<thead><tr><th>ID</th><th>Actor</th><th>Action</th><th>Type</th><th>Resource</th><th>Created</th><th>Metadata</th></tr></thead>" +
			"<tbody>" + rows + "</tbody>" +
		"</table></div>";
	}

	function parseRedactionRules(text) {
		const lines = text
			.split(/\r?\n/)
			.map((line) => line.trim())
			.filter((line) => line.length > 0);
		if (lines.length === 0) {
			return [];
		}
		return lines.map((line, index) => {
			const firstDelimiterIndex = line.indexOf("|");
			const lastDelimiterIndex = line.lastIndexOf("|");
			if (firstDelimiterIndex < 0 || lastDelimiterIndex <= firstDelimiterIndex) {
				throw new Error("redaction rules 형식 오류 (line " + (index + 1) + "): name|pattern|replace_with");
			}
			const name = line.slice(0, firstDelimiterIndex).trim();
			const pattern = line.slice(firstDelimiterIndex + 1, lastDelimiterIndex).trim();
			const replaceWith = line.slice(lastDelimiterIndex + 1).trim();
			if (!name || !pattern || !replaceWith) {
				throw new Error("redaction rules 형식 오류 (line " + (index + 1) + "): name|pattern|replace_with");
			}
			return {
				name,
				pattern,
				replace_with: replaceWith
			};
		});
	}

	function parseRecipients(raw) {
		const recipients = raw
			.split(/[\n,]/)
			.map((value) => value.trim())
			.filter((value) => value.length > 0);
		if (recipients.length === 0) {
			throw new Error("수신 이메일을 하나 이상 입력해 주세요.");
		}
		return recipients;
	}

	function requireToken() {
		if (!state.bootstrapStatus.bootstrapped) {
			throw new Error("초기 bootstrap을 먼저 완료해 주세요.");
		}
		if (!getStoredToken()) {
			throw new Error("API 토큰을 먼저 설정해 주세요.");
		}
	}

	function requireActiveProject() {
		if (!state.activeProjectId) {
			throw new Error("활성 프로젝트를 먼저 선택해 주세요.");
		}
		return state.activeProjectId;
	}

	function valueOf(selector) {
		const target = queryInSection(selector);
		if (!target) {
			return "";
		}
		return typeof target.value === "string" ? target.value : "";
	}

	function isChecked(selector) {
		const target = queryInSection(selector);
		return Boolean(target && target.checked);
	}

	function queryInSection(selector) {
		if (!elements.sectionBody) {
			return null;
		}
		return elements.sectionBody.querySelector(selector);
	}

	function queryInSectionAll(selector) {
		if (!elements.sectionBody) {
			return [];
		}
		return elements.sectionBody.querySelectorAll(selector);
	}

	function selected(value, expected) {
		return value === expected ? " selected" : "";
	}

	function resetProjectScopedState() {
		state.llmAccounts = [];
		state.incidents = [];
		state.incidentMeta = null;
		state.selectedIncident = null;
		state.auditLogs = [];
		state.auditMeta = null;
	}

	function updateActiveProject(projectId) {
		const nextProjectId = projectId || "";
		if (state.activeProjectId === nextProjectId) {
			return;
		}
		state.activeProjectId = nextProjectId;
		resetProjectScopedState();
	}

	function renderActiveProject() {
		elements.activeProjectId.textContent = state.activeProjectId || "없음";
	}

	function updateAuthStatus() {
		if (!state.bootstrapStatus.bootstrapped) {
			elements.authStatus.textContent = "초기 bootstrap이 필요합니다. bootstrap 완료 시 운영자 토큰이 자동 설정됩니다.";
			elements.authStatus.style.color = "#6a5a47";
			return;
		}
		if (getStoredToken()) {
			elements.authStatus.textContent = "토큰이 메모리에 설정되었습니다. API 요청 시 Authorization 헤더를 사용합니다.";
			elements.authStatus.style.color = "#1f5c47";
			return;
		}
		elements.authStatus.textContent = "토큰이 아직 설정되지 않았습니다. 새로고침 시 입력값은 유지되지 않습니다.";
		elements.authStatus.style.color = "#6a5a47";
	}

	function setSectionBody(html) {
		elements.sectionBody.innerHTML = html;
	}

	function setSectionFeedback(message, type) {
		elements.sectionFeedback.textContent = message;
		elements.sectionFeedback.classList.remove("feedback-info", "feedback-success", "feedback-error");
		if (type === "success") {
			elements.sectionFeedback.classList.add("feedback-success");
			return;
		}
		if (type === "error") {
			elements.sectionFeedback.classList.add("feedback-error");
			return;
		}
		elements.sectionFeedback.classList.add("feedback-info");
	}

	function setSectionHint(message) {
		if (elements.sectionFeedback.classList.contains("feedback-success")
			|| elements.sectionFeedback.classList.contains("feedback-error")) {
			return;
		}
		setSectionFeedback(message, "info");
	}

	function setPreview(payload) {
		if (typeof payload === "string") {
			elements.preview.textContent = payload;
			return;
		}
		elements.preview.textContent = formatJson(payload);
	}

	function handleSectionError(prefix, error) {
		const message = prefix + " " + errorMessage(error);
		setSectionFeedback(message, "error");
		setPreview({
			status: "error",
			message
		});
		showToast(message);
	}

	function errorMessage(error) {
		if (!error) {
			return "알 수 없는 오류";
		}
		if (error.message) {
			return error.message;
		}
		return String(error);
	}

	function showToast(message) {
		if (!elements.toast) {
			return;
		}
		if (state.toastTimer) {
			window.clearTimeout(state.toastTimer);
		}
		elements.toast.textContent = message;
		elements.toast.classList.add("visible");
		state.toastTimer = window.setTimeout(() => {
			elements.toast.classList.remove("visible");
			state.toastTimer = null;
		}, 2600);
	}

	function getStoredToken() {
		return state.token;
	}

	function parseIntSafe(value) {
		const parsed = Number.parseInt(value, 10);
		if (Number.isNaN(parsed)) {
			return null;
		}
		return parsed;
	}

	function parseFloatSafe(value) {
		const parsed = Number.parseFloat(value);
		if (Number.isNaN(parsed)) {
			return null;
		}
		return parsed;
	}

	function safeString(value) {
		if (value === undefined || value === null) {
			return "";
		}
		return String(value);
	}

	function formatDateTime(value) {
		if (!value) {
			return "-";
		}
		const date = new Date(value);
		if (Number.isNaN(date.getTime())) {
			return String(value);
		}
		return date.toLocaleString("ko-KR", {
			year: "numeric",
			month: "2-digit",
			day: "2-digit",
			hour: "2-digit",
			minute: "2-digit",
			second: "2-digit",
			hour12: false
		});
	}

	function formatJson(payload) {
		try {
			return JSON.stringify(payload, null, 2);
		} catch (_error) {
			return String(payload);
		}
	}

	function maskOAuthStartResult(response) {
		if (!response || !response.data) {
			return response;
		}
		const data = response.data;
		return {
			...response,
			data: {
				...data,
				state: maskValue(data.state)
			}
		};
	}

	function maskValue(value) {
		if (!value) {
			return value;
		}
		const text = String(value);
		if (text.length <= 8) {
			return "****";
		}
		return text.slice(0, 4) + "..." + text.slice(-4);
	}

	function validateOAuthAuthUrl(urlValue) {
		if (!urlValue) {
			throw new Error("OAuth 시작 URL이 비어 있습니다.");
		}
		let parsed;
		try {
			parsed = new URL(urlValue);
		} catch (_error) {
			throw new Error("OAuth 시작 URL 형식이 올바르지 않습니다.");
		}
		const protocol = parsed.protocol.toLowerCase();
		if (protocol !== "http:" && protocol !== "https:") {
			throw new Error("OAuth 시작 URL은 http/https 스킴이어야 합니다.");
		}
		return parsed.toString();
	}

	function escapeHtml(value) {
		return String(value)
			.replace(/&/g, "&amp;")
			.replace(/</g, "&lt;")
			.replace(/>/g, "&gt;")
			.replace(/\"/g, "&quot;")
			.replace(/'/g, "&#39;");
	}

	function createApiClient(getToken) {
		return {
			getBootstrapStatus: () => request("/v1/bootstrap/status"),
			initializeBootstrap: (payload) => request("/v1/bootstrap/initialize", { method: "POST", body: payload }),
			getSystemInfo: () => request("/v1/system/info"),
			listProjects: () => request("/v1/projects"),
			createProject: (payload) => request("/v1/projects", { method: "POST", body: payload }),
			listTokens: () => request("/v1/tokens"),
			issueToken: (payload) => request("/v1/tokens", { method: "POST", body: payload }),
			rotateToken: (tokenId) => request("/v1/tokens/" + encode(tokenId) + "/rotate", { method: "POST" }),
			revokeToken: (tokenId, payload) => request(
				"/v1/tokens/" + encode(tokenId) + "/revoke",
				{ method: "POST", body: payload }
			),
			upsertLokiConnector: (projectId, payload) => request(
				"/v1/projects/" + encode(projectId) + "/connectors/loki",
				{ method: "POST", body: payload }
			),
			testLokiConnector: (projectId) => request(
				"/v1/projects/" + encode(projectId) + "/connectors/loki/test",
				{ method: "POST" }
			),
			upsertLlmApiKey: (projectId, payload) => request(
				"/v1/projects/" + encode(projectId) + "/llm-accounts/api-key",
				{ method: "POST", body: payload }
			),
			startLlmOAuth: (projectId, provider) => request(
				"/v1/projects/" + encode(projectId) + "/llm-oauth/" + encode(provider) + "/start",
				{ method: "POST" }
			),
			listLlmAccounts: (projectId) => request("/v1/projects/" + encode(projectId) + "/llm-accounts"),
			deleteLlmAccount: (projectId, accountId) => request(
				"/v1/projects/" + encode(projectId) + "/llm-accounts/" + encode(accountId),
				{ method: "DELETE" }
			),
			updateExportPolicy: (projectId, payload) => request(
				"/v1/projects/" + encode(projectId) + "/policies/export",
				{ method: "PUT", body: payload }
			),
			updateRedactionPolicy: (projectId, payload) => request(
				"/v1/projects/" + encode(projectId) + "/policies/redaction",
				{ method: "PUT", body: payload }
			),
			configureSlack: (projectId, payload) => request(
				"/v1/projects/" + encode(projectId) + "/alerts/slack",
				{ method: "POST", body: payload }
			),
			configureEmail: (projectId, payload) => request(
				"/v1/projects/" + encode(projectId) + "/alerts/email",
				{ method: "POST", body: payload }
			),
			listIncidents: (projectId, query) => request(
				"/v1/projects/" + encode(projectId) + "/incidents",
				{ query }
			),
			getIncident: (incidentId) => request("/v1/incidents/" + encode(incidentId)),
			reanalyzeIncident: (incidentId, payload) => request(
				"/v1/incidents/" + encode(incidentId) + "/reanalyze",
				{ method: "POST", body: payload }
			),
			listAuditLogs: (projectId, query) => request(
				"/v1/projects/" + encode(projectId) + "/audit-logs",
				{ query }
			)
		};

		function request(path, options) {
			const config = options || {};
			const token = getToken();
			const hasBody = config.body !== undefined && config.body !== null;
			const headers = {
				"Accept": "application/json",
				...config.headers
			};
			if (hasBody) {
				headers["Content-Type"] = "application/json";
			}
			if (token) {
				headers.Authorization = "Bearer " + token;
			}

			const responsePath = appendQuery(path, config.query);
			return fetch(responsePath, {
				method: config.method || "GET",
				headers,
				body: hasBody ? JSON.stringify(config.body) : undefined
			}).then(async (response) => {
				const text = await response.text();
				const payload = parseJson(text);
				if (!response.ok) {
					throw normalizeApiError(response.status, payload);
				}
				return payload;
			});
		}
	}

	function appendQuery(path, query) {
		if (!query) {
			return path;
		}
		const params = new URLSearchParams();
		Object.keys(query).forEach((key) => {
			const value = query[key];
			if (value === undefined || value === null || value === "") {
				return;
			}
			params.set(key, String(value));
		});
		const queryString = params.toString();
		if (!queryString) {
			return path;
		}
		return path + "?" + queryString;
	}

	function encode(value) {
		return encodeURIComponent(String(value));
	}

	function parseJson(text) {
		if (!text) {
			return null;
		}
		try {
			return JSON.parse(text);
		} catch (_error) {
			return { raw: text };
		}
	}

	function normalizeApiError(status, payload) {
		const errorObject = payload && payload.error ? payload.error : null;
		const code = errorObject && errorObject.code ? errorObject.code : "http_" + status;
		const message = errorObject && errorObject.message
			? errorObject.message
			: "HTTP " + status + " 요청 실패";
		return new Error(code + ": " + message);
	}
})();
