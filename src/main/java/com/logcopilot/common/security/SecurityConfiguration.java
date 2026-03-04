package com.logcopilot.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logcopilot.common.api.ApiErrorResponse;
import com.logcopilot.common.auth.BearerTokenValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

@Configuration
public class SecurityConfiguration {

	@Bean
	public SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		BearerAuthenticationFilter bearerAuthenticationFilter,
		AuthenticationEntryPoint authenticationEntryPoint,
		AccessDeniedHandler accessDeniedHandler
	) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/healthz", "/readyz").permitAll()
				.requestMatchers("/v1/projects/*/llm-oauth/*/callback").permitAll()
				.requestMatchers("/v1/ingest/**").hasRole("INGEST")
				.anyRequest().hasRole("API"))
			.addFilterBefore(bearerAuthenticationFilter, AnonymousAuthenticationFilter.class);

		return http.build();
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
