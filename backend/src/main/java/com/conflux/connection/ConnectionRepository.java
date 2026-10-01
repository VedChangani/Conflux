package com.conflux.connection;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {

	@EntityGraph(attributePaths = { "listing", "listing.owner", "requester" })
	Optional<Connection> findByRequesterIdAndListingId(Long requesterId, Long listingId);

	@EntityGraph(attributePaths = { "listing", "listing.owner", "requester" })
	@Query("""
			select c from Connection c
			where c.requester.id = :userId
			  and (:status is null or c.status = :status)
			""")
	Page<Connection> findSent(@Param("userId") Long userId, @Param("status") ConnectionStatus status,
			Pageable pageable);

	@EntityGraph(attributePaths = { "listing", "listing.owner", "requester" })
	@Query("""
			select c from Connection c
			where c.listing.owner.id = :userId
			  and (:status is null or c.status = :status)
			""")
	Page<Connection> findReceived(@Param("userId") Long userId, @Param("status") ConnectionStatus status,
			Pageable pageable);

	@EntityGraph(attributePaths = { "listing", "listing.owner", "requester" })
	@Query("""
			select c from Connection c
			where c.id = :id
			  and (c.requester.id = :userId or c.listing.owner.id = :userId)
			""")
	Optional<Connection> findForParticipant(@Param("id") Long id, @Param("userId") Long userId);

}
