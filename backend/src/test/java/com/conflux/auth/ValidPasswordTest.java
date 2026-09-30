package com.conflux.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidPasswordTest {

	private final ValidPassword.Validator validator = new ValidPassword.Validator();

	@Test
	void countsUtf8BytesNotCharacters() {
		assertThat(ValidPassword.Validator.utf8Length("abcdefgh")).isEqualTo(8);
		assertThat(ValidPassword.Validator.utf8Length("é")).isEqualTo(2);
		assertThat(ValidPassword.Validator.utf8Length("密")).isEqualTo(3);
		assertThat(ValidPassword.Validator.utf8Length("🔒")).isEqualTo(4);
	}

	@Test
	void acceptsExactly8To72Bytes() {
		assertThat(isValid("a".repeat(8))).isTrue();
		assertThat(isValid("a".repeat(72))).isTrue();
		assertThat(isValid("é".repeat(36))).isTrue();
		assertThat(isValid("a".repeat(7))).isFalse();
		assertThat(isValid("a".repeat(73))).isFalse();
		assertThat(isValid("a".repeat(71) + "é")).isFalse();
		assertThat(isValid("")).isFalse();
	}

	@Test
	void nullIsLeftToNotNull() {
		assertThat(isValid(null)).isTrue();
	}

	private boolean isValid(String password) {
		return this.validator.isValid(password, null);
	}

}
