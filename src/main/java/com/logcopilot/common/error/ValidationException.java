package com.logcopilot.common.error;

public class ValidationException extends RuntimeException {

	public ValidationException(String message) {
		super(message);
	}
}
