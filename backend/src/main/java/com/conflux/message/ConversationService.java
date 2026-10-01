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

	@Transactional
	public Conversation createFor(Connection connection) {
		if (connection.getStatus() != ConnectionStatus.ACCEPTED) {
			throw new IllegalStateException("Conversations exist only for accepted connections");
		}
		return this.conversationRepository.findByConnectionId(connection.getId())
			.orElseGet(() -> this.conversationRepository.saveAndFlush(new Conversation(connection)));
	}

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
							(a, b) -> Comparator.comparing(Message::getId).compare(a, b) >= 0 ? a : b));
		return PageResponse.from(rows.map(row -> {
			Conversation conversation = (Conversation) row[0];
			return ConversationResponse.from(conversation, userId, latest.get(conversation.getId()));
		}));
	}

	@Transactional(readOnly = true)
	public ConversationResponse conversation(Long conversationId) {
		Long userId = this.currentUser.id();
		Conversation conversation = participantConversation(conversationId, userId);
		Message latest = this.messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversationId)
			.orElse(null);
		return ConversationResponse.from(conversation, userId, latest);
	}

	public Conversation participantConversation(Long conversationId, Long userId) {
		return this.conversationRepository.findForParticipant(conversationId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
	}

}
