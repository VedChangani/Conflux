package com.conflux.listing;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, Long> {

	Optional<Listing> findBySlug(String slug);

	boolean existsBySlug(String slug);

	@EntityGraph(attributePaths = "owner")
	@Query("""
			select l from Listing l
			where l.id = :id
			  and l.status = com.conflux.listing.ListingStatus.PUBLISHED
			  and l.owner.status = com.conflux.user.UserStatus.ACTIVE
			""")
	Optional<Listing> findPublicById(@Param("id") Long id);

	@EntityGraph(attributePaths = "owner")
	@Query("""
			select l from Listing l
			where l.slug = :slug
			  and l.status = com.conflux.listing.ListingStatus.PUBLISHED
			  and l.owner.status = com.conflux.user.UserStatus.ACTIVE
			""")
	Optional<Listing> findPublicBySlug(@Param("slug") String slug);

	@EntityGraph(attributePaths = "owner")
	Optional<Listing> findWithOwnerById(Long id);

	@EntityGraph(attributePaths = "owner")
	Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Optional<Listing> findBySlugAndStatus(String slug, ListingStatus status);

	@EntityGraph(attributePaths = "owner")
	@Query("""
			select l from Listing l
			where l.status = com.conflux.listing.ListingStatus.PUBLISHED
			  and l.owner.status = com.conflux.user.UserStatus.ACTIVE
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

	Page<Listing> findByOwnerId(Long ownerId, Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Optional<Listing> findByIdAndOwnerId(Long id, Long ownerId);

}
