package com.logcopilot.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "logcopilot.llm.oauth")
@Validated
public class LlmOAuthProperties {

	private static final Duration DEFAULT_STATE_TTL = Duration.ofMinutes(10);

	@NotNull
	private Mode mode = Mode.STUB;

	@NotNull
	private Duration stateTtl = DEFAULT_STATE_TTL;

	@NotBlank
	private String callbackBaseUrl = "http://localhost:8080";

	@Positive
	private int maxStateEntries = 10000;

	@Valid
	@NotNull
	private ProviderSettings openai = ProviderSettings.defaultOpenai();

	@Valid
	@NotNull
	private ProviderSettings gemini = ProviderSettings.defaultGemini();

	public static LlmOAuthProperties defaultProperties() {
		return new LlmOAuthProperties();
	}

	public ProviderSettings provider(String provider) {
		String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "openai" -> openai;
			case "gemini" -> gemini;
			default -> null;
		};
	}

	public String authorizationUriFor(String provider) {
		ProviderSettings settings = provider(provider);
		if (settings == null) {
			return null;
		}
		return mode == Mode.LIVE ? settings.getAuthorizationUri() : settings.getStubAuthorizationUri();
	}

	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public Duration getStateTtl() {
		return stateTtl;
	}

	public void setStateTtl(Duration stateTtl) {
		if (stateTtl == null) {
			this.stateTtl = DEFAULT_STATE_TTL;
			return;
		}
		if (stateTtl.isZero() || stateTtl.isNegative()) {
			throw new IllegalArgumentException("stateTtl must be positive");
		}
		this.stateTtl = stateTtl;
	}

	public String getCallbackBaseUrl() {
		return callbackBaseUrl;
	}

	public void setCallbackBaseUrl(String callbackBaseUrl) {
		validateCallbackBaseUrl(callbackBaseUrl);
		this.callbackBaseUrl = callbackBaseUrl;
	}

	public int getMaxStateEntries() {
		return maxStateEntries;
	}

	public void setMaxStateEntries(int maxStateEntries) {
		this.maxStateEntries = maxStateEntries;
	}

	public ProviderSettings getOpenai() {
		return openai;
	}

	public void setOpenai(ProviderSettings openai) {
		this.openai = openai;
	}

	public ProviderSettings getGemini() {
		return gemini;
	}

	public void setGemini(ProviderSettings gemini) {
		this.gemini = gemini;
	}

	public enum Mode {
		STUB,
		LIVE
	}

	public static class ProviderSettings {

		@NotBlank
		private String clientId;

		@NotBlank
		private String authorizationUri;

		@NotBlank
		private String stubAuthorizationUri;

		@NotEmpty
		private List<@NotBlank String> scopes = new ArrayList<>();

		public static ProviderSettings defaultOpenai() {
			ProviderSettings settings = new ProviderSettings();
			settings.clientId = "openai-local-client";
			settings.authorizationUri = "https://auth.openai.com/oauth/authorize";
			settings.stubAuthorizationUri = "https://stub.openai.example.com/oauth/authorize";
			settings.scopes = new ArrayList<>(List.of("openid", "profile"));
			return settings;
		}

		public static ProviderSettings defaultGemini() {
			ProviderSettings settings = new ProviderSettings();
			settings.clientId = "gemini-local-client";
			settings.authorizationUri = "https://accounts.google.com/o/oauth2/v2/auth";
			settings.stubAuthorizationUri = "https://stub.gemini.example.com/oauth/authorize";
			settings.scopes = new ArrayList<>(List.of("openid", "profile"));
			return settings;
		}

		public String getClientId() {
			return clientId;
		}

		public void setClientId(String clientId) {
			this.clientId = clientId;
		}

		public String getAuthorizationUri() {
			return authorizationUri;
		}

		public void setAuthorizationUri(String authorizationUri) {
			this.authorizationUri = authorizationUri;
		}

		public String getStubAuthorizationUri() {
			return stubAuthorizationUri;
		}

		public void setStubAuthorizationUri(String stubAuthorizationUri) {
			this.stubAuthorizationUri = stubAuthorizationUri;
		}

		public List<String> getScopes() {
			return scopes;
		}

		public void setScopes(List<String> scopes) {
			this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
		}
	}

	private void validateCallbackBaseUrl(String callbackBaseUrl) {
		if (callbackBaseUrl == null || callbackBaseUrl.isBlank()) {
			throw new IllegalArgumentException("callbackBaseUrl must not be blank");
		}
		try {
			URI uri = new URI(callbackBaseUrl);
			if (!uri.isAbsolute() || uri.getHost() == null) {
				throw new IllegalArgumentException("callbackBaseUrl must be an absolute URI");
			}
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!"http".equals(scheme) && !"https".equals(scheme)) {
				throw new IllegalArgumentException("callbackBaseUrl must use http or https");
			}
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("callbackBaseUrl must be a valid URI");
		}
	}
}
