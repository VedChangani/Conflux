package com.conflux.user;

import com.conflux.auth.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * User profiles. Public profiles exist only for ACTIVE accounts; the own profile is always
 * the authenticated user's ({@link CurrentUser}). Each call is a single user query; no
 * listings, connections or messages are loaded.
 */
@Service
public class UserService {

	private final UserRepository userRepository;

	private final CurrentUser currentUser;

	public UserService(UserRepository userRepository, CurrentUser currentUser) {
		this.userRepository = userRepository;
		this.currentUser = currentUser;
	}

	/**
	 * The public profile for a username (matched with the usual username normalization, so
	 * {@code Alice} finds {@code alice}).
	 * @throws ResponseStatusException 404 if there is no ACTIVE account with that username;
	 * suspended and nonexistent accounts are indistinguishable
	 */
	@Transactional(readOnly = true)
	public UserProfileResponse publicProfile(String username) {
		if (!User.isValidUsername(username)) {
			throw notFound();
		}
		return this.userRepository.findByUsernameAndStatus(User.normalizeUsername(username), UserStatus.ACTIVE)
			.map(UserProfileResponse::from)
			.orElseThrow(UserService::notFound);
	}

	/**
	 * The authenticated user's own profile, whatever their account status.
	 */
	@Transactional(readOnly = true)
	public UserProfileResponse currentProfile() {
		return UserProfileResponse.from(currentAccount());
	}

	/**
	 * Replaces the authenticated user's editable profile fields. Username, email, role and
	 * status cannot change here.
	 * @throws ResponseStatusException 403 for a suspended account
	 */
	@Transactional
	public UserProfileResponse updateProfile(UpdateProfileRequest request) {
		User user = currentAccount();
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is suspended.");
		}
		user.setDisplayName(request.displayName());
		user.setBio(request.bio());
		user.setLocation(request.location());
		user.setWebsiteUrl(request.websiteUrl());
		user.setGithubUrl(request.githubUrl());
		user.setLinkedinUrl(request.linkedinUrl());
		this.userRepository.saveAndFlush(user);
		return UserProfileResponse.from(user);
	}

	private User currentAccount() {
		return this.userRepository.findById(this.currentUser.id())
			.orElseThrow(() -> new InvalidBearerTokenException("Token subject does not match an account"));
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
	}

}
