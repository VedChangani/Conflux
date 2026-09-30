package com.conflux.auth;

import java.time.Duration;
import java.time.Instant;

import com.conflux.user.User;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed access tokens. Claims: {@code iss}, {@code sub} (user id), {@code iat},
 * {@code exp} and {@code role}. No credentials or profile data are included.
 */
@Service
public class JwtTokenService {

	static final String ROLE_CLAIM = "role";

	private final JwtEncoder encoder;

	private final String issuer;

	private final Duration ttl;

	public JwtTokenService(JwtEncoder encoder, JwtProperties properties) {
		this.encoder = encoder;
		this.issuer = properties.issuer();
		this.ttl = properties.accessTokenTtl();
	}

	public TokenResponse issueAccessToken(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(this.issuer)
			.subject(user.getId().toString())
			.issuedAt(now)
			.expiresAt(now.plus(this.ttl))
			.claim(ROLE_CLAIM, user.getRole().name())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String token = this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return TokenResponse.bearer(token, this.ttl);
	}

}
