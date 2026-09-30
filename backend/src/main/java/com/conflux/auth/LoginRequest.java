package com.conflux.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Login input. {@code identifier} is either an email address or a username.
 */
public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {

	@Override
	public String toString() {
		return "LoginRequest[identifier=" + this.identifier + ", password=****]";
	}

}
