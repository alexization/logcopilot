const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const APP_JS_PATH = path.resolve(__dirname, "../../main/resources/static/admin/app.js");
const APP_JS_SOURCE = fs.readFileSync(APP_JS_PATH, "utf8");
const BOOTSTRAP_MARKER = "\tconst apiClient = createApiClient(getStoredToken);\n\n\twireGlobalEvents();\n\tbootstrap();";
const INSTRUMENTED_SOURCE = APP_JS_SOURCE.replace(
	BOOTSTRAP_MARKER,
	"\tconst apiClient = createApiClient(getStoredToken);\n\n"
		+ "\twindow.__adminUiTestHooks = {\n"
		+ "\t\tstate,\n"
		+ "\t\telements,\n"
		+ "\t\tactivateNav,\n"
		+ "\t\tclearCachedData,\n"
		+ "\t\tprimeAuthenticatedContext,\n"
		+ "\t\tcommitAuthenticatedContext,\n"
		+ "\t\trefreshCurrentSection,\n"
		+ "\t\tcreateRefreshContextSnapshot,\n"
		+ "\t\tisRefreshContextCurrent,\n"
		+ "\t\tcreateSectionContextSnapshot,\n"
		+ "\t\tisSectionContextCurrent,\n"
		+ "\t\trenderConnectorsSection,\n"
		+ "\t\trenderAlertsSection,\n"
		+ "\t\tupdateActiveProject\n"
		+ "\t};\n\n"
		+ "\twireGlobalEvents();"
);

if (INSTRUMENTED_SOURCE === APP_JS_SOURCE) {
	throw new Error("Failed to instrument admin/app.js for runtime tests.");
}

class FakeClassList {
	constructor() {
		this.values = new Set();
	}

	add(...tokens) {
		tokens.forEach((token) => this.values.add(token));
	}

	remove(...tokens) {
		tokens.forEach((token) => this.values.delete(token));
	}

	contains(token) {
		return this.values.has(token);
	}

	toggle(token, force) {
		if (force === true) {
			this.values.add(token);
			return true;
		}
		if (force === false) {
			this.values.delete(token);
			return false;
		}
		if (this.values.has(token)) {
			this.values.delete(token);
			return false;
		}
		this.values.add(token);
		return true;
	}
}

class FakeElement {
	constructor(id, tagName) {
		this.id = id || "";
		this.tagName = (tagName || "div").toUpperCase();
		this.dataset = {};
		this.style = {};
		this.classList = new FakeClassList();
		this.listeners = {};
		this.value = "";
		this.textContent = "";
		this.checked = false;
		this.disabled = false;
		this.required = false;
		this.placeholder = "";
	}

	addEventListener(type, handler) {
		if (!this.listeners[type]) {
			this.listeners[type] = [];
		}
		this.listeners[type].push(handler);
	}

	getAttribute(name) {
		if (name === "data-nav") {
			return this.dataset.nav || null;
		}
		return null;
	}

	async fire(type, extra) {
		const handlers = this.listeners[type] || [];
		const event = {
			type,
			target: this,
			currentTarget: this,
			preventDefault() {},
			...extra
		};
		for (const handler of handlers) {
			await handler(event);
		}
	}
}

class FakeSectionBody extends FakeElement {
	constructor() {
		super("section-body", "div");
		this.childrenById = {};
		this._innerHTML = "";
	}

	get innerHTML() {
		return this._innerHTML;
	}

	set innerHTML(html) {
		this._innerHTML = html;
		this.childrenById = {};
		this.parseChildren(html);
	}

	parseChildren(html) {
		const openTagRegex = /<([a-z]+)([^>]*)id="([^"]+)"([^>]*)>/gi;
		let match;
		while ((match = openTagRegex.exec(html)) !== null) {
			const tagName = match[1];
			const attributeSource = (match[2] || "") + " " + (match[4] || "");
			const element = new FakeElement(match[3], tagName);
			element.value = readAttribute(attributeSource, "value");
			element.placeholder = readAttribute(attributeSource, "placeholder");
			element.checked = /\schecked(?:\s|>|$)/.test(attributeSource);
			element.disabled = /\sdisabled(?:\s|>|$)/.test(attributeSource);
			element.required = /\srequired(?:\s|>|$)/.test(attributeSource);
			if (element.tagName === "TEXTAREA") {
				element.value = readTagBody(html, tagName, match[3]);
			}
			if (element.tagName === "SELECT") {
				element.value = readSelectedOptionValue(html, match[3]);
			}
			this.childrenById[element.id] = element;
		}
	}

	querySelector(selector) {
		if (selector.startsWith("#")) {
			return this.childrenById[selector.slice(1)] || null;
		}
		return null;
	}

	querySelectorAll(selector) {
		if (selector.startsWith("#")) {
			const element = this.querySelector(selector);
			return element ? [element] : [];
		}
		return [];
	}

	contains(element) {
		return Object.values(this.childrenById).includes(element);
	}
}

function readAttribute(source, name) {
	const match = source.match(new RegExp(name + "=\"([^\"]*)\"", "i"));
	return match ? decodeHtml(match[1]) : "";
}

