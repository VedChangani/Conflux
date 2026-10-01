package com.conflux.report;

import com.conflux.common.web.PageResponse;
import com.conflux.user.User;
import com.conflux.user.UserService;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The moderation audit log. Moderation services record an action after it has actually
 * changed something, inside their own transaction ({@link Propagation#MANDATORY}): if the
 * insert fails, the action rolls back with it, and failed or no-op attempts are never
 * recorded. The actor is always the authenticated administrator the caller resolved.
 */
@Service
public class ModerationActionService {

	private final ModerationActionRepository moderationActionRepository;

	private final UserService userService;

	public ModerationActionService(ModerationActionRepository moderationActionRepository, UserService userService) {
		this.moderationActionRepository = moderationActionRepository;
		this.userService = userService;
	}

	/**
	 * Records a direct user or listing action (no report, no note).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordDirect(User actor, ModerationActionType actionType, Long targetId) {
		this.moderationActionRepository.saveAndFlush(ModerationAction.direct(actor, actionType, targetId));
	}

	/**
	 * Records the review of a report (its target and resolution note).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordReview(User actor, ModerationActionType actionType, Report report) {
		this.moderationActionRepository.saveAndFlush(ModerationAction.review(actor, actionType, report));
	}

	/**
	 * The audit log for administrators, newest first; {@code null} filters are not applied.
	 */
	@Transactional(readOnly = true)
	public PageResponse<ModerationActionResponse> list(ModerationActionType actionType, ReportTargetType targetType,
			Long targetId, Long actorId, int page, int size) {
		this.userService.currentActiveAdmin();
		return PageResponse.from(this.moderationActionRepository.search(actionType, targetType, targetId, actorId,
				PageRequest.of(page, size)));
	}

}
