package com.conflux.saved;

import com.conflux.auth.CurrentUser;
import com.conflux.common.web.PageResponse;
import com.conflux.listing.Listing;
import com.conflux.listing.ListingRepository;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserStatus;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Saving (bookmarking) marketplace listings. The acting user always comes from the
 * verified JWT ({@link CurrentUser}); nothing here accepts a user id from the caller.
 */
@Service
public class SavedListingService {

	private static final Sort NEWEST_SAVED_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final SavedListingRepository savedListingRepository;

	private final ListingRepository listingRepository;

	private final UserRepository userRepository;

	private final CurrentUser currentUser;

	public SavedListingService(SavedListingRepository savedListingRepository, ListingRepository listingRepository,
			UserRepository userRepository, CurrentUser currentUser) {
		this.savedListingRepository = savedListingRepository;
		this.listingRepository = listingRepository;
		this.userRepository = userRepository;
		this.currentUser = currentUser;
	}

	/**
	 * Saves a PUBLISHED listing for the current user. Saving an already saved listing does
	 * nothing. Owners may save their own listings.
	 * <p>
	 * Deliberately not one transaction: if a concurrent request inserts the same pair first,
	 * the unique constraint fails only the insert's own transaction, and the outcome is the
	 * same as a normal repeated save.
	 * @throws ResponseStatusException 404 unless the listing exists and is PUBLISHED
	 */
	public void save(Long listingId) {
		User user = activeUser();
		Listing listing = this.listingRepository.findPublicById(listingId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
		if (this.savedListingRepository.existsByUserIdAndListingId(user.getId(), listing.getId())) {
			return;
		}
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
	 */
	@Transactional
	public void unsave(Long listingId) {
		User user = activeUser();
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

	// Same rule as listing management: suspended accounts cannot change anything.
	private User activeUser() {
		User user = this.userRepository.findById(this.currentUser.id())
			.orElseThrow(() -> new InvalidBearerTokenException("Token subject does not match an account"));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is suspended.");
		}
		return user;
	}

}
