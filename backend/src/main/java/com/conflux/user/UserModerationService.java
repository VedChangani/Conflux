package com.conflux.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reactive account moderation by administrators. Suspending changes only the account
 * status: listings, saves, connections, conversations and messages are kept untouched, and
 * public visibility follows from the status (so restoring brings everything back).
 * Repeating an operation is a no-op.
 */
@Service
public class UserModerationService {

	private final UserRepository userRepository;

	private final UserService userService;

	public UserModerationService(UserRepository userRepository, UserService userService) {
		this.userRepository = userRepository;
		this.userService = userService;
	}

	/**
	 * ACTIVE to SUSPENDED; already SUSPENDED is left as is.
	 * @throws ResponseStatusException 404 if the user does not exist, 409 for the acting admin
	 */
	@Transactional
	public void suspend(Long userId) {
		User admin = this.userService.currentActiveAdmin();
		if (admin.getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot suspend your own account.");
		}
		User user = user(userId);
		if (user.getStatus() == UserStatus.ACTIVE) {
			user.suspend();
		}
	}

	/**
	 * SUSPENDED to ACTIVE; already ACTIVE is left as is.
	 * @throws ResponseStatusException 404 if the user does not exist
	 */
	@Transactional
	public void restore(Long userId) {
		this.userService.currentActiveAdmin();
		User user = user(userId);
		if (user.getStatus() == UserStatus.SUSPENDED) {
			user.restore();
		}
	}

	private User user(Long userId) {
		return this.userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
	}

}
