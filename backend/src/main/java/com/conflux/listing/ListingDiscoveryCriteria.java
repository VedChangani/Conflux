package com.conflux.listing;

public record ListingDiscoveryCriteria(String search, ListingAssetType assetType,
		ListingMarketplaceMode marketplaceMode, ListingCategory category, ListingStage stage, ListingSort sort) {

	public static final int SEARCH_MAX_LENGTH = 100;

}
