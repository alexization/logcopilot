package com.logcopilot.common.http;

import com.logcopilot.common.error.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyValidator {

	public String validateRequired(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BadRequestException("Idempotency-Key header is required");
		}
		return idempotencyKey.trim();
	}
}
