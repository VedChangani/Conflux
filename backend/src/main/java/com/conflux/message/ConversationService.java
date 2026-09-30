package com.conflux.message;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.conflux.auth.CurrentUser;
import com.conflux.common.web.PageResponse;
import com.conflux.connection.Connection;
import com.conflux.connection.ConnectionStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Conversations of accepted connections. The acting user always comes from the verified
 * JWT ({@link CurrentUser}); only the connection's requester and its listing's owner can
 * see a conversation. There is no way to start a conversation directly: one is created
 * when a connection is accepted ({@link #createFor(Connection)}).
 */
@Service
public class ConversationService {

	private final ConversationRepository conversationRepository;

	private final MessageRepository messageRepository;

	private final CurrentUser currentUser;

	public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository,
			CurrentUser currentUser) {
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.currentUser = currentUser;
	}

	/**
	 * Creates the conversation of a just-accepted connection, or returns the existing one.
	 * Runs inside the caller's transaction, so a failure here undoes the acceptance too. The
	 * database guarantees one conversation per connection; a concurrent duplicate insert
	 * fails with a {@code DataIntegrityViolationException} for the caller to handle.
	 * @throws IllegalStateException if the connection is not ACCEPTED
	 */
	@Transactional
	public Conversation createFor(Connection connection) {
		if (connection.getStatus() != ConnectionStatus.ACCEPTED) {
			throw new IllegalStateException("Conversations exist only for accepted connections");
		}
		return this.conversationRepository.findByConnectionId(connection.getId())
			.orElseGet(() -> this.conversationRepository.saveAndFlush(new Conversation(connection)));
	}

	/**
	 * The current user's conversations, most recent activity first (latest message, or
	 * creation when there is none; then id descending). One query for the page and one for
	 * the page's latest messages, whatever the page size.
	 */
	@Transactional(readOnly = true)
	public PageResponse<ConversationResponse> conversations(int page, int size) {
		Long userId = this.currentUser.id();
		Page<Object[]> rows = this.conversationRepository.findForParticipantByActivity(userId,
				PageRequest.of(page, size));
		List<Long> ids = rows.getContent().stream().map(row -> ((Conversation) row[0]).getId()).toList();
		Map<Long, Message> latest = ids.isEmpty() ? Map.of()
				: this.messageRepository.findLatestInConversations(ids)
					.stream()
					.collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity(),
							// Same timestamp: the higher id is the later message.
							(a, b) -> Comparator.comparing(Message::getId).compare(a, b) >= 0 ? a : b));
		return PageResponse.from(rows.map(row -> {
			Conversation conversation = (Conversation) row[0];
			return ConversationResponse.from(conversation, userId, latest.get(conversation.getId()));
		}));
	}

	/**
	 * @throws ResponseStatusException 404 unless the current user participates
	 */
	@Transactional(readOnly = true)
	public ConversationResponse conversation(Long conversationId) {
		Long userId = this.currentUser.id();
		Conversation conversation = participantConversation(conversationId, userId);
		Message latest = this.messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversationId)
			.orElse(null);
		return ConversationResponse.from(conversation, userId, latest);
	}

	/**
	 * The conversation, with connection, listing, owner and requester loaded, if the user
	 * participates in it.
	 * @throws ResponseStatusException 404 otherwise (indistinguishable from a missing one)
	 */
	public Conversation participantConversation(Long conversationId, Long userId) {
		return this.conversationRepository.findForParticipant(conversationId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
	}

}
