(function () {
	"use strict";

	const SECTION_TEXT = {
		overview: {
			title: "개요",
			description: "시스템 상태와 프로젝트 목록을 빠르게 확인합니다."
		},
		projects: {
			title: "프로젝트",
			description: "프로젝트 생성/조회 화면은 T-20에서 완성됩니다."
		},
		connectors: {
			title: "커넥터",
			description: "Loki 커넥터 설정/테스트 흐름을 준비 중입니다."
		},
		llm: {
			title: "LLM 계정",
			description: "OpenAI/Gemini 계정 관리 흐름을 준비 중입니다."
		},
		policies: {
			title: "정책",
			description: "Export/Redaction 정책 화면을 준비 중입니다."
		},
		alerts: {
			title: "알림",
			description: "Slack/Email 알림 설정 흐름을 준비 중입니다."
		},
		incidents: {
			title: "인시던트",
			description: "인시던트 목록/상세/재분석 화면을 준비 중입니다."
		},
		audit: {
			title: "감사 로그",
			description: "감사 로그 조회 화면을 준비 중입니다."
		}
	};

	const state = {
		activeNav: "overview",
		token: "",
		toastTimer: null
	};

	const elements = {
		navItems: Array.from(document.querySelectorAll("[data-nav]")),
		tokenInput: document.getElementById("api-token-input"),
		connectButton: document.getElementById("connect-button"),
		clearButton: document.getElementById("clear-button"),
		refreshButton: document.getElementById("refresh-button"),
		sectionTitle: document.getElementById("section-title"),
		sectionDescription: document.getElementById("section-description"),
		authStatus: document.getElementById("auth-status"),
		preview: document.getElementById("response-preview"),
		toast: document.getElementById("toast")
	};

	const apiClient = createApiClient(getStoredToken);

	wireEvents();
	bootstrap();

	function bootstrap() {
		updateAuthStatus();
		activateNav(state.activeNav);
	}

	function wireEvents() {
		elements.navItems.forEach((item) => {
			item.addEventListener("click", () => {
				activateNav(item.dataset.nav || "overview");
				loadPreview();
			});
		});

		elements.connectButton.addEventListener("click", async () => {
			const token = elements.tokenInput.value.trim();
			if (!token) {
				showToast("API 토큰을 입력해 주세요.");
				return;
			}
			state.token = token;
			updateAuthStatus();
			await loadPreview();
		});

		elements.clearButton.addEventListener("click", () => {
			state.token = "";
			elements.tokenInput.value = "";
			elements.preview.textContent = "토큰이 제거되었습니다.";
			updateAuthStatus();
		});

		elements.refreshButton.addEventListener("click", () => loadPreview());
	}

	function activateNav(nav) {
		state.activeNav = nav;
		elements.navItems.forEach((item) => {
			item.classList.toggle("active", item.dataset.nav === nav);
		});
		const section = SECTION_TEXT[nav] || SECTION_TEXT.overview;
		elements.sectionTitle.textContent = section.title;
		elements.sectionDescription.textContent = section.description;
	}

	function updateAuthStatus() {
		if (getStoredToken()) {
			elements.authStatus.textContent = "토큰이 메모리에 설정되었습니다. API 요청 시 Authorization 헤더를 사용합니다.";
			elements.authStatus.style.color = "#1f5c47";
			return;
		}
		elements.authStatus.textContent = "토큰이 아직 설정되지 않았습니다. 새로고침 시 입력값은 유지되지 않습니다.";
		elements.authStatus.style.color = "#6a5a47";
	}

	async function loadPreview() {
		const token = getStoredToken();
		if (!token) {
			elements.preview.textContent = "API 조회를 위해 토큰을 먼저 설정해 주세요.";
			return;
		}

		elements.preview.textContent = "조회 중...";
		try {
			const data = await resolvePreviewData();
			elements.preview.textContent = JSON.stringify(data, null, 2);
		} catch (error) {
			const message = error && error.message ? error.message : "알 수 없는 오류";
			elements.preview.textContent = "요청 실패: " + message;
			showToast(message);
		}
	}

	async function resolvePreviewData() {
		switch (state.activeNav) {
			case "overview":
				return Promise.all([apiClient.getSystemInfo(), apiClient.listProjects()])
					.then(([systemInfo, projects]) => ({ systemInfo, projects }));
			case "projects":
				return apiClient.listProjects();
			case "connectors":
				return placeholderPayload("POST /v1/projects/{project_id}/connectors/loki");
			case "llm":
				return placeholderPayload("POST /v1/projects/{project_id}/llm-accounts/api-key");
			case "policies":
				return placeholderPayload("PUT /v1/projects/{project_id}/policies/*");
			case "alerts":
				return placeholderPayload("POST /v1/projects/{project_id}/alerts/*");
			case "incidents":
				return placeholderPayload("GET /v1/projects/{project_id}/incidents");
			case "audit":
				return placeholderPayload("GET /v1/projects/{project_id}/audit-logs");
			default:
				return placeholderPayload("Unknown section");
		}
	}

	function placeholderPayload(endpoint) {
		return {
			status: "planned",
			message: "T-20에서 상세 UI를 연결합니다.",
			endpoint
		};
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

	function createApiClient(getToken) {
		return {
			getSystemInfo: () => request("/v1/system/info"),
			listProjects: () => request("/v1/projects")
		};

		async function request(path, options) {
			const config = options || {};
			const token = getToken();
			const headers = {
				"Accept": "application/json",
				...config.headers
			};
			if (config.body !== undefined && config.body !== null) {
				headers["Content-Type"] = "application/json";
			}
			if (token) {
				headers.Authorization = "Bearer " + token;
			}

			const response = await fetch(path, {
				method: config.method || "GET",
				headers,
				body: config.body ? JSON.stringify(config.body) : undefined
			});

			const text = await response.text();
			const payload = parseJson(text);

			if (!response.ok) {
				throw normalizeApiError(response.status, payload);
			}
			return payload;
		}
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
