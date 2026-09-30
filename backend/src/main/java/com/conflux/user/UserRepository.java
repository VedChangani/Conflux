package com.conflux.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link User}. Email and username arguments must already be normalized
 * with {@link User#normalizeEmail(String)} / {@link User#normalizeUsername(String)}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByUsername(String username);

	/**
	 * A user by (normalized) username only if the account has the given status, e.g. ACTIVE
	 * for public profiles.
	 */
	Optional<User> findByUsernameAndStatus(String username, UserStatus status);

	boolean existsByEmail(String email);

	boolean existsByUsername(String username);

}
