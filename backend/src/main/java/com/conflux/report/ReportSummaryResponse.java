package com.conflux.report;

import java.time.Instant;

/**
 * An entry of the admin report queue. No details, notes or target content.
 */
public record ReportSummaryResponse(Long id, ReportTargetType targetType, Long targetId, ReportReason reason,
		ReportStatus status, Instant createdAt, Instant reviewedAt) {

	static ReportSummaryResponse from(Report report) {
		return new ReportSummaryResponse(report.getId(), report.getTargetType(), report.getTargetId(),
				report.getReason(), report.getStatus(), report.getCreatedAt(), report.getReviewedAt());
	}

}
