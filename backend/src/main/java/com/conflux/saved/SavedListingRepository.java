package com.conflux.saved;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedListingRepository extends JpaRepository<SavedListing, Long> {

	boolean existsByUserIdAndListingId(Long userId, Long listingId);

	Optional<SavedListing> findByUserIdAndListingId(Long userId, Long listingId);

	void deleteByUserIdAndListingId(Long userId, Long listingId);

	@EntityGraph(attributePaths = { "listing", "listing.owner" })
	@Query("""
			select s from SavedListing s
			where s.user.id = :userId
			  and s.listing.status = com.conflux.listing.ListingStatus.PUBLISHED
			  and s.listing.owner.status = com.conflux.user.UserStatus.ACTIVE
			""")
	Page<SavedListing> findPublishedByUserId(@Param("userId") Long userId, Pageable pageable);

}
