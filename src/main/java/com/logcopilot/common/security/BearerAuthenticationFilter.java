package com.logcopilot.common.security;

import com.logcopilot.common.auth.BearerTokenValidator;
import com.logcopilot.common.error.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public class BearerAuthenticationFilter extends OncePerRequestFilter {

	private static final Pattern OAUTH_CALLBACK_PATH = Pattern.compile("^/v1/projects/[^/]+/llm-oauth/[^/]+/callback$");

	private final BearerTokenValidator bearerTokenValidator;
	private final AuthenticationEntryPoint authenticationEntryPoint;

	public BearerAuthenticationFilter(
		BearerTokenValidator bearerTokenValidator,
		AuthenticationEntryPoint authenticationEntryPoint
	) {
		this.bearerTokenValidator = bearerTokenValidator;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		boolean publicEndpoint = "/healthz".equals(path)
			|| "/readyz".equals(path)
			|| OAUTH_CALLBACK_PATH.matcher(path).matches();
		return HttpMethod.OPTIONS.matches(request.getMethod()) || publicEndpoint;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		try {
			BearerTokenValidator.ValidatedToken validatedToken = bearerTokenValidator.validate(
				request.getHeader(HttpHeaders.AUTHORIZATION)
			);
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				validatedToken.value(),
				validatedToken.value(),
				List.of(new SimpleGrantedAuthority(resolveAuthority(validatedToken)))
			);
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
			filterChain.doFilter(request, response);
		} catch (UnauthorizedException exception) {
			SecurityContextHolder.clearContext();
			authenticationEntryPoint.commence(
				request,
				response,
				new BadCredentialsException(exception.getMessage(), exception)
			);
		}
	}

	private String resolveAuthority(BearerTokenValidator.ValidatedToken validatedToken) {
		return switch (validatedToken.type()) {
			case INGEST -> "ROLE_INGEST";
			case API -> "ROLE_API";
		};
	}
}
