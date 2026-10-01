package com.conflux.ratelimit;

/**
 * The rate-limited operations. Each has its own limit and its own counters, so using one
 * operation never uses up another's allowance.
 */
public enum RateLimitOperation {

	/**
	 * {@code POST /api/v1/auth/login}, per client IP.
	 */
	LOGIN,

	/**
	 * {@code POST /api/v1/auth/register}, per client IP.
	 */
	REGISTER,

	/**
	 * {@code POST /api/v1/listings}, per user.
	 */
	LISTING_CREATE,

	/**
	 * {@code POST /api/v1/listings/{id}/publish}, per user.
	 */
	LISTING_PUBLISH,

	/**
	 * {@code POST /api/v1/listings/{id}/save}, per user.
	 */
	LISTING_SAVE,

	/**
	 * {@code POST /api/v1/listings/{id}/interest}, per user.
	 */
	LISTING_INTEREST,

	/**
	 * {@code POST /api/v1/conversations/{id}/messages}, per user.
	 */
	MESSAGE_SEND,

	/**
	 * {@code POST /api/v1/reports}, per user.
	 */
	REPORT_CREATE,

	/**
	 * {@code PUT /api/v1/listings/{id}}, per user.
	 */
	LISTING_UPDATE,

	/**
	 * {@code DELETE /api/v1/listings/{id}} (archive), per user.
	 */
	LISTING_ARCHIVE,

	/**
	 * {@code DELETE /api/v1/listings/{id}/save}, per user.
	 */
	LISTING_UNSAVE,

	/**
	 * {@code POST /api/v1/connections/{id}/accept} and {@code .../reject}, per user (the
	 * listing owner answering requests).
	 */
	CONNECTION_DECISION,

	/**
	 * {@code DELETE /api/v1/connections/{id}} (withdraw), per user.
	 */
	CONNECTION_WITHDRAW,

	/**
	 * {@code PUT /api/v1/profile}, per user.
	 */
	PROFILE_UPDATE,

	/**
	 * Every state-changing admin operation (suspend/restore of users and listings,
	 * resolve/dismiss of reports), per administrator.
	 */
	ADMIN_MODERATION

}
