package com.conflux.user;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Runs against the H2 (MySQL mode) test database with the real Flyway migrations
 * applied; the embedded-database replacement is disabled on purpose.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private Flyway flyway;

	@Test
	void flywayMigrationCreatesUsersTable() {
		// V1 specifically (later migrations exist), not merely the current version.
		MigrationInfo v1 = Arrays.stream(this.flyway.info().applied())
			.filter(migration -> "1".equals(migration.getVersion().getVersion()))
			.findFirst()
			.orElse(null);
		assertThat(v1).isNotNull();
		assertThat(v1.getVersion().getVersion()).isEqualTo("1");
		assertThat(v1.getScript()).isEqualTo("V1__create_users_table.sql");
		assertThat(v1.getState()).isEqualTo(MigrationState.SUCCESS);

		List<String> columns = this.entityManager.getEntityManager()
			.createNativeQuery("SELECT column_name FROM information_schema.columns "
					+ "WHERE table_schema = SCHEMA() AND table_name = 'users' ORDER BY ordinal_position", String.class)
			.getResultList();
		assertThat(columns).containsExactly("id", "email", "password_hash", "username", "display_name", "bio",
				"location", "website_url", "github_url", "linkedin_url", "role", "status", "created_at",
				"updated_at");
	}

	@Test
	void persistsAndRetrievesUser() {
		User user = new User("alice@example.com", "{bcrypt}hash", "alice", "Alice Doe");
		user.setBio("Builder of things");
		user.setLocation("Pune, India");
		user.setWebsiteUrl("https://alice.example.com");
		user.setGithubUrl("https://github.com/alice");
		user.setLinkedinUrl("https://www.linkedin.com/in/alice");
		Long id = this.userRepository.saveAndFlush(user).getId();
		this.entityManager.clear();

		User found = this.userRepository.findById(id).orElseThrow();

		assertThat(found.getId()).isEqualTo(id);
		assertThat(found.getEmail()).isEqualTo("alice@example.com");
		assertThat(found.getPasswordHash()).isEqualTo("{bcrypt}hash");
		assertThat(found.getUsername()).isEqualTo("alice");
		assertThat(found.getDisplayName()).isEqualTo("Alice Doe");
		assertThat(found.getBio()).isEqualTo("Builder of things");
		assertThat(found.getLocation()).isEqualTo("Pune, India");
		assertThat(found.getWebsiteUrl()).isEqualTo("https://alice.example.com");
		assertThat(found.getGithubUrl()).isEqualTo("https://github.com/alice");
		assertThat(found.getLinkedinUrl()).isEqualTo("https://www.linkedin.com/in/alice");
		assertThat(found.getRole()).isEqualTo(UserRole.USER);
		assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void optionalProfileFieldsMayBeNull() {
		Long id = this.userRepository.saveAndFlush(new User("bob@example.com", "hash", "bob", "Bob")).getId();
		this.entityManager.clear();

		User found = this.userRepository.findById(id).orElseThrow();

		assertThat(found.getBio()).isNull();
		assertThat(found.getLocation()).isNull();
		assertThat(found.getWebsiteUrl()).isNull();
		assertThat(found.getGithubUrl()).isNull();
		assertThat(found.getLinkedinUrl()).isNull();
	}

	@Test
	void emailIsUniqueIgnoringCase() {
		this.userRepository.saveAndFlush(new User("alice@example.com", "hash", "alice", "Alice"));

		User duplicate = new User("  Alice@Example.COM ", "hash", "alice2", "Alice Two");

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.userRepository.saveAndFlush(duplicate));
	}

	@Test
	void usernameIsUniqueIgnoringCase() {
		this.userRepository.saveAndFlush(new User("alice@example.com", "hash", "alice", "Alice"));

		User duplicate = new User("other@example.com", "hash", "ALICE", "Other Alice");

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.userRepository.saveAndFlush(duplicate));
	}

	@Test
	void enumsArePersistedAsStrings() {
		User user = new User("admin@example.com", "hash", "admin", "Admin");
		user.setRole(UserRole.ADMIN);
		user.setStatus(UserStatus.SUSPENDED);
		Long id = this.userRepository.saveAndFlush(user).getId();
		this.entityManager.clear();

		Object[] row = (Object[]) this.entityManager.getEntityManager()
			.createNativeQuery("SELECT role, status FROM users WHERE id = ?1")
			.setParameter(1, id)
			.getSingleResult();
		assertThat(row).containsExactly("ADMIN", "SUSPENDED");

		User found = this.userRepository.findById(id).orElseThrow();
		assertThat(found.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(found.getStatus()).isEqualTo(UserStatus.SUSPENDED);
	}

	@Test
	void timestampsAreSetOnCreateAndUpdatedOnChange() throws InterruptedException {
		User user = this.userRepository.saveAndFlush(new User("carol@example.com", "hash", "carol", "Carol"));
		assertThat(user.getCreatedAt()).isNotNull();
		assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
		Long id = user.getId();
		this.entityManager.clear();

		User stored = this.userRepository.findById(id).orElseThrow();
		assertThat(stored.getCreatedAt()).isEqualTo(user.getCreatedAt());
		assertThat(stored.getUpdatedAt()).isEqualTo(user.getUpdatedAt());

		Thread.sleep(5);
		stored.setDisplayName("Carol Updated");
		this.userRepository.saveAndFlush(stored);
		this.entityManager.clear();

		User updated = this.userRepository.findById(id).orElseThrow();
		assertThat(updated.getDisplayName()).isEqualTo("Carol Updated");
		assertThat(updated.getCreatedAt()).isEqualTo(user.getCreatedAt());
		assertThat(updated.getUpdatedAt()).isAfter(user.getCreatedAt());
	}

	@Test
	void findsAndChecksExistenceByNormalizedEmailAndUsername() {
		this.userRepository.saveAndFlush(new User("Dave@Example.com", "hash", "Dave", "Dave"));

		assertThat(this.userRepository.findByEmail(User.normalizeEmail("DAVE@example.com")))
			.map(User::getUsername)
			.contains("dave");
		assertThat(this.userRepository.findByEmail("nobody@example.com")).isEmpty();

		assertThat(this.userRepository.existsByEmail(User.normalizeEmail(" dave@EXAMPLE.com"))).isTrue();
		assertThat(this.userRepository.existsByEmail("nobody@example.com")).isFalse();

		assertThat(this.userRepository.findByUsername(User.normalizeUsername(" DAVE ")))
			.map(User::getEmail)
			.contains("dave@example.com");
		assertThat(this.userRepository.findByUsername("nobody")).isEmpty();

		assertThat(this.userRepository.existsByUsername(User.normalizeUsername("DAVE"))).isTrue();
		assertThat(this.userRepository.existsByUsername("nobody")).isFalse();
	}

}
