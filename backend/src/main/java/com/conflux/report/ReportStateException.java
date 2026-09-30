package com.conflux.report;

/**
 * A report operation is not allowed in the report's current status.
 */
public class ReportStateException extends RuntimeException {

	public ReportStateException(String message) {
		super(message);
	}

}
