package com.conflux.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

	public Long id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwt) {
			try {
				return Long.valueOf(jwt.getToken().getSubject());
			}
			catch (NumberFormatException ex) {
				throw new InvalidBearerTokenException("Token subject is not a user id");
			}
		}
		throw new AuthenticationCredentialsNotFoundException("No authenticated user");
	}

}
