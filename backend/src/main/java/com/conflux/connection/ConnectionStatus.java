package com.conflux.connection;

/**
 * Lifecycle of an expression of interest. Only PENDING can change:
 * <ul>
 * <li>{@code PENDING -> ACCEPTED} (listing owner)</li>
 * <li>{@code PENDING -> REJECTED} (listing owner)</li>
 * <li>{@code PENDING -> WITHDRAWN} (requester)</li>
 * </ul>
 * ACCEPTED, REJECTED and WITHDRAWN are final.
 */
public enum ConnectionStatus {

	PENDING,

	ACCEPTED,

	REJECTED,

	WITHDRAWN

}
