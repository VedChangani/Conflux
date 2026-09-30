package com.conflux.report;

import jakarta.validation.constraints.Size;

/**
 * An administrator's optional note when resolving or dismissing a report; trimmed, blank
 * becomes {@code null}. The reviewer and review time are set by the server.
 */
public record ReportDecisionRequest(@Size(max = Report.RESOLUTION_NOTE_MAX_LENGTH) String resolutionNote) {

	public ReportDecisionRequest {
		resolutionNote = ReportRequest.trimToNull(resolutionNote);
	}

}