function readTagBody(html, tagName, id) {
	const pattern = new RegExp("<" + tagName + "[^>]*id=\"" + id + "\"[^>]*>([\\s\\S]*?)</" + tagName + ">", "i");
	const match = html.match(pattern);
	return match ? decodeHtml(match[1]) : "";
}

function readSelectedOptionValue(html, id) {
	const pattern = new RegExp("<select[^>]*id=\"" + id + "\"[^>]*>([\\s\\S]*?)</select>", "i");
	const match = html.match(pattern);
	if (!match) {
		return "";
	}
	const selected = match[1].match(/<option[^>]*value="([^"]*)"[^>]*selected[^>]*>/i);
	if (selected) {
		return decodeHtml(selected[1]);
	}
	const firstOption = match[1].match(/<option[^>]*value="([^"]*)"[^>]*>/i);
	return firstOption ? decodeHtml(firstOption[1]) : "";
}

function decodeHtml(value) {
	return String(value)
		.replace(/&quot;/g, "\"")
		.replace(/&#39;/g, "'")
		.replace(/&lt;/g, "<")
		.replace(/&gt;/g, ">")
		.replace(/&amp;/g, "&");
}

function createHarness() {
	const document = {
		activeElement: null,
		byId: {},
		navItems: [],
		getElementById(id) {
			return this.byId[id] || null;
		},
		querySelectorAll(selector) {
			if (selector === "[data-nav]") {
				return this.navItems;
			}
			return [];
		}
	};

	const sectionBody = new FakeSectionBody();
	const baseElements = {
		"api-token-input": new FakeElement("api-token-input", "input"),
		"connect-button": new FakeElement("connect-button", "button"),
		"clear-button": new FakeElement("clear-button", "button"),
		"refresh-button": new FakeElement("refresh-button", "button"),
		"section-title": new FakeElement("section-title", "div"),
		"section-description": new FakeElement("section-description", "div"),
		"active-project-id": new FakeElement("active-project-id", "div"),
		"section-feedback": new FakeElement("section-feedback", "div"),
		"section-body": sectionBody,
		"auth-status": new FakeElement("auth-status", "div"),
		"response-preview": new FakeElement("response-preview", "pre"),
		toast: new FakeElement("toast", "div")
	};
	Object.assign(document.byId, baseElements);

	const navNames = ["overview", "projects", "connectors", "llm", "policies", "alerts", "incidents", "audit"];
	document.navItems = navNames.map((nav) => {
		const item = new FakeElement("", "button");
		item.dataset.nav = nav;
		return item;
	});

	let fetchHandler = async () => {
		throw new Error("Unhandled fetch call");
	};

	global.window = {
		document,
		setTimeout,
		clearTimeout,
		open: () => null,
		location: { replace() {} }
	};
	global.document = document;
	global.fetch = (url, options) => Promise.resolve().then(() => fetchHandler(url, options || {}));
	global.console = console;
	global.URL = URL;
	global.URLSearchParams = URLSearchParams;

	global.eval(INSTRUMENTED_SOURCE);

	return {
		hooks: global.window.__adminUiTestHooks,
		document,
		elements: baseElements,
		setFetchHandler(handler) {
			fetchHandler = handler;
		}
	};
}

function jsonResponse(payload, status) {
	const text = payload === null || payload === undefined ? "" : JSON.stringify(payload);
	return {
		ok: status >= 200 && status < 300,
		status,
		async text() {
			return text;
		}
	};
}

function deferredJsonResponse() {
	let resolve;
	let reject;
	const promise = new Promise((res, rej) => {
		resolve = res;
		reject = rej;
	});
	return {
		promise,
		resolve(payload, status = 200) {
			resolve(jsonResponse(payload, status));
		},
		reject(error) {
			reject(error);
		}
	};
}

function tick() {
	return new Promise((resolve) => setImmediate(resolve));
}

async function testConnectCommitsTokenOnlyAfterPrimeSucceeds() {
	const harness = createHarness();
	const { hooks, elements } = harness;
	hooks.state.bootstrapStatus.bootstrapped = true;
	hooks.state.activeNav = "projects";
	hooks.state.token = "old-token";
	hooks.state.activeProjectId = "old-project";
	hooks.state.connectorSettingsByProjectId["old-project"] = { configured: true };
	hooks.state.exportPoliciesByProjectId["old-project"] = { level: "level1_byom_only" };
	hooks.state.slackAlertsByProjectId["old-project"] = { configured: true };
	elements["api-token-input"].value = "new-token";

	const firstProjects = deferredJsonResponse();
	const firstTokens = deferredJsonResponse();
	let projectCalls = 0;

	harness.setFetchHandler((url, options) => {
		if (url === "/v1/projects") {
			projectCalls += 1;
			assert.equal(options.headers.Authorization, "Bearer new-token");
			if (projectCalls === 1) {
				return firstProjects.promise;
			}
			return Promise.resolve(jsonResponse({
				data: [{ id: "new-project", name: "New Project" }]
			}, 200));
		}
		if (url === "/v1/tokens") {
			assert.equal(options.headers.Authorization, "Bearer new-token");
			return firstTokens.promise;
		}
		throw new Error("Unexpected fetch: " + url);
	});

	const connectPromise = elements["connect-button"].fire("click");
	assert.equal(hooks.state.token, "old-token");
	assert.deepEqual(hooks.state.connectorSettingsByProjectId["old-project"], { configured: true });

	firstProjects.resolve({
		data: [{ id: "new-project", name: "New Project" }]
	});
	await tick();
	assert.equal(hooks.state.token, "old-token");

	firstTokens.resolve({
		data: [{ id: "token-1", name: "operator" }]
	});
	await connectPromise;

	assert.equal(hooks.state.token, "new-token");
	assert.equal(hooks.state.activeProjectId, "new-project");
	assert.equal(hooks.state.connectorSettingsByProjectId["old-project"], undefined);
	assert.equal(hooks.state.exportPoliciesByProjectId["old-project"], undefined);
	assert.equal(hooks.state.slackAlertsByProjectId["old-project"], undefined);
	assert.match(elements["auth-status"].textContent, /토큰이 메모리에 설정되었습니다/);
}

async function testClearInvalidatesPendingRefreshResponses() {
	const harness = createHarness();
	const { hooks, elements } = harness;
	hooks.state.bootstrapStatus.bootstrapped = true;
	hooks.state.token = "token";
	hooks.state.activeNav = "connectors";
	hooks.state.activeProjectId = "project-a";

	const connectorDeferred = deferredJsonResponse();

	harness.setFetchHandler((url) => {
		if (url === "/v1/projects/project-a/connectors/loki") {
			return connectorDeferred.promise;
		}
		throw new Error("Unexpected fetch: " + url);
	});

	const refreshPromise = hooks.refreshCurrentSection();
	await tick();
	await elements["clear-button"].fire("click");
	assert.equal(hooks.state.token, "");

	connectorDeferred.resolve({
		data: {
			configured: true,
			endpoint: "https://loki.example.com"
		}
	});
	await refreshPromise;

	assert.equal(elements["response-preview"].textContent, "토큰이 제거되었습니다.");
	assert.equal(hooks.state.connectorSettingsByProjectId["project-a"], undefined);
}

async function testConnectorSaveSkipsStaleSectionRerender() {
	const harness = createHarness();
	const { hooks, elements } = harness;
	hooks.state.bootstrapStatus.bootstrapped = true;
	hooks.state.token = "token";
	hooks.state.activeNav = "connectors";
	hooks.state.activeProjectId = "project-a";
	hooks.renderConnectorsSection();

	const sectionBody = elements["section-body"];
	sectionBody.querySelector("#connector-endpoint").value = "https://loki.example.com";
	sectionBody.querySelector("#connector-query").value = "{job=\"app\"}";

	const saveDeferred = deferredJsonResponse();
	let connectorReloaded = false;

	harness.setFetchHandler((url, options) => {
		if (url === "/v1/projects/project-a/connectors/loki" && options.method === "POST") {
			return saveDeferred.promise;
		}
		if (url === "/v1/projects/project-a/connectors/loki" && (!options.method || options.method === "GET")) {
			connectorReloaded = true;
			return Promise.resolve(jsonResponse({
				data: { configured: true }
			}, 200));
		}
		throw new Error("Unexpected fetch: " + url);
	});

	const submitPromise = sectionBody.querySelector("#connector-upsert-form").fire("submit");
	await tick();
	hooks.state.activeNav = "overview";
	elements["section-feedback"].textContent = "overview-stable";
	sectionBody.innerHTML = "<div id=\"overview-marker\"></div>";

	saveDeferred.resolve({
		data: { id: "connector-1" }
	});
	await submitPromise;

	assert.equal(connectorReloaded, false);
	assert.equal(elements["section-feedback"].textContent, "overview-stable");
	assert.ok(sectionBody.querySelector("#overview-marker"));
}

async function testAlertDefaultsMatchServerContract() {
	const harness = createHarness();
	const { hooks, elements } = harness;
	hooks.state.bootstrapStatus.bootstrapped = true;
	hooks.state.token = "token";
	hooks.state.activeNav = "alerts";
	hooks.state.activeProjectId = "project-a";

	hooks.renderAlertsSection();

	const sectionBody = elements["section-body"];
	assert.equal(sectionBody.querySelector("#slack-min-confidence").value, "0.45");
	assert.equal(sectionBody.querySelector("#email-min-confidence").value, "0.45");
}

async function main() {
	await testConnectCommitsTokenOnlyAfterPrimeSucceeds();
	await testClearInvalidatesPendingRefreshResponses();
	await testConnectorSaveSkipsStaleSectionRerender();
	await testAlertDefaultsMatchServerContract();
	process.stdout.write("admin-ui runtime checks passed\n");
}

main().catch((error) => {
	process.stderr.write(String(error && error.stack ? error.stack : error) + "\n");
	process.exitCode = 1;
});
