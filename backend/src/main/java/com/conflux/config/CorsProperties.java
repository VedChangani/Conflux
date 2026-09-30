package com.conflux.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS settings bound from {@code conflux.cors.*}.
 *
 * @param allowedOrigins origins allowed to call the API (e.g. the React dev server)
 */
@ConfigurationProperties("conflux.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = (allowedOrigins != null) ? List.copyOf(allowedOrigins) : List.of();
	}

}
