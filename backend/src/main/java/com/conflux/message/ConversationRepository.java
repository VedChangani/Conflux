package com.conflux.message;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

	Optional<Conversation> findByConnectionId(Long connectionId);

	@EntityGraph(attributePaths = { "connection", "connection.listing", "connection.listing.owner",
			"connection.requester" })
	@Query("""
			select c from Conversation c
			where c.id = :id
			  and (c.connection.requester.id = :userId or c.connection.listing.owner.id = :userId)
			""")
	Optional<Conversation> findForParticipant(@Param("id") Long id, @Param("userId") Long userId);

	@Query(value = """
			select c, coalesce((select max(m.createdAt) from Message m where m.conversation = c), c.createdAt) as activityAt
			from Conversation c
			join fetch c.connection conn
			join fetch conn.listing l
			join fetch l.owner
			join fetch conn.requester
			where conn.requester.id = :userId or l.owner.id = :userId
			order by activityAt desc, c.id desc
			""", countQuery = """
			select count(c) from Conversation c
			where c.connection.requester.id = :userId or c.connection.listing.owner.id = :userId
			""")
	Page<Object[]> findForParticipantByActivity(@Param("userId") Long userId, Pageable pageable);

}
