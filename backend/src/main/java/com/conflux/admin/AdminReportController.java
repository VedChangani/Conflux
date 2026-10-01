package com.conflux.admin;

import com.conflux.common.web.ApiPaths;
import com.conflux.common.web.PageResponse;
import com.conflux.report.ReportDecisionRequest;
import com.conflux.report.ReportDetailResponse;
import com.conflux.report.ReportService;
import com.conflux.report.ReportStatus;
import com.conflux.report.ReportSummaryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AdminReportController.BASE_PATH)
public class AdminReportController {

	public static final String BASE_PATH = ApiPaths.ADMIN + "/reports";

	private final ReportService reportService;

	public AdminReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@GetMapping
	public PageResponse<ReportSummaryResponse> queue(@RequestParam(required = false) ReportStatus status,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return this.reportService.queue(status, page, size);
	}

	@GetMapping("/{id}")
	public ReportDetailResponse detail(@PathVariable Long id) {
		return this.reportService.detail(id);
	}

	@PostMapping("/{id}/resolve")
	public ReportDetailResponse resolve(@PathVariable Long id,
			@Valid @RequestBody(required = false) ReportDecisionRequest request) {
		return this.reportService.resolve(id, request);
	}

	@PostMapping("/{id}/dismiss")
	public ReportDetailResponse dismiss(@PathVariable Long id,
			@Valid @RequestBody(required = false) ReportDecisionRequest request) {
		return this.reportService.dismiss(id, request);
	}

}
