package com.conflux.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(@NotBlank @Size(max = Message.CONTENT_MAX_LENGTH) String content) {

	public SendMessageRequest {
		content = (content != null) ? content.strip() : null;
	}

	@Override
	public String toString() {
		return "SendMessageRequest[content=****]";
	}

}
