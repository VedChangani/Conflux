package com.conflux.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileTest {

	@Test
	void acceptsOrdinaryHttpAndHttpsUrls() {
		for (String url : new String[] { "https://example.com", "http://example.com", "https://example.com/",
				"https://github.com/ved-changani", "https://www.linkedin.com/in/ved?trk=x#top", "HTTPS://Example.COM/Path",
				"http://localhost:8080/me", "https://sub.domain.co.uk/a/b/c" }) {
			assertThat(HttpUrl.Validator.isHttpUrl(url)).as(url).isTrue();
		}
	}

	@Test
	void rejectsNonHttpOrMalformedUrls() {
		for (String url : new String[] { "example.com", "www.example.com", "ftp://example.com", "mailto:me@example.com",
				"javascript:alert(1)", "https://", "http:///path", "https://exa mple.com", "https://user:pw@example.com",
				"//example.com", "https:example.com", "" }) {
			assertThat(HttpUrl.Validator.isHttpUrl(url)).as(url).isFalse();
		}
	}

	@Test
	void requestTrimsEndsKeepsCaseAndInnerSpacesAndBlankOptionalValuesBecomeNull() {
		UpdateProfileRequest request = new UpdateProfileRequest("  Ved   Changani ", "  Builds  things.\n ", "   ",
				" https://ved.example.com/ ", "", null);

		assertThat(request.displayName()).isEqualTo("Ved   Changani");
		assertThat(request.bio()).isEqualTo("Builds  things.");
		assertThat(request.location()).isNull();
		assertThat(request.websiteUrl()).isEqualTo("https://ved.example.com/");
		assertThat(request.githubUrl()).isNull();
		assertThat(request.linkedinUrl()).isNull();
		assertThat(new UpdateProfileRequest("   ", null, null, null, null, null).displayName()).isEmpty();
	}

	@Test
	void entityStoresBlankOptionalProfileValuesAsNull() {
		User user = new User("ved@example.com", "hash", "ved", "Ved");
		user.setBio(" \n ");
		user.setLocation("");
		user.setWebsiteUrl("  ");
		user.setGithubUrl("\t");
		user.setLinkedinUrl(null);

		assertThat(user.getBio()).isNull();
		assertThat(user.getLocation()).isNull();
		assertThat(user.getWebsiteUrl()).isNull();
		assertThat(user.getGithubUrl()).isNull();
		assertThat(user.getLinkedinUrl()).isNull();
		user.setBio(" kept as given ");
		assertThat(user.getBio()).isEqualTo(" kept as given ");
	}

}
