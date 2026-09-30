package com.conflux.listing;

/**
 * Test fixture: brings a new (DRAFT) listing into a given status using only the real
 * lifecycle methods, since the entity has no status setter.
 */
final class ListingTestStates {

	private ListingTestStates() {
	}

	static Listing moveTo(Listing listing, ListingStatus target) {
		if (listing.getStatus() != ListingStatus.DRAFT) {
			throw new IllegalArgumentException("expected a DRAFT listing");
		}
		switch (target) {
			case DRAFT -> {
			}
			case PUBLISHED -> listing.publish();
			case ARCHIVED -> listing.archive();
			case SUSPENDED -> {
				listing.publish();
				listing.suspend();
			}
		}
		return listing;
	}

}
