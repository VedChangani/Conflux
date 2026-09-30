package com.conflux.message;

import java.time.Instant;

import com.conflux.listing.Listing;
import com.conflux.user.User;

/**
 * A conversation as seen by one participant: the listing it is about and the other
 * participant, plus a short preview of the latest message. No emails or security data.
 *
 * @param lastMessagePreview the start of the latest message ({@value #PREVIEW_LENGTH}
 * characters at most), {@code null} if there are no messages yet
 * @param lastMessageAt time of the latest message, {@code null} if there are none
 */
public record ConversationResponse(Long id, Long connectionId, ListingSummary listing, Participant otherParticipant,
		String lastMessagePreview, Instant lastMessageAt, Instant createdAt, Instant updatedAt) {

	static final int PREVIEW_LENGTH = 120;

	public record ListingSummary(Long id, String slug, String title, String shortPitch) {

		static ListingSummary from(Listing listing) {
			return new ListingSummary(listing.getId(), listing.getSlug(), listing.getTitle(), listing.getShortPitch());
		}

	}

	public record Participant(Long id, String username, String displayName) {

		static Participant from(User user) {
			return new Participant(user.getId(), user.getUsername(), user.getDisplayName());
		}

	}

	/**
	 * Must be called while the connection, listing, owner and requester are loaded.
	 * @param lastMessage the latest message, or {@code null}
	 */
	static ConversationResponse from(Conversation conversation, Long currentUserId, Message lastMessage) {
		return new ConversationResponse(conversation.getId(), conversation.getConnection().getId(),
				ListingSummary.from(conversation.getConnection().getListing()),
				Participant.from(conversation.otherParticipant(currentUserId)),
				(lastMessage != null) ? preview(lastMessage.getContent()) : null,
				(lastMessage != null) ? lastMessage.getCreatedAt() : null, conversation.getCreatedAt(),
				conversation.getUpdatedAt());
	}

	// Cut on a code point boundary so a preview never ends with half a character.
	private static String preview(String content) {
		if (content.codePointCount(0, content.length()) <= PREVIEW_LENGTH) {
			return content;
		}
		return content.substring(0, content.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…";
	}

}
