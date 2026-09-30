package com.conflux.auth;

import java.util.Optional;
import java.util.UUID;

import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserStatus;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registration, login and current-account lookup.
 */
@Service
public class AuthService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtTokenService tokenService;

	// Compared against when no user matches, so unknown identifiers cost the same as wrong passwords.
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * Creates an ACTIVE account with the USER role. The request must already be valid
	 * (see {@link RegisterRequest}); the password is hashed exactly as supplied.
	 * @throws ResponseStatusException 409 if the email or username is taken
	 */
	@Transactional
	public AccountResponse register(RegisterRequest request) {
		String email = User.normalizeEmail(request.email());
		String username = User.normalizeUsername(request.username());
		if (this.userRepository.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
		}
		if (this.userRepository.existsByUsername(username)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken.");
		}
		User user = new User(email, this.passwordEncoder.encode(request.password()), username,
				request.displayName());
		try {
			this.userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// A concurrent registration won the race for the same email or username.
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email or username is already in use.");
		}
		return AccountResponse.from(user);
	}

	/**
	 * Authenticates by email or username and issues an access token.
	 * @throws BadCredentialsException for an unknown identifier or a wrong password
	 * (indistinguishable to the caller)
	 * @throws LockedException if the credentials are correct but the account is suspended
	 */
	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
		// BCrypt ignores everything after 72 bytes, so without this check a registered
		// 72-byte password followed by anything would also match. No registered password
		// can be longer (see ValidPassword), so this is simply a wrong password.
		if (ValidPassword.Validator.utf8Length(request.password()) > ValidPassword.MAX_BYTES) {
			throw new BadCredentialsException("Invalid credentials");
		}
		Optional<User> candidate = findByIdentifier(request.identifier());
		String hash = candidate.map(User::getPasswordHash).orElse(this.dummyPasswordHash);
		boolean passwordMatches = this.passwordEncoder.matches(request.password(), hash);
		if (candidate.isEmpty() || !passwordMatches) {
			throw new BadCredentialsException("Invalid credentials");
		}
		User user = candidate.get();
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new LockedException("Account is suspended");
		}
		return this.tokenService.issueAccessToken(user);
	}

	/**
	 * Returns the account identified by the {@code sub} claim of a verified token.
	 * @throws InvalidBearerTokenException if the subject no longer matches an account
	 */
	@Transactional(readOnly = true)
	public AccountResponse currentAccount(String subject) {
		return parseUserId(subject).flatMap(this.userRepository::findById)
			.map(AccountResponse::from)
			.orElseThrow(() -> new InvalidBearerTokenException("Token subject does not match an account"));
	}

	// Usernames cannot contain '@', so the presence of '@' unambiguously means an email.
	private Optional<User> findByIdentifier(String identifier) {
		if (identifier.contains("@")) {
			return this.userRepository.findByEmail(User.normalizeEmail(identifier));
		}
		if (User.isValidUsername(identifier)) {
			return this.userRepository.findByUsername(User.normalizeUsername(identifier));
		}
		return Optional.empty();
	}

	private static Optional<Long> parseUserId(String subject) {
		try {
			return Optional.of(Long.valueOf(subject));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

}
