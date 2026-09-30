package com.conflux.connection;

import java.time.Instant;

import com.conflux.listing.Listing;
import com.conflux.listing.ListingStatus;
import com.conflux.user.User;

/**
 * A connection as seen by one of its two participants. Contains no emails, credentials or
 * security data.
 *
 * @param listing the listing it is about; {@code listing.status} tells whether the
 * opportunity is still public
 */
public record ConnectionResponse(Long id, ConnectionStatus status, Instant createdAt, Instant updatedAt,
		ListingSummary listing, Participant requester, Participant owner) {

	public record ListingSummary(Long id, String slug, String title, String shortPitch, ListingStatus status) {

		static ListingSummary from(Listing listing) {
			return new ListingSummary(listing.getId(), listing.getSlug(), listing.getTitle(), listing.getShortPitch(),
					listing.getStatus());
		}

	}

	public record Participant(Long id, String username, String displayName) {

		static Participant from(User user) {
			return new Participant(user.getId(), user.getUsername(), user.getDisplayName());
		}

	}

	/**
	 * Must be called while the listing, its owner and the requester are loaded.
	 */
	static ConnectionResponse from(Connection connection) {
		return new ConnectionResponse(connection.getId(), connection.getStatus(), connection.getCreatedAt(),
				connection.getUpdatedAt(), ListingSummary.from(connection.getListing()),
				Participant.from(connection.getRequester()), Participant.from(connection.getOwner()));
	}

}
