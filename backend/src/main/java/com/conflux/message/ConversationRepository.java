package com.conflux.message;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link Conversation}. Participant checks are part of the queries: a user
 * participates if they are the connection's requester or its listing's owner.
 */
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

	Optional<Conversation> findByConnectionId(Long connectionId);

	/**
	 * A conversation only if the user participates, with connection, listing, owner and
	 * requester fetched; anyone else's conversation is indistinguishable from a missing one.
	 */
	@EntityGraph(attributePaths = { "connection", "connection.listing", "connection.listing.owner",
			"connection.requester" })
	@Query("""
			select c from Conversation c
			where c.id = :id
			  and (c.connection.requester.id = :userId or c.connection.listing.owner.id = :userId)
			""")
	Optional<Conversation> findForParticipant(@Param("id") Long id, @Param("userId") Long userId);

	/**
	 * The user's conversations, most recent activity first: the latest message's time, or the
	 * conversation's creation time when it has no messages, then id descending. Each row is
	 * {@code [Conversation, Instant activityAt]}; the connection, listing, owner and requester
	 * are fetched in the same query. Paging must not add a sort (the order is fixed here).
	 */
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
