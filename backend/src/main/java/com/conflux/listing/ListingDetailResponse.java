package com.conflux.listing;

import java.math.BigDecimal;
import java.time.Instant;

public record ListingDetailResponse(Long id, String slug, String title, String shortPitch, String description,
		String problem, String solution, ListingAssetType assetType, ListingMarketplaceMode marketplaceMode,
		ListingCategory category, ListingStage stage, ListingStatus status, BigDecimal askingPrice, String currency,
		boolean priceNegotiable, String collaborationDetails, Instant publishedAt, Instant createdAt,
		Instant updatedAt, ListingOwnerResponse owner) {

	static ListingDetailResponse from(Listing listing) {
		return new ListingDetailResponse(listing.getId(), listing.getSlug(), listing.getTitle(),
				listing.getShortPitch(), listing.getDescription(), listing.getProblem(), listing.getSolution(),
				listing.getAssetType(), listing.getMarketplaceMode(), listing.getCategory(), listing.getStage(),
				listing.getStatus(), listing.getAskingPrice(), listing.getCurrency(), listing.isPriceNegotiable(),
				listing.getCollaborationDetails(), listing.getPublishedAt(), listing.getCreatedAt(),
				listing.getUpdatedAt(), ListingOwnerResponse.from(listing.getOwner()));
	}

}
