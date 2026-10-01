package com.conflux.user;

import com.conflux.auth.CurrentUser;
import com.conflux.ratelimit.RateLimitOperation;
import com.conflux.ratelimit.RateLimiter;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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

	private final RateLimiter rateLimiter;

	public UserService(UserRepository userRepository, CurrentUser currentUser, RateLimiter rateLimiter) {
		this.userRepository = userRepository;
		this.currentUser = currentUser;
		this.rateLimiter = rateLimiter;
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
	 * @throws com.conflux.ratelimit.RateLimitExceededException (429) if the user updated the
	 * profile too often recently
	 */
	@Transactional
	public UserProfileResponse updateProfile(UpdateProfileRequest request) {
		User user = currentActiveUser();
		user.setDisplayName(request.displayName());
		user.setBio(request.bio());
		user.setLocation(request.location());
		user.setWebsiteUrl(request.websiteUrl());
		user.setGithubUrl(request.githubUrl());
		user.setLinkedinUrl(request.linkedinUrl());
		// After the 403 check, before the flush; a 429 rolls the in-memory changes back.
		this.rateLimiter.acquire(RateLimitOperation.PROFILE_UPDATE, this.currentUser.id().toString());
		this.userRepository.saveAndFlush(user);
		return UserProfileResponse.from(user);
	}

	/**
	 * The authenticated administrator, re-checked against the database for every admin
	 * operation. Routes under {@code /api/v1/admin/**} already require {@code ROLE_ADMIN} in
	 * the token; this additionally stops a demoted or suspended admin whose token is still
	 * valid.
	 * @throws AccessDeniedException (403) if the account is no longer an administrator
	 * @throws ResponseStatusException 403 if the account is suspended
	 */
	public User currentActiveAdmin() {
		User user = currentAccount();
		if (user.getRole() != UserRole.ADMIN) {
			throw new AccessDeniedException("Administrator role required");
		}
		requireActive(user);
		return user;
	}

	/**
	 * The authenticated user, for write operations (suspended accounts cannot write).
	 * @throws ResponseStatusException 403 if the account is suspended
	 */
	public User currentActiveUser() {
		User user = currentAccount();
		requireActive(user);
		return user;
	}

	private static void requireActive(User user) {
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is suspended.");
		}
	}

	private User currentAccount() {
		return this.userRepository.findById(this.currentUser.id())
			.orElseThrow(() -> new InvalidBearerTokenException("Token subject does not match an account"));
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
	}

}
