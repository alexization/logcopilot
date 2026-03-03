package com.logcopilot.common.auth;

import com.logcopilot.common.error.UnauthorizedException;
import org.springframework.stereotype.Component;

@Component
public class BearerTokenValidator {

	public String validate(String authorization) {
		if (authorization == null) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		String[] parts = authorization.trim().split("\\s+", 2);
		if (parts.length != 2 || !"bearer".equalsIgnoreCase(parts[0]) || parts[1].isBlank()) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		return parts[1].trim();
	}
}
