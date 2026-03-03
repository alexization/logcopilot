package com.logcopilot.common.error;

import com.logcopilot.common.api.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ApiErrorResponse.of("unauthorized", exception.getMessage()));
	}
}
