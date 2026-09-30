package com.conflux.listing;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

/**
 * The only orderings the public marketplace accepts; clients can never name a field.
 * Every ordering ends with {@code id} so results are deterministic. Listings without an
 * asking price are always placed after priced ones for both price orderings (they are
 * neither the cheapest nor the most expensive).
 */
public enum ListingSort {

	/** Most recently published first. */
	NEWEST(Sort.by(Order.desc("publishedAt"), Order.desc("id"))),

	/** Earliest published first. */
	OLDEST(Sort.by(Order.asc("publishedAt"), Order.asc("id"))),

	/** Most recently updated first. */
	UPDATED(Sort.by(Order.desc("updatedAt"), Order.desc("id"))),

	/** Cheapest first; unpriced listings last, newest first among equal prices. */
	PRICE_LOW(Sort.by(Order.asc("askingPrice").nullsLast(), Order.desc("publishedAt"), Order.desc("id"))),

	/** Most expensive first; unpriced listings last, newest first among equal prices. */
	PRICE_HIGH(Sort.by(Order.desc("askingPrice").nullsLast(), Order.desc("publishedAt"), Order.desc("id")));

	/** Used when the client does not choose an ordering. */
	public static final ListingSort DEFAULT = NEWEST;

	private final Sort sort;

	ListingSort(Sort sort) {
		this.sort = sort;
	}

	Sort toSort() {
		return this.sort;
	}

}
