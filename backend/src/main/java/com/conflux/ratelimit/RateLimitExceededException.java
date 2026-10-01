package com.conflux.ratelimit;

public class RateLimitExceededException extends RuntimeException {

	private final long retryAfterSeconds;

	RateLimitExceededException(long retryAfterSeconds) {
		super("Rate limit exceeded", null, false, false);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return this.retryAfterSeconds;
	}

}
