package com.logcopilot.common.error;

import com.logcopilot.common.api.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException exception) {
		return error(HttpStatus.UNAUTHORIZED, "unauthorized", exception.getMessage());
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException exception) {
		return error(HttpStatus.BAD_REQUEST, "bad_request", exception.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException exception) {
		return error(HttpStatus.BAD_REQUEST, "bad_request", "Malformed JSON request body");
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception) {
		return error(HttpStatus.CONFLICT, "conflict", exception.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, "not_found", exception.getMessage());
	}

	@ExceptionHandler(ValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(ValidationException exception) {
		return error(HttpStatus.UNPROCESSABLE_ENTITY, "validation_error", exception.getMessage());
	}

	@ExceptionHandler(BadGatewayException.class)
	public ResponseEntity<ApiErrorResponse> handleBadGateway(BadGatewayException exception) {
		return error(HttpStatus.BAD_GATEWAY, "bad_gateway", exception.getMessage());
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status)
			.body(ApiErrorResponse.of(code, message));
	}
}
