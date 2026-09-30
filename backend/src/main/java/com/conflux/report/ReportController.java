package com.conflux.report;

import com.conflux.common.web.ApiPaths;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Submitting reports. Requires a bearer token; the reporter is always the token's user.
 */
@RestController
@RequestMapping(ReportController.BASE_PATH)
public class ReportController {

	public static final String BASE_PATH = ApiPaths.API_V1 + "/reports";

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReportResponse create(@Valid @RequestBody ReportRequest request) {
		return this.reportService.create(request);
	}

}
