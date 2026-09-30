package com.conflux.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness endpoint. Intentionally does not touch the database.
 */
@RestController
@RequestMapping(ApiPaths.API_V1)
public class HealthController {

	public static final String HEALTH_PATH = ApiPaths.API_V1 + "/health";

	@GetMapping("/health")
	public HealthResponse health() {
		return new HealthResponse("UP");
	}

	public record HealthResponse(String status) {
	}

}
