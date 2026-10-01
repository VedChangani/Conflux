package com.conflux.saved;

import java.math.BigDecimal;
import java.time.Instant;

import com.conflux.listing.Listing;
import com.conflux.listing.ListingAssetType;
import com.conflux.listing.ListingCategory;
import com.conflux.listing.ListingMarketplaceMode;
import com.conflux.listing.ListingStage;
import com.conflux.user.User;

public record SavedListingResponse(Long id, String slug, String title, String shortPitch,
		ListingAssetType assetType, ListingMarketplaceMode marketplaceMode, ListingCategory category,
		ListingStage stage, BigDecimal askingPrice, String currency, boolean priceNegotiable, Instant publishedAt,
		Owner owner, Instant savedAt) {

	public record Owner(String username, String displayName) {

		static Owner from(User owner) {
			return new Owner(owner.getUsername(), owner.getDisplayName());
		}

	}

	static SavedListingResponse from(SavedListing saved) {
		Listing listing = saved.getListing();
		return new SavedListingResponse(listing.getId(), listing.getSlug(), listing.getTitle(),
				listing.getShortPitch(), listing.getAssetType(), listing.getMarketplaceMode(), listing.getCategory(),
				listing.getStage(), listing.getAskingPrice(), listing.getCurrency(), listing.isPriceNegotiable(),
				listing.getPublishedAt(), Owner.from(listing.getOwner()), saved.getCreatedAt());
	}

}
