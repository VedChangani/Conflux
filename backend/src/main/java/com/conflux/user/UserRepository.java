package com.conflux.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByUsername(String username);

	Optional<User> findByUsernameAndStatus(String username, UserStatus status);

	boolean existsByEmail(String email);

	boolean existsByUsername(String username);

}
