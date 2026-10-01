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
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			assertThat(User.normalizeUsername("IVAN")).isEqualTo("ivan");
			assertThat(User.normalizeEmail("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void usernameIsLowerCasedAndTrimmed() {
		assertThat(User.normalizeUsername("Ved_123")).isEqualTo("ved_123");
		assertThat(User.normalizeUsername("  ved-dev \t")).isEqualTo("ved-dev");
		assertThat(User.normalizeUsername("VEDCHANGANI")).isEqualTo("vedchangani");
	}

	@Test
	void accentedUsernameIsRejectedNotRewritten() {
		assertThatIllegalArgumentException().isThrownBy(() -> User.normalizeUsername("josé"));
		assertThatIllegalArgumentException().isThrownBy(() -> User.normalizeUsername("Zoë_dev"));
		assertThat(User.isValidUsername("josé")).isFalse();
	}

	@Test
	void usernameWithInvalidCharactersIsRejected() {
		for (String invalid : new String[] { "ved.123", "ved 123", "ved@example", "ved+1", "вед123", "ved!" }) {
			assertThatIllegalArgumentException().as(invalid).isThrownBy(() -> User.normalizeUsername(invalid));
			assertThat(User.isValidUsername(invalid)).as(invalid).isFalse();
		}
	}

	@Test
	void usernameLengthMustBeBetween3And30() {
		assertThat(User.normalizeUsername("abc")).isEqualTo("abc");
		assertThat(User.normalizeUsername("a".repeat(30))).hasSize(30);
		assertThatIllegalArgumentException().isThrownBy(() -> User.normalizeUsername("ab"));
		assertThatIllegalArgumentException().isThrownBy(() -> User.normalizeUsername("a".repeat(31)));
		assertThatIllegalArgumentException().isThrownBy(() -> User.normalizeUsername("  ab  "));
	}

	@Test
	void newUserRejectsInvalidUsername() {
		assertThatIllegalArgumentException().isThrownBy(() -> new User("a@example.com", "hash", "josé", "José"));
	}

	@Test
	void displayNameKeepsUnicode() {
		User user = new User("jose@example.com", "hash", "jose", "José Müller");

		assertThat(user.getDisplayName()).isEqualTo("José Müller");
	}

	@Test
	void blankOrNullIdentifiersAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> User.normalizeEmail("   "));
		assertThatIllegalArgumentException().isThrownBy(() -> User.normalizeUsername(""));
		assertThatNullPointerException().isThrownBy(() -> User.normalizeEmail(null));
		assertThatNullPointerException().isThrownBy(() -> User.normalizeUsername(null));
	}

}
