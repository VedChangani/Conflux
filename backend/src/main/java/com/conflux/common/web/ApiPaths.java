package com.conflux.common.web;

/**
 * Shared API path prefixes.
 */
public final class ApiPaths {

	public static final String API_V1 = "/api/v1";

	/**
	 * Administrator namespace; restricted to ROLE_ADMIN as a whole.
	 */
	public static final String ADMIN = API_V1 + "/admin";

	private ApiPaths() {
	}

}
