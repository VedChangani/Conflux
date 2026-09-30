package com.conflux.report;

import java.time.Instant;

/**
 * What the reporter gets back: the report itself, without reporter, reviewer or moderation
 * data.
 */
public record ReportResponse(Long id, ReportTargetType targetType, Long targetId, ReportReason reason,
		ReportStatus status, Instant createdAt) {

	static ReportResponse from(Report report) {
		return new ReportResponse(report.getId(), report.getTargetType(), report.getTargetId(), report.getReason(),
				report.getStatus(), report.getCreatedAt());
	}

}
