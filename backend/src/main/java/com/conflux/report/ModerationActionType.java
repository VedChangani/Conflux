package com.conflux.report;

/**
 * What an administrator did, as recorded in the moderation audit log.
 */
public enum ModerationActionType {

	SUSPEND_USER,

	RESTORE_USER,

	SUSPEND_LISTING,

	RESTORE_LISTING,

	RESOLVE_REPORT,

	DISMISS_REPORT

}
