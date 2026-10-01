package com.conflux.admin;

import com.conflux.common.web.ApiPaths;
import com.conflux.common.web.PageResponse;
import com.conflux.report.ModerationActionResponse;
import com.conflux.report.ModerationActionService;
import com.conflux.report.ModerationActionType;
import com.conflux.report.ReportTargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read-only moderation audit log for administrators ({@code ROLE_ADMIN}, see
 * SecurityConfig). Entries are written by the moderation operations themselves; there is
 * no endpoint to create, change or delete them.
 */
@RestController
@RequestMapping(AdminModerationActionController.BASE_PATH)
public class AdminModerationActionController {

	public static final String BASE_PATH = ApiPaths.ADMIN + "/moderation-actions";

	private final ModerationActionService moderationActionService;

	public AdminModerationActionController(ModerationActionService moderationActionService) {
		this.moderationActionService = moderationActionService;
	}

	/**
	 * Newest first; every filter is optional and they are combined with AND.
	 */
	@GetMapping
	public PageResponse<ModerationActionResponse> list(@RequestParam(required = false) ModerationActionType actionType,
			@RequestParam(required = false) ReportTargetType targetType,
			@RequestParam(required = false) @Positive Long targetId,
			@RequestParam(required = false) @Positive Long actorId,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return this.moderationActionService.list(actionType, targetType, targetId, actorId, page, size);
	}

}
