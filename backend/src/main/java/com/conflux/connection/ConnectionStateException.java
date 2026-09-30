package com.conflux.connection;

/**
 * A connection operation is not allowed in the connection's current status.
 */
public class ConnectionStateException extends RuntimeException {

	public ConnectionStateException(String message) {
		super(message);
	}

}
