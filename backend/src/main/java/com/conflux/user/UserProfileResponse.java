package com.conflux.user;

import java.time.Instant;

public record UserProfileResponse(Long id, String username, String displayName, String bio, String location,
		String websiteUrl, String githubUrl, String linkedinUrl, Instant createdAt) {

	static UserProfileResponse from(User user) {
		return new UserProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getBio(),
				user.getLocation(), user.getWebsiteUrl(), user.getGithubUrl(), user.getLinkedinUrl(),
				user.getCreatedAt());
	}

}
