package com.conflux.message;

import com.conflux.common.web.ApiPaths;
import com.conflux.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ConversationController.BASE_PATH)
public class ConversationController {

	public static final String BASE_PATH = ApiPaths.API_V1 + "/conversations";

	private final ConversationService conversationService;

	private final MessageService messageService;

	public ConversationController(ConversationService conversationService, MessageService messageService) {
		this.conversationService = conversationService;
		this.messageService = messageService;
	}

	@GetMapping
	public PageResponse<ConversationResponse> conversations(
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return this.conversationService.conversations(page, size);
	}

	@GetMapping("/{id}")
	public ConversationResponse conversation(@PathVariable Long id) {
		return this.conversationService.conversation(id);
	}

	@GetMapping("/{id}/messages")
	public PageResponse<MessageResponse> messages(@PathVariable Long id,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		return this.messageService.messages(id, page, size);
	}

	@PostMapping("/{id}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public MessageResponse send(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request) {
		return this.messageService.send(id, request);
	}

}
