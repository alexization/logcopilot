package com.logcopilot.common.error;

public class TooManyRequestsException extends RuntimeException {

	private static final long DEFAULT_RETRY_AFTER_SECONDS = 60L;
	private final long retryAfterSeconds;

	public TooManyRequestsException(String message) {
		this(message, DEFAULT_RETRY_AFTER_SECONDS);
	}

	public TooManyRequestsException(String message, long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
