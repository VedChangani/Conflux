package com.conflux.listing;

/**
 * Optional public marketplace filters, combined with AND. {@code null} means "no
 * filter"; {@code sort} {@code null} means {@link ListingSort#DEFAULT}.
 *
 * @param search free text matched case-insensitively against title, short pitch and
 * description; blank means no search
 */
public record ListingDiscoveryCriteria(String search, ListingAssetType assetType,
		ListingMarketplaceMode marketplaceMode, ListingCategory category, ListingStage stage, ListingSort sort) {

	/** Maximum accepted length of {@code search}. */
	public static final int SEARCH_MAX_LENGTH = 100;

}
