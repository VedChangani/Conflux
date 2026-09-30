package com.conflux.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link User}. Email and username arguments must already be normalized
 * with {@link User#normalizeEmail(String)} / {@link User#normalizeUsername(String)}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByUsername(String username);

}
