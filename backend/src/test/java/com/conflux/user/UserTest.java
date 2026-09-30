package com.conflux.user;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UserTest {

	@Test
	void newUserIsActiveWithUserRole() {
		User user = new User("alice@example.com", "hash", "alice", "Alice");

		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void emailAndUsernameAreNormalized() {
		User user = new User("  Alice@Example.COM ", "hash", " Alice_Dev ", "Alice");

		assertThat(user.getEmail()).isEqualTo("alice@example.com");
		assertThat(user.getUsername()).isEqualTo("alice_dev");
		assertThat(user.getDisplayName()).isEqualTo("Alice");
	}

	@Test
	void normalizationIsLocaleIndependent() {
		Locale original = Locale.getDefault();
		try {
			// Under a Turkish default locale, "I".toLowerCase() would be the dotless "ı".
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			assertThat(User.normalizeUsername("IVAN")).isEqualTo("ivan");
			assertThat(User.normalizeEmail("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void blankOrNullIdentifiersAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> User.normalizeEmail("   "));
		assertThatIllegalArgumentException().isThrownBy(() -> User.normalizeUsername(""));
		assertThatNullPointerException().isThrownBy(() -> User.normalizeEmail(null));
		assertThatNullPointerException().isThrownBy(() -> User.normalizeUsername(null));
	}

}
