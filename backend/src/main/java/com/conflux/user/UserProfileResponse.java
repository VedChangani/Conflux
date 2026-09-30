package com.conflux.user;

import java.time.Instant;

/**
 * A user's public profile, used for both the public view and the user's own view. Never
 * contains email, credentials, role, status or other security data.
 */
public record UserProfileResponse(Long id, String username, String displayName, String bio, String location,
		String websiteUrl, String githubUrl, String linkedinUrl, Instant createdAt) {

	static UserProfileResponse from(User user) {
		return new UserProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getBio(),
				user.getLocation(), user.getWebsiteUrl(), user.getGithubUrl(), user.getLinkedinUrl(),
				user.getCreatedAt());
	}

}
