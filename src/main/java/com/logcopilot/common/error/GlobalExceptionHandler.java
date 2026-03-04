package com.logcopilot.common.error;

import com.logcopilot.common.api.ApiErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final int MAX_VALIDATION_DETAILS = 20;

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
		return error(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"validation_error",
			exception.getMessage(),
			List.of(validationDetail("request", exception.getMessage()))
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
		List<Map<String, Object>> details = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(this::toFieldDetail)
			.sorted(validationDetailComparator())
			.limit(MAX_VALIDATION_DETAILS)
			.toList();
		List<Map<String, Object>> normalizedDetails = capValidationDetails(details, exception.getErrorCount());
		String message = firstValidationMessage(normalizedDetails);
		return error(HttpStatus.UNPROCESSABLE_ENTITY, "validation_error", message, normalizedDetails);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
		List<Map<String, Object>> details = exception.getConstraintViolations()
			.stream()
			.map(this::toConstraintDetail)
			.sorted(validationDetailComparator())
			.limit(MAX_VALIDATION_DETAILS)
			.toList();
		List<Map<String, Object>> normalizedDetails = capValidationDetails(
			details,
			exception.getConstraintViolations().size()
		);
		String message = firstValidationMessage(normalizedDetails);
		return error(HttpStatus.UNPROCESSABLE_ENTITY, "validation_error", message, normalizedDetails);
	}

	@ExceptionHandler(BadGatewayException.class)
	public ResponseEntity<ApiErrorResponse> handleBadGateway(BadGatewayException exception) {
		return error(HttpStatus.BAD_GATEWAY, "bad_gateway", exception.getMessage());
	}

	@ExceptionHandler(NotImplementedException.class)
	public ResponseEntity<ApiErrorResponse> handleNotImplemented(NotImplementedException exception) {
		return error(HttpStatus.NOT_IMPLEMENTED, "not_implemented", exception.getMessage());
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status)
			.body(ApiErrorResponse.of(code, message));
	}

	private ResponseEntity<ApiErrorResponse> error(
		HttpStatus status,
		String code,
		String message,
		List<Map<String, Object>> details
	) {
		return ResponseEntity.status(status)
			.body(ApiErrorResponse.of(code, message, details));
	}

	private Map<String, Object> toFieldDetail(FieldError error) {
		return validationDetail(normalizeFieldPath(error.getField()), error.getDefaultMessage());
	}

	private Map<String, Object> toConstraintDetail(ConstraintViolation<?> violation) {
		String propertyPath = violation.getPropertyPath().toString();
		int separatorIndex = propertyPath.lastIndexOf('.');
		String field = separatorIndex < 0
			? propertyPath
			: propertyPath.substring(separatorIndex + 1);
		return validationDetail(normalizeFieldPath(field), violation.getMessage());
	}

	private String firstValidationMessage(List<Map<String, Object>> details) {
		if (details.isEmpty()) {
			return "Validation failed";
		}
		Object message = details.get(0).get("message");
		return message == null ? "Validation failed" : message.toString();
	}

	private String defaultMessage(String message) {
		return message == null || message.isBlank() ? "Validation failed" : message;
	}

	private List<Map<String, Object>> capValidationDetails(List<Map<String, Object>> details, int totalCount) {
		if (totalCount <= MAX_VALIDATION_DETAILS || details.isEmpty()) {
			return details;
		}

		List<Map<String, Object>> capped = new ArrayList<>(details);
		int omittedCount = totalCount - MAX_VALIDATION_DETAILS + 1;
		if (omittedCount > 0) {
			capped.remove(capped.size() - 1);
			capped.add(validationDetail("_truncated", "%d additional validation errors omitted".formatted(omittedCount)));
		}
		return List.copyOf(capped);
	}

	private Comparator<Map<String, Object>> validationDetailComparator() {
		return Comparator
			.comparing((Map<String, Object> detail) -> detail.get("field").toString())
			.thenComparing(detail -> detail.get("message").toString());
	}

	private Map<String, Object> validationDetail(String field, String message) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("field", field == null || field.isBlank() ? "request" : field);
		detail.put("message", defaultMessage(message));
		return detail;
	}

	private String normalizeFieldPath(String fieldPath) {
		if (fieldPath == null || fieldPath.isBlank()) {
			return "request";
		}

		String[] segments = fieldPath.split("\\.");
		StringBuilder normalized = new StringBuilder();
		for (int index = 0; index < segments.length; index++) {
			if (index > 0) {
				normalized.append('.');
			}
			normalized.append(normalizeSegment(segments[index]));
		}
		return normalized.toString();
	}

	private String normalizeSegment(String segment) {
		int bracketIndex = segment.indexOf('[');
		String base = bracketIndex < 0 ? segment : segment.substring(0, bracketIndex);
		String suffix = bracketIndex < 0 ? "" : segment.substring(bracketIndex);
		return camelToSnake(base) + suffix;
	}

	private String camelToSnake(String value) {
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (Character.isUpperCase(character)) {
				builder.append('_').append(Character.toLowerCase(character));
			} else {
				builder.append(character);
			}
		}
		return builder.toString();
	}
}
