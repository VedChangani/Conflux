package com.conflux.auth;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from {@code conflux.jwt.*}. Values come from the environment
 * ({@code JWT_SECRET}, {@code JWT_ISSUER}, {@code JWT_ACCESS_TOKEN_TTL}); invalid values
 * fail application startup.
 *
 * @param secret Base64-encoded HMAC key of at least 256 bits (32 bytes), used for HS256
 * @param issuer value of the {@code iss} claim written to and required in every token
 * @param accessTokenTtl lifetime of an access token
 */
@ConfigurationProperties("conflux.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

	private static final int MIN_SECRET_BYTES = 32;

	public JwtProperties {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("conflux.jwt.secret (JWT_SECRET) must be set");
		}
		if (decode(secret).length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
					"conflux.jwt.secret (JWT_SECRET) must decode to at least " + MIN_SECRET_BYTES + " bytes");
		}
		if (issuer == null || issuer.isBlank()) {
			throw new IllegalArgumentException("conflux.jwt.issuer (JWT_ISSUER) must be set");
		}
		if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
			throw new IllegalArgumentException("conflux.jwt.access-token-ttl (JWT_ACCESS_TOKEN_TTL) must be positive");
		}
	}

	SecretKey signingKey() {
		return new SecretKeySpec(decode(this.secret), "HmacSHA256");
	}

	private static byte[] decode(String secret) {
		try {
			return Base64.getDecoder().decode(secret.strip());
		}
		catch (IllegalArgumentException ex) {
			// Deliberately does not include the value or the decoder message.
			throw new IllegalArgumentException("conflux.jwt.secret (JWT_SECRET) must be valid Base64");
		}
	}

	@Override
	public String toString() {
		return "JwtProperties[secret=****, issuer=" + this.issuer + ", accessTokenTtl=" + this.accessTokenTtl + "]";
	}

}
