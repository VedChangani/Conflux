package com.conflux.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A new report. {@code details} is trimmed on arrival and blank becomes {@code null}. The
 * reporter, status and review fields are deliberately absent: the server controls them.
 */
public record ReportRequest(@NotNull ReportTargetType targetType, @NotNull @Positive Long targetId,
		@NotNull ReportReason reason, @Size(max = Report.DETAILS_MAX_LENGTH) String details) {

	public ReportRequest {
		details = trimToNull(details);
	}

	static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}

}
