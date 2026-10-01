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

public interface MessageRepository extends JpaRepository<Message, Long> {

	@EntityGraph(attributePaths = "sender")
	Page<Message> findByConversationId(Long conversationId, Pageable pageable);

	@EntityGraph(attributePaths = "sender")
	@Query("""
			select m from Message m
			where m.id = :id
			  and (m.conversation.connection.requester.id = :userId
			       or m.conversation.connection.listing.owner.id = :userId)
			""")
	Optional<Message> findForParticipant(@Param("id") Long id, @Param("userId") Long userId);

	@EntityGraph(attributePaths = { "sender", "conversation", "conversation.connection",
			"conversation.connection.listing" })
	Optional<Message> findWithContextById(Long id);

	Optional<Message> findFirstByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId);

	@Query("""
			select m from Message m
			where m.conversation.id in :conversationIds
			  and m.createdAt = (select max(m2.createdAt) from Message m2 where m2.conversation = m.conversation)
			""")
	List<Message> findLatestInConversations(@Param("conversationIds") Collection<Long> conversationIds);

}
