package com.conflux.connection;

/**
 * Outcome of expressing interest: the connection, and whether this request created it
 * (201) or it already existed (200).
 */
public record InterestResult(ConnectionResponse connection, boolean created) {

}
