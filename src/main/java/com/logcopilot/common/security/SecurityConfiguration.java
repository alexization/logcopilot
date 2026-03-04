package com.logcopilot.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logcopilot.common.api.ApiErrorResponse;
import com.logcopilot.common.auth.BearerTokenValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfiguration {

	private static final String CONTENT_SECURITY_POLICY = String.join(" ",
		"default-src 'self';",
		"script-src 'self';",
		"style-src 'self';",
		"img-src 'self' data:;",
		"connect-src 'self';",
		"object-src 'none';",
		"base-uri 'self';",
		"form-action 'self';",
		"frame-ancestors 'none'"
	);

	@Bean
	public SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		BearerAuthenticationFilter bearerAuthenticationFilter,
		AuthenticationEntryPoint authenticationEntryPoint,
		AccessDeniedHandler accessDeniedHandler
	) throws Exception {
		http
			.csrf(this::configureCsrfPolicy)
			.cors(Customizer.withDefaults())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
				.frameOptions(frame -> frame.deny())
				.referrerPolicy(referrer -> referrer
					.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/healthz", "/readyz").permitAll()
				.requestMatchers("/favicon.ico").permitAll()
				.requestMatchers("/admin", "/admin/**").permitAll()
				.requestMatchers("/v1/projects/*/llm-oauth/*/callback").permitAll()
				.requestMatchers("/v1/ingest/**").hasRole("INGEST")
				.anyRequest().hasRole("API"))
			.addFilterBefore(bearerAuthenticationFilter, AnonymousAuthenticationFilter.class);

		return http.build();
	}

	private void configureCsrfPolicy(CsrfConfigurer<HttpSecurity> csrf) {
		csrf.ignoringRequestMatchers(
			"/healthz",
			"/readyz",
			"/admin",
			"/admin/**",
			"/v1/**"
		);
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(
		@Value("${logcopilot.security.cors.allowed-origins:}") String allowedOriginsProperty
	) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(parseAllowedOrigins(allowedOriginsProperty));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of(
			"Authorization",
			"Content-Type",
			"Idempotency-Key",
			"X-CSRF-TOKEN"
		));
		configuration.setExposedHeaders(List.of("Retry-After", "Location"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/v1/**", configuration);
		return source;
	}

	private List<String> parseAllowedOrigins(String allowedOriginsProperty) {
		if (allowedOriginsProperty == null || allowedOriginsProperty.isBlank()) {
			return List.of();
		}
		return Arrays.stream(allowedOriginsProperty.split(","))
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.toList();
	}

	@Bean
	public BearerAuthenticationFilter bearerAuthenticationFilter(
		BearerTokenValidator bearerTokenValidator,
		AuthenticationEntryPoint authenticationEntryPoint
	) {
		return new BearerAuthenticationFilter(bearerTokenValidator, authenticationEntryPoint);
	}

	@Bean
	public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
		return (request, response, exception) -> writeError(
			response,
			HttpStatus.UNAUTHORIZED,
			ApiErrorResponse.of("unauthorized", unauthorizedMessage(request)),
			objectMapper
		);
	}

	@Bean
	public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
		return (request, response, exception) -> writeError(
			response,
			HttpStatus.FORBIDDEN,
			ApiErrorResponse.of("forbidden", "Access denied"),
			objectMapper
		);
	}

	private void writeError(
		HttpServletResponse response,
		HttpStatus status,
		ApiErrorResponse payload,
		ObjectMapper objectMapper
	) throws IOException {
		if (response.isCommitted()) {
			return;
		}

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), payload);
	}

	private String unauthorizedMessage(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path != null && path.startsWith("/v1/ingest/")) {
			return "Missing or invalid ingest token";
		}
		return "Missing or invalid bearer token";
	}
}
