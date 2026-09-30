package com.conflux.saved;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link SavedListing}. Every method is scoped to one user.
 */
public interface SavedListingRepository extends JpaRepository<SavedListing, Long> {

	boolean existsByUserIdAndListingId(Long userId, Long listingId);

	Optional<SavedListing> findByUserIdAndListingId(Long userId, Long listingId);

	void deleteByUserIdAndListingId(Long userId, Long listingId);

	/**
	 * The user's saves whose listing is currently PUBLISHED (the status is fixed in the
	 * query), with the listing and its owner fetched in the same query. Saves of listings
	 * that are no longer public are kept but not returned.
	 */
	@EntityGraph(attributePaths = { "listing", "listing.owner" })
	@Query("""
			select s from SavedListing s
			where s.user.id = :userId
			  and s.listing.status = com.conflux.listing.ListingStatus.PUBLISHED
			""")
	Page<SavedListing> findPublishedByUserId(@Param("userId") Long userId, Pageable pageable);

}
