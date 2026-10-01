package com.conflux.message;

import java.time.Instant;

import com.conflux.user.User;

public record MessageResponse(Long id, Sender sender, String content, Instant createdAt) {

	public record Sender(Long id, String username, String displayName) {

		static Sender from(User user) {
			return new Sender(user.getId(), user.getUsername(), user.getDisplayName());
		}

	}

	static MessageResponse from(Message message) {
		return new MessageResponse(message.getId(), Sender.from(message.getSender()), message.getContent(),
				message.getCreatedAt());
	}

}
