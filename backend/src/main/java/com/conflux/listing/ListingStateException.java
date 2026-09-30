package com.conflux.listing;

/**
 * An operation is not allowed in the listing's current lifecycle status.
 */
public class ListingStateException extends RuntimeException {

	public ListingStateException(String message) {
		super(message);
	}

}
