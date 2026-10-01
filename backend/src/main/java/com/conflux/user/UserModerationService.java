package com.conflux.user;

import com.conflux.ratelimit.RateLimitOperation;
import com.conflux.ratelimit.RateLimiter;
import com.conflux.report.ModerationActionService;
import com.conflux.report.ModerationActionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reactive account moderation by administrators. Suspending changes only the account
 * status: listings, saves, connections, conversations and messages are kept untouched, and
 * public visibility follows from the status (so restoring brings everything back).
 * Repeating an operation is a no-op. Each actual status change is recorded in the
 * moderation audit log in the same transaction; no-ops and failures are not. Only actual
 * changes count toward the acting admin's ADMIN_MODERATION rate limit (429), checked after
 * every 403/404/409 check.
 */
@Service
public class UserModerationService {

	private final UserRepository userRepository;

	private final UserService userService;

	private final ModerationActionService moderationActionService;

	private final EntityManager entityManager;

	private final RateLimiter rateLimiter;

	public UserModerationService(UserRepository userRepository, UserService userService,
			ModerationActionService moderationActionService, EntityManager entityManager, RateLimiter rateLimiter) {
		this.userRepository = userRepository;
		this.userService = userService;
		this.moderationActionService = moderationActionService;
		this.entityManager = entityManager;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * ACTIVE to SUSPENDED, recorded as SUSPEND_USER; already SUSPENDED is left as is.
	 * @throws ResponseStatusException 404 if the user does not exist, 409 for the acting admin
	 */
	@Transactional
	public void suspend(Long userId) {
		User admin = this.userService.currentActiveAdmin();
		if (admin.getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot suspend your own account.");
		}
		User user = lockedUser(userId);
		if (user.getStatus() == UserStatus.ACTIVE) {
			this.rateLimiter.acquire(RateLimitOperation.ADMIN_MODERATION, admin.getId().toString());
			user.suspend();
			this.moderationActionService.recordDirect(admin, ModerationActionType.SUSPEND_USER, user.getId());
		}
	}

	/**
	 * SUSPENDED to ACTIVE, recorded as RESTORE_USER; already ACTIVE is left as is.
	 * @throws ResponseStatusException 404 if the user does not exist
	 */
	@Transactional
	public void restore(Long userId) {
		User admin = this.userService.currentActiveAdmin();
		User user = lockedUser(userId);
		if (user.getStatus() == UserStatus.SUSPENDED) {
			this.rateLimiter.acquire(RateLimitOperation.ADMIN_MODERATION, admin.getId().toString());
			user.restore();
			this.moderationActionService.recordDirect(admin, ModerationActionType.RESTORE_USER, user.getId());
		}
	}

	/**
	 * Locks the account row and re-reads it, so concurrent calls on the same account run one
	 * after another and only the first one changes (and records) anything.
	 */
	private User lockedUser(Long userId) {
		User user = user(userId);
		this.entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
		return user;
	}

	private User user(Long userId) {
		return this.userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
	}

}
