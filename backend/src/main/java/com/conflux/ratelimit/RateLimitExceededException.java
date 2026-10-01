package com.conflux.ratelimit;

/**
 * The caller used up an operation's allowance; rendered as 429 with {@code Retry-After}
 * by {@link com.conflux.common.web.GlobalExceptionHandler}.
 */
public class RateLimitExceededException extends RuntimeException {

	private final long retryAfterSeconds;

	RateLimitExceededException(long retryAfterSeconds) {
		super("Rate limit exceeded", null, false, false);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/**
	 * @return whole seconds (at least 1) until the caller's allowance is renewed
	 */
	public long getRetryAfterSeconds() {
		return this.retryAfterSeconds;
	}

}
