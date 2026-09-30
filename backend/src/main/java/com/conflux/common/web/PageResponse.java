package com.conflux.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Stable JSON shape for a page of results (Spring Data's {@link Page} is not serialized
 * directly).
 *
 * @param content items of this page
 * @param page zero-based page number
 * @param size requested page size
 * @param totalElements total number of items across all pages
 * @param totalPages total number of pages
 * @param first whether this is the first page
 * @param last whether this is the last page
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
		boolean first, boolean last) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages(), page.isFirst(), page.isLast());
	}

}
