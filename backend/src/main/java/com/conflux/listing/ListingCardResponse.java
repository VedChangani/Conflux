package com.conflux.listing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight published listing for marketplace browsing. Long text fields
 * (description, problem, solution, collaboration details) are intentionally omitted.
 */
public record ListingCardResponse(Long id, String slug, String title, String shortPitch,
		ListingAssetType assetType, ListingMarketplaceMode marketplaceMode, ListingCategory category,
		ListingStage stage, BigDecimal askingPrice, String currency, boolean priceNegotiable, Instant publishedAt,
		ListingOwnerResponse owner) {

	/**
	 * Must be called while the listing's owner is loaded (inside the transaction).
	 */
	static ListingCardResponse from(Listing listing) {
		return new ListingCardResponse(listing.getId(), listing.getSlug(), listing.getTitle(),
				listing.getShortPitch(), listing.getAssetType(), listing.getMarketplaceMode(), listing.getCategory(),
				listing.getStage(), listing.getAskingPrice(), listing.getCurrency(), listing.isPriceNegotiable(),
				listing.getPublishedAt(), ListingOwnerResponse.from(listing.getOwner()));
	}

}
