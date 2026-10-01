package com.conflux.report;

import java.time.Instant;

/**
 * An entry of the admin moderation audit log. The actor is a public summary only (never an
 * email or credentials); the report is referenced by id.
 */
public record ModerationActionResponse(Long id, Actor actor, ModerationActionType actionType,
		ReportTargetType targetType, Long targetId, Long reportId, String note, Instant createdAt) {

	public record Actor(Long id, String username, String displayName) {

	}

	/**
	 * Flat form for the JPQL constructor expression in {@link ModerationActionRepository}.
	 */
	public ModerationActionResponse(Long id, Long actorId, String actorUsername, String actorDisplayName,
			ModerationActionType actionType, ReportTargetType targetType, Long targetId, Long reportId, String note,
			Instant createdAt) {
		this(id, new Actor(actorId, actorUsername, actorDisplayName), actionType, targetType, targetId, reportId, note,
				createdAt);
	}

}
