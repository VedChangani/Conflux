package com.conflux.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@NotBlank @Size(max = User.DISPLAY_NAME_MAX_LENGTH) String displayName,
		@Size(max = User.BIO_MAX_LENGTH) String bio,
		@Size(max = User.LOCATION_MAX_LENGTH) String location,
		@Size(max = User.URL_MAX_LENGTH) @HttpUrl String websiteUrl,
		@Size(max = User.URL_MAX_LENGTH) @HttpUrl String githubUrl,
		@Size(max = User.URL_MAX_LENGTH) @HttpUrl String linkedinUrl) {

	public UpdateProfileRequest {
		displayName = (displayName != null) ? displayName.strip() : null;
		bio = optional(bio);
		location = optional(location);
		websiteUrl = optional(websiteUrl);
		githubUrl = optional(githubUrl);
		linkedinUrl = optional(linkedinUrl);
	}

	private static String optional(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}

}
