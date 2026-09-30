package com.conflux.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of a new message. {@code content} is trimmed on arrival (inner whitespace is kept)
 * and validated after trimming. There is deliberately no sender field.
 */
public record SendMessageRequest(@NotBlank @Size(max = Message.CONTENT_MAX_LENGTH) String content) {

	public SendMessageRequest {
		content = (content != null) ? content.strip() : null;
	}

	// Message text is private: never print it (e.g. in logs).
	@Override
	public String toString() {
		return "SendMessageRequest[content=****]";
	}

}
