package com.conflux.message;

import java.time.Instant;

import com.conflux.listing.Listing;
import com.conflux.user.User;

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

	static ConversationResponse from(Conversation conversation, Long currentUserId, Message lastMessage) {
		return new ConversationResponse(conversation.getId(), conversation.getConnection().getId(),
				ListingSummary.from(conversation.getConnection().getListing()),
				Participant.from(conversation.otherParticipant(currentUserId)),
				(lastMessage != null) ? preview(lastMessage.getContent()) : null,
				(lastMessage != null) ? lastMessage.getCreatedAt() : null, conversation.getCreatedAt(),
				conversation.getUpdatedAt());
	}

	private static String preview(String content) {
		if (content.codePointCount(0, content.length()) <= PREVIEW_LENGTH) {
			return content;
		}
		return content.substring(0, content.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…";
	}

}
