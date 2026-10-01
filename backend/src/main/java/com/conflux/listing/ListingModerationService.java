package com.conflux.listing;

import com.conflux.ratelimit.RateLimitOperation;
import com.conflux.ratelimit.RateLimiter;
import com.conflux.report.ModerationActionService;
import com.conflux.report.ModerationActionType;
import com.conflux.user.User;
import com.conflux.user.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ListingModerationService {

	private final ListingRepository listingRepository;

	private final UserService userService;

	private final ModerationActionService moderationActionService;

	private final EntityManager entityManager;

	private final RateLimiter rateLimiter;

	public ListingModerationService(ListingRepository listingRepository, UserService userService,
			ModerationActionService moderationActionService, EntityManager entityManager, RateLimiter rateLimiter) {
		this.listingRepository = listingRepository;
		this.userService = userService;
		this.moderationActionService = moderationActionService;
		this.entityManager = entityManager;
		this.rateLimiter = rateLimiter;
	}

	@Transactional
	public void suspend(Long listingId) {
		User admin = this.userService.currentActiveAdmin();
		Listing listing = lockedListing(listingId);
		if (listing.getStatus() != ListingStatus.SUSPENDED) {
			transition(listing::suspend);
			this.rateLimiter.acquire(RateLimitOperation.ADMIN_MODERATION, admin.getId().toString());
			this.moderationActionService.recordDirect(admin, ModerationActionType.SUSPEND_LISTING, listing.getId());
		}
	}

	@Transactional
	public void restore(Long listingId) {
		User admin = this.userService.currentActiveAdmin();
		Listing listing = lockedListing(listingId);
		if (listing.getStatus() != ListingStatus.PUBLISHED) {
			transition(listing::restore);
			this.rateLimiter.acquire(RateLimitOperation.ADMIN_MODERATION, admin.getId().toString());
			this.moderationActionService.recordDirect(admin, ModerationActionType.RESTORE_LISTING, listing.getId());
		}
	}

	private Listing lockedListing(Long listingId) {
		Listing listing = listing(listingId);
		this.entityManager.refresh(listing, LockModeType.PESSIMISTIC_WRITE);
		return listing;
	}

	private Listing listing(Long listingId) {
		return this.listingRepository.findById(listingId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
	}

	private static void transition(Runnable transition) {
		try {
			transition.run();
		}
		catch (ListingStateException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
		}
	}

}
