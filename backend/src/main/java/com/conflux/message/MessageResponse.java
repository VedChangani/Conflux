package com.conflux.message;

import java.time.Instant;

import com.conflux.user.User;

/**
 * A message with a public sender summary. No emails, roles or security data.
 */
public record MessageResponse(Long id, Sender sender, String content, Instant createdAt) {

	public record Sender(Long id, String username, String displayName) {

		static Sender from(User user) {
			return new Sender(user.getId(), user.getUsername(), user.getDisplayName());
		}

	}

	/**
	 * Must be called while the sender is loaded.
	 */
	static MessageResponse from(Message message) {
		return new MessageResponse(message.getId(), Sender.from(message.getSender()), message.getContent(),
				message.getCreatedAt());
	}

}
