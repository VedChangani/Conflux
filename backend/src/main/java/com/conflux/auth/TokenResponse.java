package com.conflux.auth;

import java.time.Duration;

/**
 * Successful login response.
 *
 * @param accessToken signed JWT to send as {@code Authorization: Bearer <accessToken>}
 * @param tokenType always {@code Bearer}
 * @param expiresIn token lifetime in seconds
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	static TokenResponse bearer(String accessToken, Duration ttl) {
		return new TokenResponse(accessToken, "Bearer", ttl.toSeconds());
	}

	@Override
	public String toString() {
		return "TokenResponse[accessToken=****, tokenType=" + this.tokenType + ", expiresIn=" + this.expiresIn + "]";
	}

}
