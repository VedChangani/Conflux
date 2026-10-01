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
		}
	}

	@Transactional
	public void unsave(Long listingId) {
		User user = this.userService.currentActiveUser();
		if (!this.savedListingRepository.existsByUserIdAndListingId(user.getId(), listingId)) {
			return;
		}
		this.rateLimiter.acquire(RateLimitOperation.LISTING_UNSAVE, this.currentUser.id().toString());
		this.savedListingRepository.deleteByUserIdAndListingId(user.getId(), listingId);
	}

	@Transactional(readOnly = true)
	public PageResponse<SavedListingResponse> savedListings(int page, int size) {
		return PageResponse.from(this.savedListingRepository
			.findPublishedByUserId(this.currentUser.id(), PageRequest.of(page, size, NEWEST_SAVED_FIRST))
			.map(SavedListingResponse::from));
	}

}
