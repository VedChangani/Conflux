package com.conflux.report;

/**
 * Review state of a report: {@code OPEN -> RESOLVED | DISMISSED}; RESOLVED and DISMISSED are final.
 */
public enum ReportStatus {

	OPEN,

	RESOLVED,

	DISMISSED

}
