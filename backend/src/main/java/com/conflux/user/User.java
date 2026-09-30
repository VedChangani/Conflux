package com.conflux.user;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A Conflux user account and its public profile.
 * <p>
 * {@code email} and {@code username} are always stored normalized (see
 * {@link #normalizeEmail(String)} and {@link #normalizeUsername(String)}), so the
 * database unique constraints make them case-insensitively unique. Callers looking a
 * user up by email or username must normalize the value first.
 */
@Entity
@Table(name = "users")
public class User {

	/**
	 * Human-readable username rule, applied after trimming and lower-casing.
	 */
	public static final String USERNAME_RULE = "3-30 characters, only a-z, 0-9, '_' and '-'";

	private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9_-]{3,30}");

	/** Maximum lengths of the optional public profile fields (see V7). */
	public static final int BIO_MAX_LENGTH = 500;

	public static final int LOCATION_MAX_LENGTH = 120;

	public static final int URL_MAX_LENGTH = 255;

	public static final int DISPLAY_NAME_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "email", nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "username", nullable = false, length = 50)
	private String username;

	@Column(name = "display_name", nullable = false, length = DISPLAY_NAME_MAX_LENGTH)
	private String displayName;

	@Column(name = "bio", length = BIO_MAX_LENGTH)
	private String bio;

	@Column(name = "location", length = LOCATION_MAX_LENGTH)
	private String location;

	@Column(name = "website_url", length = URL_MAX_LENGTH)
	private String websiteUrl;

	@Column(name = "github_url", length = URL_MAX_LENGTH)
	private String githubUrl;

	@Column(name = "linkedin_url", length = URL_MAX_LENGTH)
	private String linkedinUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private UserStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * For JPA only.
	 */
	protected User() {
	}

	/**
	 * Creates a new active user with the {@link UserRole#USER} role.
	 */
	public User(String email, String passwordHash, String username, String displayName) {
		setEmail(email);
		setPasswordHash(passwordHash);
		setUsername(username);
		setDisplayName(displayName);
		this.role = UserRole.USER;
		this.status = UserStatus.ACTIVE;
	}

	/**
	 * Canonical form of an email address: surrounding whitespace removed, lower-cased.
	 */
	public static String normalizeEmail(String email) {
		return normalizeIdentifier(email, "email");
	}

	/**
	 * Canonical form of a username: surrounding whitespace removed, lower-cased. The
	 * result must match {@link #USERNAME_RULE}; anything else (including accented or
	 * other non-ASCII letters) is rejected rather than rewritten.
	 * @throws IllegalArgumentException if the username does not satisfy the rule
	 */
	public static String normalizeUsername(String username) {
		String normalized = normalizeIdentifier(username, "username");
		if (!USERNAME_PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("username must satisfy: " + USERNAME_RULE);
		}
		return normalized;
	}

	/**
	 * Whether {@link #normalizeUsername(String)} would accept the given input.
	 */
	public static boolean isValidUsername(String username) {
		return username != null && USERNAME_PATTERN.matcher(username.strip().toLowerCase(Locale.ROOT)).matches();
	}

	private static String normalizeIdentifier(String value, String name) {
		Objects.requireNonNull(value, name + " must not be null");
		String normalized = value.strip().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return normalized;
	}

	// Optional profile text: blank means "not provided". Other content is stored as given.
	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	@PrePersist
	void onCreate() {
		Instant now = now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = now();
	}

	// Truncated to the precision of the DATETIME(6) columns so in-memory and stored values match.
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public void setEmail(String email) {
		this.email = normalizeEmail(email);
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = normalizeUsername(username);
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
	}

	public String getBio() {
		return this.bio;
	}

	public void setBio(String bio) {
		this.bio = blankToNull(bio);
	}

	public String getLocation() {
		return this.location;
	}

	public void setLocation(String location) {
		this.location = blankToNull(location);
	}

	public String getWebsiteUrl() {
		return this.websiteUrl;
	}

	public void setWebsiteUrl(String websiteUrl) {
		this.websiteUrl = blankToNull(websiteUrl);
	}

	public String getGithubUrl() {
		return this.githubUrl;
	}

	public void setGithubUrl(String githubUrl) {
		this.githubUrl = blankToNull(githubUrl);
	}

	public String getLinkedinUrl() {
		return this.linkedinUrl;
	}

	public void setLinkedinUrl(String linkedinUrl) {
		this.linkedinUrl = blankToNull(linkedinUrl);
	}

	public UserRole getRole() {
		return this.role;
	}

	public void setRole(UserRole role) {
		this.role = Objects.requireNonNull(role, "role must not be null");
	}

	public UserStatus getStatus() {
		return this.status;
	}

	/**
	 * Trust-and-safety suspension, ACTIVE to SUSPENDED. For administrative moderation only.
	 * Nothing owned by the user is changed; visibility rules key off the account status.
	 * @throws IllegalStateException if the account is not ACTIVE
	 */
	public void suspend() {
		if (this.status != UserStatus.ACTIVE) {
			throw new IllegalStateException("Only an active account can be suspended");
		}
		this.status = UserStatus.SUSPENDED;
	}

	/**
	 * Lifts a suspension, SUSPENDED to ACTIVE. For administrative moderation only.
	 * @throws IllegalStateException if the account is not SUSPENDED
	 */
	public void restore() {
		if (this.status != UserStatus.SUSPENDED) {
			throw new IllegalStateException("Only a suspended account can be restored");
		}
		this.status = UserStatus.ACTIVE;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
