package com.conflux.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link ModerationAction}. Deliberately not a {@code JpaRepository}: the log
 * is append-only, so only inserting and reading are exposed (no delete methods).
 */
public interface ModerationActionRepository extends Repository<ModerationAction, Long> {

	ModerationAction saveAndFlush(ModerationAction action);

	/**
	 * The admin audit log, newest first ({@code createdAt}, then {@code id}), paged in the
	 * database. Each filter is optional ({@code null} = not applied); they are combined with
	 * AND. The actor summary comes from the same query (projection, no entities loaded).
	 */
	@Query(value = """
			select new com.conflux.report.ModerationActionResponse(a.id, u.id, u.username, u.displayName,
			       a.actionType, a.targetType, a.targetId, r.id, a.note, a.createdAt)
			from ModerationAction a
			join a.actor u
			left join a.report r
			where (:actionType is null or a.actionType = :actionType)
			  and (:targetType is null or a.targetType = :targetType)
			  and (:targetId is null or a.targetId = :targetId)
			  and (:actorId is null or u.id = :actorId)
			order by a.createdAt desc, a.id desc
			""", countQuery = """
			select count(a) from ModerationAction a
			where (:actionType is null or a.actionType = :actionType)
			  and (:targetType is null or a.targetType = :targetType)
			  and (:targetId is null or a.targetId = :targetId)
			  and (:actorId is null or a.actor.id = :actorId)
			""")
	Page<ModerationActionResponse> search(@Param("actionType") ModerationActionType actionType,
			@Param("targetType") ReportTargetType targetType, @Param("targetId") Long targetId,
			@Param("actorId") Long actorId, Pageable pageable);

}
