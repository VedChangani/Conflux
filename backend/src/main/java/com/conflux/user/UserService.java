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

	@Transactional(readOnly = true)
	public UserProfileResponse publicProfile(String username) {
		if (!User.isValidUsername(username)) {
			throw notFound();
		}
		return this.userRepository.findByUsernameAndStatus(User.normalizeUsername(username), UserStatus.ACTIVE)
			.map(UserProfileResponse::from)
			.orElseThrow(UserService::notFound);
	}

	@Transactional(readOnly = true)
	public UserProfileResponse currentProfile() {
		return UserProfileResponse.from(currentAccount());
	}

	@Transactional
	public UserProfileResponse updateProfile(UpdateProfileRequest request) {
		User user = currentActiveUser();
		user.setDisplayName(request.displayName());
		user.setBio(request.bio());
		user.setLocation(request.location());
		user.setWebsiteUrl(request.websiteUrl());
		user.setGithubUrl(request.githubUrl());
		user.setLinkedinUrl(request.linkedinUrl());
		this.rateLimiter.acquire(RateLimitOperation.PROFILE_UPDATE, this.currentUser.id().toString());
		this.userRepository.saveAndFlush(user);
		return UserProfileResponse.from(user);
	}

	public User currentActiveAdmin() {
		User user = currentAccount();
		if (user.getRole() != UserRole.ADMIN) {
			throw new AccessDeniedException("Administrator role required");
		}
		requireActive(user);
		return user;
	}

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
