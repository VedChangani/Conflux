package com.conflux.message;

import com.conflux.auth.CurrentUser;
import com.conflux.common.web.PageResponse;
import com.conflux.connection.ConnectionStatus;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserStatus;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
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

	private final UserRepository userRepository;

	private final CurrentUser currentUser;

	public MessageService(ConversationService conversationService, MessageRepository messageRepository,
			UserRepository userRepository, CurrentUser currentUser) {
		this.conversationService = conversationService;
		this.messageRepository = messageRepository;
		this.userRepository = userRepository;
		this.currentUser = currentUser;
	}

	/**
	 * @throws ResponseStatusException 403 for a suspended account, 404 unless the user
	 * participates, 409 unless the connection is ACCEPTED
	 */
	@Transactional
	public MessageResponse send(Long conversationId, SendMessageRequest request) {
		User sender = activeUser();
		Conversation conversation = this.conversationService.participantConversation(conversationId, sender.getId());
		if (conversation.getConnection().getStatus() != ConnectionStatus.ACCEPTED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Messages can only be sent in conversations of accepted connections.");
		}
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

	// Same rule as elsewhere: suspended accounts cannot change anything.
	private User activeUser() {
		User user = this.userRepository.findById(this.currentUser.id())
			.orElseThrow(() -> new InvalidBearerTokenException("Token subject does not match an account"));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is suspended.");
		}
		return user;
	}

}
