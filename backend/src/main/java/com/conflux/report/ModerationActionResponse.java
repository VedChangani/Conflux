package com.conflux.report;

import java.time.Instant;

public record ModerationActionResponse(Long id, Actor actor, ModerationActionType actionType,
		ReportTargetType targetType, Long targetId, Long reportId, String note, Instant createdAt) {

	public record Actor(Long id, String username, String displayName) {

	}

	public ModerationActionResponse(Long id, Long actorId, String actorUsername, String actorDisplayName,
			ModerationActionType actionType, ReportTargetType targetType, Long targetId, Long reportId, String note,
			Instant createdAt) {
		this(id, new Actor(actorId, actorUsername, actorDisplayName), actionType, targetType, targetId, reportId, note,
				createdAt);
	}

}
