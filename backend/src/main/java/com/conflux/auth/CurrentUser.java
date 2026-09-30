package com.conflux.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The authenticated user of the current request, taken from the verified JWT in the
 * Spring Security context (never from request data).
 */
@Component
public class CurrentUser {

	/**
	 * @return the user id (the token's {@code sub} claim, see {@link JwtTokenService})
	 * @throws AuthenticationCredentialsNotFoundException (401) if the request is not
	 * authenticated with a JWT
	 * @throws InvalidBearerTokenException (401) if the token's subject is not a user id
	 */
	public Long id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwt) {
			try {
				return Long.valueOf(jwt.getToken().getSubject());
			}
			catch (NumberFormatException ex) {
				// Only this application's signed tokens get here, so this "cannot happen"; treat as invalid.
				throw new InvalidBearerTokenException("Token subject is not a user id");
			}
		}
		throw new AuthenticationCredentialsNotFoundException("No authenticated user");
	}

}
