package com.conflux.auth;

import com.conflux.user.User;
import com.conflux.user.UserRole;
import com.conflux.user.UserStatus;

public record AccountResponse(Long id, String email, String username, String displayName, UserRole role,
		UserStatus status) {

	static AccountResponse from(User user) {
		return new AccountResponse(user.getId(), user.getEmail(), user.getUsername(), user.getDisplayName(),
				user.getRole(), user.getStatus());
	}

}
