package com.conflux.listing;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link Listing}. Methods whose results are mapped together with owner
 * data fetch the owner in the same query ({@code @EntityGraph}), because open-in-view is
 * disabled and N+1 lazy loading must be avoided.
 */
public interface ListingRepository extends JpaRepository<Listing, Long> {

	Optional<Listing> findBySlug(String slug);

	boolean existsBySlug(String slug);

	@EntityGraph(attributePaths = "owner")
	Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Optional<Listing> findBySlugAndStatus(String slug, ListingStatus status);

	/**
	 * The owner's listings; owner data is not needed for these results, so it is not fetched.
	 */
	Page<Listing> findByOwnerId(Long ownerId, Pageable pageable);

	/**
	 * A listing only if it belongs to the given owner, so other users' listings are
	 * indistinguishable from missing ones.
	 */
	@EntityGraph(attributePaths = "owner")
	Optional<Listing> findByIdAndOwnerId(Long id, Long ownerId);

}
