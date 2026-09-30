package com.conflux.listing;

/**
 * Marketplace lifecycle / moderation status of a listing.
 * <p>
 * Owner transitions (implemented in {@link Listing}):
 * <ul>
 * <li>submit: {@code DRAFT | REJECTED -> PENDING_REVIEW}</li>
 * <li>edit: {@code PUBLISHED -> PENDING_REVIEW} (DRAFT and REJECTED stay unchanged)</li>
 * <li>archive: {@code DRAFT | REJECTED | PENDING_REVIEW | PUBLISHED -> ARCHIVED}</li>
 * </ul>
 * Moderation transitions ({@code PENDING_REVIEW -> PUBLISHED | REJECTED},
 * {@code PUBLISHED -> SUSPENDED}) belong to a later batch.
 */
public enum ListingStatus {

	DRAFT,

	PENDING_REVIEW,

	PUBLISHED,

	REJECTED,

	ARCHIVED,

	SUSPENDED

}
