package com.conflux.listing;

import com.conflux.user.User;

public record ListingOwnerResponse(Long id, String username, String displayName) {

	static ListingOwnerResponse from(User owner) {
		return new ListingOwnerResponse(owner.getId(), owner.getUsername(), owner.getDisplayName());
	}

}
