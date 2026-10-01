package com.conflux.auth;

import java.time.Duration;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	static TokenResponse bearer(String accessToken, Duration ttl) {
		return new TokenResponse(accessToken, "Bearer", ttl.toSeconds());
	}

	@Override
	public String toString() {
		return "TokenResponse[accessToken=****, tokenType=" + this.tokenType + ", expiresIn=" + this.expiresIn + "]";
	}

}
