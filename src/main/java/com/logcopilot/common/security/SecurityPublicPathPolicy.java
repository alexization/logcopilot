package com.logcopilot.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;

public final class SecurityPublicPathPolicy {

	public static final String[] PUBLIC_ENDPOINT_PATTERNS = {
		"/healthz",
		"/readyz",
		"/favicon.ico",
		"/admin",
		"/admin/**",
		"/v1/projects/*/llm-oauth/*/callback"
	};

	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

	private SecurityPublicPathPolicy() {
	}

	public static boolean isPublicEndpoint(HttpServletRequest request) {
		String path = applicationPath(request);
		return Arrays.stream(PUBLIC_ENDPOINT_PATTERNS)
			.anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
	}

	public static String applicationPath(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null
			&& !contextPath.isBlank()
			&& requestUri != null
			&& requestUri.startsWith(contextPath)) {
			String normalized = requestUri.substring(contextPath.length());
			return normalized.isEmpty() ? "/" : normalized;
		}
		return requestUri == null || requestUri.isBlank() ? "/" : requestUri;
	}
}
