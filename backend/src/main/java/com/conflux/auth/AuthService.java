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

@Service
public class AuthService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtTokenService tokenService;

	private final CurrentUser currentUser;

	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
			CurrentUser currentUser) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.currentUser = currentUser;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

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
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email or username is already in use.");
		}
		return AccountResponse.from(user);
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
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

	@Transactional(readOnly = true)
	public AccountResponse currentAccount() {
		return this.userRepository.findById(this.currentUser.id())
			.map(AccountResponse::from)
			.orElseThrow(() -> new InvalidBearerTokenException("Token subject does not match an account"));
	}

	private Optional<User> findByIdentifier(String identifier) {
		if (identifier.contains("@")) {
			return this.userRepository.findByEmail(User.normalizeEmail(identifier));
		}
		if (User.isValidUsername(identifier)) {
			return this.userRepository.findByUsername(User.normalizeUsername(identifier));
		}
		return Optional.empty();
	}

}
