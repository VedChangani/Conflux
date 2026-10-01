package com.conflux.report;

import jakarta.validation.constraints.Size;

public record ReportDecisionRequest(@Size(max = Report.RESOLUTION_NOTE_MAX_LENGTH) String resolutionNote) {

	public ReportDecisionRequest {
		resolutionNote = ReportRequest.trimToNull(resolutionNote);
	}

}
