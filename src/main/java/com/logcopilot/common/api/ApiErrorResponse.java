package com.logcopilot.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public record ApiErrorResponse(ErrorBody error) {

	public static ApiErrorResponse of(String code, String message) {
		return new ApiErrorResponse(new ErrorBody(code, message, null));
	}

	public static ApiErrorResponse of(String code, String message, List<Map<String, Object>> details) {
		return new ApiErrorResponse(new ErrorBody(code, message, details));
	}

	public record ErrorBody(
		String code,
		String message,
		@JsonInclude(JsonInclude.Include.NON_NULL)
		List<Map<String, Object>> details
	) {
	}
}
