package com.conflux.listing;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

public enum ListingSort {

	NEWEST(Sort.by(Order.desc("publishedAt"), Order.desc("id"))),

	OLDEST(Sort.by(Order.asc("publishedAt"), Order.asc("id"))),

	UPDATED(Sort.by(Order.desc("updatedAt"), Order.desc("id"))),

	PRICE_LOW(Sort.by(Order.asc("askingPrice").nullsLast(), Order.desc("publishedAt"), Order.desc("id"))),

	PRICE_HIGH(Sort.by(Order.desc("askingPrice").nullsLast(), Order.desc("publishedAt"), Order.desc("id")));

	public static final ListingSort DEFAULT = NEWEST;

	private final Sort sort;

	ListingSort(Sort sort) {
		this.sort = sort;
	}

	Sort toSort() {
		return this.sort;
	}

}
