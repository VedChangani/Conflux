package com.conflux.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ModerationActionRepository extends Repository<ModerationAction, Long> {

	ModerationAction saveAndFlush(ModerationAction action);

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
