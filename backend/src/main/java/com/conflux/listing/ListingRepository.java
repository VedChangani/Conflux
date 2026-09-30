package com.conflux.listing;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link Listing}. Methods whose results are mapped together with owner
 * data fetch the owner in the same query ({@code @EntityGraph}), because open-in-view is
 * disabled and N+1 lazy loading must be avoided.
 */
public interface ListingRepository extends JpaRepository<Listing, Long> {

	Optional<Listing> findBySlug(String slug);

	boolean existsBySlug(String slug);

	/**
	 * A listing only if it is in the given status (e.g. PUBLISHED for public actions).
	 */
	Optional<Listing> findByIdAndStatus(Long id, ListingStatus status);

	@EntityGraph(attributePaths = "owner")
	Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Optional<Listing> findBySlugAndStatus(String slug, ListingStatus status);

	/**
	 * Public marketplace discovery. Only PUBLISHED listings can ever match: the status is
	 * fixed in the query, not a parameter. Every other condition is optional ({@code null}
	 * = not applied) and they are combined with AND.
	 * @param searchPattern lower-case LIKE pattern (already wrapped in {@code %} and with
	 * {@code !}, {@code %} and {@code _} escaped using {@code !}), matched against title,
	 * short pitch and description; {@code null} for no text search
	 */
	@EntityGraph(attributePaths = "owner")
	@Query("""
			select l from Listing l
			where l.status = com.conflux.listing.ListingStatus.PUBLISHED
			  and (:searchPattern is null
			       or lower(l.title) like :searchPattern escape '!'
			       or lower(l.shortPitch) like :searchPattern escape '!'
			       or lower(l.description) like :searchPattern escape '!')
			  and (:assetType is null or l.assetType = :assetType)
			  and (:marketplaceMode is null or l.marketplaceMode = :marketplaceMode)
			  and (:category is null or l.category = :category)
			  and (:stage is null or l.stage = :stage)
			""")
	Page<Listing> findPublished(@Param("searchPattern") String searchPattern,
			@Param("assetType") ListingAssetType assetType,
			@Param("marketplaceMode") ListingMarketplaceMode marketplaceMode,
			@Param("category") ListingCategory category, @Param("stage") ListingStage stage, Pageable pageable);

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
