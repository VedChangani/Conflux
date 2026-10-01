package com.conflux.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class UserStatusTransitionTest {

	@Test
	void suspendAndRestoreAreTheOnlyStatusTransitions() {
		User user = new User("u@example.com", "hash", "user", "User");

		user.suspend();
		assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
		assertThatIllegalStateException().isThrownBy(user::suspend);

		user.restore();
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThatIllegalStateException().isThrownBy(user::restore);
	}

	@Test
	void thereIsNoGenericStatusSetter() {
		assertThat(User.class.getMethods()).extracting(java.lang.reflect.Method::getName).doesNotContain("setStatus");
	}

}
