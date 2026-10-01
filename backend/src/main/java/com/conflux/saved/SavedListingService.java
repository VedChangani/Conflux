package com.conflux.saved;

import com.conflux.auth.CurrentUser;
import com.conflux.common.web.PageResponse;
import com.conflux.listing.Listing;
import com.conflux.listing.ListingRepository;
import com.conflux.ratelimit.RateLimitOperation;
import com.conflux.ratelimit.RateLimiter;
import com.conflux.user.User;
import com.conflux.user.UserService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Saving (bookmarking) marketplace listings. The acting user always comes from the
 * verified JWT ({@link CurrentUser}); nothing here accepts a user id from the caller.
 * Saving and unsaving are idempotent: a request that changes nothing is answered normally
 * and does not count toward the user's rate limit (429), which is checked only after the
 * 403/404 checks.
 */
@Service
public class SavedListingService {

	private static final Sort NEWEST_SAVED_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final SavedListingRepository savedListingRepository;

	private final ListingRepository listingRepository;

	private final UserService userService;

	private final CurrentUser currentUser;

	private final RateLimiter rateLimiter;

	public SavedListingService(SavedListingRepository savedListingRepository, ListingRepository listingRepository,
			UserService userService, CurrentUser currentUser, RateLimiter rateLimiter) {
		this.savedListingRepository = savedListingRepository;
		this.listingRepository = listingRepository;
		this.userService = userService;
		this.currentUser = currentUser;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * Saves a PUBLISHED listing for the current user. Saving an already saved listing does
	 * nothing. Owners may save their own listings.
	 * <p>
	 * Deliberately not one transaction: if a concurrent request inserts the same pair first,
	 * the unique constraint fails only the insert's own transaction, and the outcome is the
	 * same as a normal repeated save.
	 * @throws ResponseStatusException 404 unless the listing exists and is PUBLISHED
	 * @throws com.conflux.ratelimit.RateLimitExceededException (429) if the user saved too
	 * often recently
	 */
	public void save(Long listingId) {
		User user = this.userService.currentActiveUser();
		Listing listing = this.listingRepository.findPublicById(listingId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
		if (this.savedListingRepository.existsByUserIdAndListingId(user.getId(), listing.getId())) {
			return;
		}
		this.rateLimiter.acquire(RateLimitOperation.LISTING_SAVE, this.currentUser.id().toString());
		try {
			this.savedListingRepository.saveAndFlush(new SavedListing(user, listing));
		}
		catch (DataIntegrityViolationException ex) {
			// Lost a race against an identical save: the listing is saved, which is what was asked.
		}
	}

	/**
	 * Removes the current user's save of the listing, if any. Unsaving something that is not
	 * saved (or does not exist) does nothing; the listing's status does not matter, so a save
	 * of a listing that is no longer public can still be removed.
	 * @throws com.conflux.ratelimit.RateLimitExceededException (429) if the user unsaved too
	 * often recently
	 */
	@Transactional
	public void unsave(Long listingId) {
		User user = this.userService.currentActiveUser();
		if (!this.savedListingRepository.existsByUserIdAndListingId(user.getId(), listingId)) {
			return;
		}
		this.rateLimiter.acquire(RateLimitOperation.LISTING_UNSAVE, this.currentUser.id().toString());
		this.savedListingRepository.deleteByUserIdAndListingId(user.getId(), listingId);
	}

	/**
	 * The current user's saved listings that are currently PUBLISHED, most recently saved
	 * first.
	 */
	@Transactional(readOnly = true)
	public PageResponse<SavedListingResponse> savedListings(int page, int size) {
		return PageResponse.from(this.savedListingRepository
			.findPublishedByUserId(this.currentUser.id(), PageRequest.of(page, size, NEWEST_SAVED_FIRST))
			.map(SavedListingResponse::from));
	}

}
