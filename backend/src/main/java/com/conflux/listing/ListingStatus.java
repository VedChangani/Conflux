package com.conflux.listing;

/**
 * Marketplace lifecycle status of a listing. Owners publish directly; there is no review
 * or approval step.
 * <p>
 * Transitions (implemented in {@link Listing}):
 * <ul>
 * <li>publish (owner): {@code DRAFT -> PUBLISHED} (sets {@code publishedAt})</li>
 * <li>archive (owner): {@code DRAFT | PUBLISHED -> ARCHIVED} (keeps {@code publishedAt})</li>
 * <li>suspend (future trust-and-safety): {@code PUBLISHED -> SUSPENDED}</li>
 * </ul>
 * Only PUBLISHED listings are publicly visible.
 */
public enum ListingStatus {

	DRAFT,

	PUBLISHED,

	ARCHIVED,

	SUSPENDED

}
