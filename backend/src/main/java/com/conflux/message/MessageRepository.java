package com.conflux.message;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link Message}. Callers check conversation participation first.
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

	/**
	 * A page of a conversation's messages with their senders (paged in the database).
	 */
	@EntityGraph(attributePaths = "sender")
	Page<Message> findByConversationId(Long conversationId, Pageable pageable);

	/**
	 * The latest message of a conversation (newest first, then highest id).
	 */
	Optional<Message> findFirstByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId);

	/**
	 * The messages carrying the latest timestamp of each given conversation, in one query
	 * (callers break rare ties by id).
	 */
	@Query("""
			select m from Message m
			where m.conversation.id in :conversationIds
			  and m.createdAt = (select max(m2.createdAt) from Message m2 where m2.conversation = m.conversation)
			""")
	List<Message> findLatestInConversations(@Param("conversationIds") Collection<Long> conversationIds);

}
