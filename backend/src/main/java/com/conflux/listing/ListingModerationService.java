package com.conflux.listing;

import com.conflux.user.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reactive listing moderation by administrators: suspending a PUBLISHED listing hides it,
 * restoring makes it public again. There is no approval step; owners publish directly.
 * Repeating an operation is a no-op. The listing is never deleted.
 */
@Service
public class ListingModerationService {

	private final ListingRepository listingRepository;

	private final UserService userService;

	public ListingModerationService(ListingRepository listingRepository, UserService userService) {
		this.listingRepository = listingRepository;
		this.userService = userService;
	}

	/**
	 * PUBLISHED to SUSPENDED; already SUSPENDED is left as is.
	 * @throws ResponseStatusException 404 if the listing does not exist, 409 if DRAFT or ARCHIVED
	 */
	@Transactional
	public void suspend(Long listingId) {
		this.userService.currentActiveAdmin();
		Listing listing = listing(listingId);
		if (listing.getStatus() != ListingStatus.SUSPENDED) {
			transition(listing::suspend);
		}
	}

	/**
	 * SUSPENDED back to PUBLISHED; already PUBLISHED is left as is.
	 * @throws ResponseStatusException 404 if the listing does not exist, 409 if DRAFT or ARCHIVED
	 */
	@Transactional
	public void restore(Long listingId) {
		this.userService.currentActiveAdmin();
		Listing listing = listing(listingId);
		if (listing.getStatus() != ListingStatus.PUBLISHED) {
			transition(listing::restore);
		}
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
