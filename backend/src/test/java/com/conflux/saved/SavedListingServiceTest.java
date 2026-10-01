package com.conflux.saved;

import java.util.Optional;

import com.conflux.auth.CurrentUser;
import com.conflux.listing.Listing;
import com.conflux.listing.ListingAssetType;
import com.conflux.listing.ListingCategory;
import com.conflux.listing.ListingMarketplaceMode;
import com.conflux.listing.ListingRepository;
import com.conflux.listing.ListingStage;
import com.conflux.ratelimit.RateLimiter;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserService;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Save paths that cannot be triggered deterministically over HTTP.
 */
class SavedListingServiceTest {

	private final SavedListingRepository savedListingRepository = mock(SavedListingRepository.class);

	private final ListingRepository listingRepository = mock(ListingRepository.class);

	private final UserRepository userRepository = mock(UserRepository.class);

	private final CurrentUser currentUser = mock(CurrentUser.class);

	private final SavedListingService service = new SavedListingService(this.savedListingRepository,
			this.listingRepository, new UserService(this.userRepository, this.currentUser, mock(RateLimiter.class)),
			this.currentUser, mock(RateLimiter.class));

	@Test
	void losingARaceAgainstAnIdenticalSaveIsTheSameAsARepeatedSave() {
		givenPublishedListingAndActiveUser();
		given(this.savedListingRepository.existsByUserIdAndListingId(any(), any())).willReturn(false);
		given(this.savedListingRepository.saveAndFlush(any(SavedListing.class)))
			.willThrow(new DataIntegrityViolationException("uk_saved_listings_user_listing ... SQL"));

		assertThatNoException().isThrownBy(() -> this.service.save(10L));
	}

	@Test
	void alreadySavedListingIsNotInsertedAgain() {
		givenPublishedListingAndActiveUser();
		given(this.savedListingRepository.existsByUserIdAndListingId(any(), any())).willReturn(true);

		this.service.save(10L);

		verify(this.savedListingRepository, never()).saveAndFlush(any());
	}

	private void givenPublishedListingAndActiveUser() {
		User user = new User("a@example.com", "hash", "alice", "Alice");
		given(this.currentUser.id()).willReturn(1L);
		given(this.userRepository.findById(1L)).willReturn(Optional.of(user));
		Listing listing = new Listing(user, "Title", "title-0000abcd", "Pitch", "Description", ListingAssetType.IDEA,
				ListingMarketplaceMode.ACQUIRE, ListingCategory.AI, ListingStage.CONCEPT);
		listing.publish();
		given(this.listingRepository.findPublicById(anyLong())).willReturn(Optional.of(listing));
	}

}
