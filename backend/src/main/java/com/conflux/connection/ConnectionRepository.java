package com.conflux.connection;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link Connection}. Every read is scoped to a participant, and fetches
 * the listing, its owner and the requester in the same query (open-in-view is disabled).
 */
public interface ConnectionRepository extends JpaRepository<Connection, Long> {

	@EntityGraph(attributePaths = { "listing", "listing.owner", "requester" })
	Optional<Connection> findByRequesterIdAndListingId(Long requesterId, Long listingId);

	/**
	 * Connections the user has requested; {@code status} {@code null} means any status.
	 */
	@EntityGraph(attributePaths = { "listing", "listing.owner", "requester" })
	@Query("""
			select c from Connection c
			where c.requester.id = :userId
			  and (:status is null or c.status = :status)
			""")
	Page<Connection> findSent(@Param("userId") Long userId, @Param("status") ConnectionStatus status,
			Pageable pageable);

	/**
	 * Connections for listings the user owns; {@code status} {@code null} means any status.
	 */
	@EntityGraph(attributePaths = { "listing", "listing.owner", "requester" })
	@Query("""
			select c from Connection c
			where c.listing.owner.id = :userId
			  and (:status is null or c.status = :status)
			""")
	Page<Connection> findReceived(@Param("userId") Long userId, @Param("status") ConnectionStatus status,
			Pageable pageable);

	/**
	 * A connection only if the user is its requester or its listing's owner, so anyone else's
	 * connection is indistinguishable from a missing one.
	 */
	@EntityGraph(attributePaths = { "listing", "listing.owner", "requester" })
	@Query("""
			select c from Connection c
			where c.id = :id
			  and (c.requester.id = :userId or c.listing.owner.id = :userId)
			""")
	Optional<Connection> findForParticipant(@Param("id") Long id, @Param("userId") Long userId);

}
