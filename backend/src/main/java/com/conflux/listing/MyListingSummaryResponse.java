package com.conflux.listing;

import java.math.BigDecimal;
import java.time.Instant;

public record MyListingSummaryResponse(Long id, String slug, String title, String shortPitch,
		ListingAssetType assetType, ListingMarketplaceMode marketplaceMode, ListingCategory category,
		ListingStage stage, ListingStatus status, BigDecimal askingPrice, String currency, boolean priceNegotiable,
		Instant publishedAt, Instant createdAt, Instant updatedAt) {

	static MyListingSummaryResponse from(Listing listing) {
		return new MyListingSummaryResponse(listing.getId(), listing.getSlug(), listing.getTitle(),
				listing.getShortPitch(), listing.getAssetType(), listing.getMarketplaceMode(), listing.getCategory(),
				listing.getStage(), listing.getStatus(), listing.getAskingPrice(), listing.getCurrency(),
				listing.isPriceNegotiable(), listing.getPublishedAt(), listing.getCreatedAt(),
				listing.getUpdatedAt());
	}

}
