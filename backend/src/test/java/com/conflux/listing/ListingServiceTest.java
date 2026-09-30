package com.conflux.listing;

import java.util.Optional;

import com.conflux.user.User;
import com.conflux.user.UserRepository;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Slug collision paths that cannot be triggered deterministically over HTTP.
 */
class ListingServiceTest {

	private final ListingRepository listingRepository = mock(ListingRepository.class);

	private final UserRepository userRepository = mock(UserRepository.class);

	private final SlugGenerator slugGenerator = mock(SlugGenerator.class);

	private final ListingService service = new ListingService(this.listingRepository, this.userRepository,
			this.slugGenerator);

	@Test
	void uniqueConstraintViolationAfterPassedExistenceCheckBecomes409() {
		given(this.userRepository.findById(1L))
			.willReturn(Optional.of(new User("a@example.com", "hash", "alice", "Alice")));
		given(this.slugGenerator.generate(anyString())).willReturn("title-0000abcd");
		given(this.listingRepository.existsBySlug("title-0000abcd")).willReturn(false);
		given(this.listingRepository.saveAndFlush(any(Listing.class)))
			.willThrow(new DataIntegrityViolationException("Unique index violation: uk_listings_slug ... SQL ..."));

		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> this.service.create(1L, request()))
			.satisfies(ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(ex.getReason()).doesNotContain("uk_listings_slug").doesNotContain("SQL");
			});
	}

	@Test
	void existingSlugIsRegeneratedAndGivesUpWith409AfterMaxAttempts() {
		given(this.userRepository.findById(1L))
			.willReturn(Optional.of(new User("a@example.com", "hash", "alice", "Alice")));
		given(this.slugGenerator.generate(anyString())).willReturn("title-0000abcd");
		given(this.listingRepository.existsBySlug("title-0000abcd")).willReturn(true);

		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> this.service.create(1L, request()))
			.satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
		verify(this.slugGenerator, times(ListingService.MAX_SLUG_ATTEMPTS)).generate(anyString());
	}

	private static ListingRequest request() {
		return new ListingRequest("Title", "Pitch", "Description", null, null, ListingAssetType.IDEA,
				ListingMarketplaceMode.ACQUIRE, ListingCategory.AI, ListingStage.CONCEPT, null, null, null, null);
	}

}
