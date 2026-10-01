package com.conflux.report;

import com.conflux.common.web.PageResponse;
import com.conflux.listing.Listing;
import com.conflux.listing.ListingRepository;
import com.conflux.message.Message;
import com.conflux.message.MessageRepository;
import com.conflux.ratelimit.RateLimitOperation;
import com.conflux.ratelimit.RateLimiter;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserService;
import com.conflux.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reports and their reactive review. Anyone active may report a user, listing or message
 * they can legitimately see; the reporter is always the authenticated user. Submitting a
 * report only records it (OPEN): nothing is moderated automatically. Administrators review
 * the queue and resolve or dismiss reports; moderation actions themselves are separate,
 * explicit admin operations.
 */
@Service
public class ReportService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final ReportRepository reportRepository;

	private final UserRepository userRepository;

	private final ListingRepository listingRepository;

	private final MessageRepository messageRepository;

	private final UserService userService;

	private final ModerationActionService moderationActionService;

	private final EntityManager entityManager;

	private final RateLimiter rateLimiter;

	public ReportService(ReportRepository reportRepository, UserRepository userRepository,
			ListingRepository listingRepository, MessageRepository messageRepository, UserService userService,
			ModerationActionService moderationActionService, EntityManager entityManager, RateLimiter rateLimiter) {
		this.reportRepository = reportRepository;
		this.userRepository = userRepository;
		this.listingRepository = listingRepository;
		this.messageRepository = messageRepository;
		this.userService = userService;
		this.moderationActionService = moderationActionService;
		this.entityManager = entityManager;
		this.rateLimiter = rateLimiter;
	}

	// ---- Reporting ----------------------------------------------------------------------

	/**
	 * Records an OPEN report by the current user.
	 * @throws ResponseStatusException 403 for a suspended reporter; 404 if the target is not
	 * one the reporter can see (a missing or suspended user, a listing that is not publicly
	 * visible, a message outside the reporter's conversations); 409 for their own account,
	 * listing or message, or if they already reported this target
	 * @throws com.conflux.ratelimit.RateLimitExceededException (429) if the reporter submitted
	 * too many reports recently
	 */
	@Transactional
	public ReportResponse create(ReportRequest request) {
		User reporter = this.userService.currentActiveUser();
		requireReportable(reporter, request.targetType(), request.targetId());
		if (this.reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporter.getId(), request.targetType(),
				request.targetId())) {
			throw alreadyReported();
		}
		// Only now, so 403/404/409 answers never become 429.
		this.rateLimiter.acquire(RateLimitOperation.REPORT_CREATE, reporter.getId().toString());
		Report report = new Report(reporter, request.targetType(), request.targetId(), request.reason(),
				request.details());
		try {
			this.reportRepository.saveAndFlush(report);
		}
		catch (DataIntegrityViolationException ex) {
			// A concurrent identical report was stored first.
			throw alreadyReported();
		}
		return ReportResponse.from(report);
	}

	private void requireReportable(User reporter, ReportTargetType type, Long targetId) {
		switch (type) {
			case USER -> {
				User user = this.userRepository.findById(targetId)
					.filter(u -> u.getStatus() == UserStatus.ACTIVE)
					.orElseThrow(() -> notFound("User not found."));
				if (user.getId().equals(reporter.getId())) {
					throw conflict("You cannot report yourself.");
				}
			}
			case LISTING -> {
				Listing listing = this.listingRepository.findPublicById(targetId)
					.orElseThrow(() -> notFound("Listing not found."));
				if (listing.getOwner().getId().equals(reporter.getId())) {
					throw conflict("You cannot report your own listing.");
				}
			}
			case MESSAGE -> {
				Message message = this.messageRepository.findForParticipant(targetId, reporter.getId())
					.orElseThrow(() -> notFound("Message not found."));
				if (message.getSender().getId().equals(reporter.getId())) {
					throw conflict("You cannot report your own message.");
				}
			}
		}
	}

	// ---- Admin review -------------------------------------------------------------------

	/**
	 * The admin queue for one status (OPEN when {@code null}), newest first.
	 */
	@Transactional(readOnly = true)
	public PageResponse<ReportSummaryResponse> queue(ReportStatus status, int page, int size) {
		this.userService.currentActiveAdmin();
		ReportStatus effective = (status != null) ? status : ReportStatus.OPEN;
		return PageResponse.from(this.reportRepository
			.findByStatus(effective, PageRequest.of(page, size, NEWEST_FIRST))
			.map(ReportSummaryResponse::from));
	}

	@Transactional(readOnly = true)
	public ReportDetailResponse detail(Long reportId) {
		this.userService.currentActiveAdmin();
		Report report = report(reportId);
		return ReportDetailResponse.from(report, target(report));
	}

	/**
	 * OPEN to RESOLVED, reviewed by the current admin, and recorded as RESOLVE_REPORT.
	 * @throws ResponseStatusException 404 if missing, 409 unless OPEN
	 */
	@Transactional
	public ReportDetailResponse resolve(Long reportId, ReportDecisionRequest request) {
		return review(reportId, request, Report::resolve, ModerationActionType.RESOLVE_REPORT);
	}

	/**
	 * OPEN to DISMISSED, reviewed by the current admin, and recorded as DISMISS_REPORT.
	 * @throws ResponseStatusException 404 if missing, 409 unless OPEN
	 */
	@Transactional
	public ReportDetailResponse dismiss(Long reportId, ReportDecisionRequest request) {
		return review(reportId, request, Report::dismiss, ModerationActionType.DISMISS_REPORT);
	}

	/**
	 * Locks the report row and re-reads it before deciding, so two admins deciding the same
	 * report at once cannot both succeed (the second sees the first decision and gets 409).
	 * The audit entry is written in the same transaction, only once the decision succeeded.
	 * The acting admin's ADMIN_MODERATION rate limit (429) is checked after every 403/404/409
	 * check.
	 */
	private ReportDetailResponse review(Long reportId, ReportDecisionRequest request, Decision decision,
			ModerationActionType actionType) {
		User admin = this.userService.currentActiveAdmin();
		Report report = report(reportId);
		this.entityManager.refresh(report, LockModeType.PESSIMISTIC_WRITE);
		try {
			decision.apply(report, admin, (request != null) ? request.resolutionNote() : null);
		}
		catch (ReportStateException ex) {
			throw conflict(ex.getMessage());
		}
		// Shared with the other moderation operations; a 429 rolls the (unflushed) decision back.
		this.rateLimiter.acquire(RateLimitOperation.ADMIN_MODERATION, admin.getId().toString());
		this.reportRepository.saveAndFlush(report);
		this.moderationActionService.recordReview(admin, actionType, report);
		return ReportDetailResponse.from(report, target(report));
	}

	@FunctionalInterface
	private interface Decision {

		void apply(Report report, User reviewer, String note);

	}

	// ---- Helpers --------------------------------------------------------------------

	private Report report(Long reportId) {
		return this.reportRepository.findWithPeopleById(reportId).orElseThrow(() -> notFound("Report not found."));
	}

	// One query for the target, whatever its visibility (admins see suspended content too).
	private ReportDetailResponse.Target target(Report report) {
		Long id = report.getTargetId();
		return switch (report.getTargetType()) {
			case USER -> this.userRepository.findById(id).map(ReportDetailResponse.UserTarget::from).orElse(null);
			case LISTING -> this.listingRepository.findWithOwnerById(id)
				.map(ReportDetailResponse.ListingTarget::from)
				.orElse(null);
			case MESSAGE -> this.messageRepository.findWithContextById(id)
				.map(ReportDetailResponse.MessageTarget::from)
				.orElse(null);
		};
	}

	private static ResponseStatusException alreadyReported() {
		return conflict("You have already reported this.");
	}

	private static ResponseStatusException notFound(String detail) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
	}

	private static ResponseStatusException conflict(String detail) {
		return new ResponseStatusException(HttpStatus.CONFLICT, detail);
	}

}
