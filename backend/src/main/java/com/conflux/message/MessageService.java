package com.conflux.message;

import com.conflux.auth.CurrentUser;
import com.conflux.common.web.PageResponse;
import com.conflux.connection.ConnectionStatus;
import com.conflux.ratelimit.RateLimitOperation;
import com.conflux.ratelimit.RateLimiter;
import com.conflux.user.User;
import com.conflux.user.UserService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sending and reading messages. Only the two participants of a conversation can do either;
 * the sender is always the authenticated user; sending requires the connection to be
 * ACCEPTED and the account to be active. Message content is never logged.
 */
@Service
public class MessageService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final ConversationService conversationService;

	private final MessageRepository messageRepository;

	private final UserService userService;

	private final CurrentUser currentUser;

	private final RateLimiter rateLimiter;

	public MessageService(ConversationService conversationService, MessageRepository messageRepository,
			UserService userService, CurrentUser currentUser, RateLimiter rateLimiter) {
		this.conversationService = conversationService;
		this.messageRepository = messageRepository;
		this.userService = userService;
		this.currentUser = currentUser;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * @throws ResponseStatusException 403 for a suspended account, 404 unless the user
	 * participates, 409 unless the connection is ACCEPTED
	 * @throws com.conflux.ratelimit.RateLimitExceededException (429) if the user sent too
	 * many messages recently
	 */
	@Transactional
	public MessageResponse send(Long conversationId, SendMessageRequest request) {
		User sender = this.userService.currentActiveUser();
		Conversation conversation = this.conversationService.participantConversation(conversationId, sender.getId());
		if (conversation.getConnection().getStatus() != ConnectionStatus.ACCEPTED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Messages can only be sent in conversations of accepted connections.");
		}
		this.rateLimiter.acquire(RateLimitOperation.MESSAGE_SEND, this.currentUser.id().toString());
		Message message = this.messageRepository.save(new Message(conversation, sender, request.content()));
		return MessageResponse.from(message);
	}

	/**
	 * A page of the conversation's messages, newest first (then id descending).
	 * @throws ResponseStatusException 404 unless the user participates
	 */
	@Transactional(readOnly = true)
	public PageResponse<MessageResponse> messages(Long conversationId, int page, int size) {
		this.conversationService.participantConversation(conversationId, this.currentUser.id());
		return PageResponse.from(this.messageRepository
			.findByConversationId(conversationId, PageRequest.of(page, size, NEWEST_FIRST))
			.map(MessageResponse::from));
	}

}
