package com.conflux.report;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.conflux.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A user's report about a user, listing or message, reviewed by an administrator. The
 * target is identified only by {@code targetType} + {@code targetId} (no polymorphic
 * foreign key); {@link ReportService} resolves it. The status changes only through
 * {@link #resolve} and {@link #dismiss}, both of which require OPEN. Submitting a report
 * never changes the target: moderation is a separate, explicit admin action.
 */
@Entity
@Table(name = "reports")
public class Report {

	public static final int DETAILS_MAX_LENGTH = 1_000;

	public static final int RESOLUTION_NOTE_MAX_LENGTH = 1_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reporter_id", nullable = false, updatable = false)
	private User reporter;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20, updatable = false)
	private ReportTargetType targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private Long targetId;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 40, updatable = false)
	private ReportReason reason;

	@Column(name = "details", length = DETAILS_MAX_LENGTH, updatable = false)
	private String details;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ReportStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by")
	private User reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "resolution_note", length = RESOLUTION_NOTE_MAX_LENGTH)
	private String resolutionNote;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/**
	 * For JPA only.
	 */
	protected Report() {
	}

	/**
	 * A new OPEN report.
	 * @param details optional, already normalized (trimmed; blank as {@code null})
	 */
	public Report(User reporter, ReportTargetType targetType, Long targetId, ReportReason reason, String details) {
		this.reporter = Objects.requireNonNull(reporter, "reporter must not be null");
		this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
		this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
		this.reason = Objects.requireNonNull(reason, "reason must not be null");
		this.details = details;
		this.status = ReportStatus.OPEN;
	}

	/**
	 * OPEN to RESOLVED (the report was valid), recording who reviewed it and when.
	 * @throws ReportStateException unless OPEN
	 */
	public void resolve(User reviewer, String note) {
		review(ReportStatus.RESOLVED, reviewer, note, "resolved");
	}

	/**
	 * OPEN to DISMISSED (no action needed), recording who reviewed it and when.
	 * @throws ReportStateException unless OPEN
	 */
	public void dismiss(User reviewer, String note) {
		review(ReportStatus.DISMISSED, reviewer, note, "dismissed");
	}

	private void review(ReportStatus outcome, User reviewer, String note, String action) {
		Objects.requireNonNull(reviewer, "reviewer must not be null");
		if (this.status != ReportStatus.OPEN) {
			throw new ReportStateException("A report with status " + this.status + " cannot be " + action + ".");
		}
		this.status = outcome;
		this.reviewedBy = reviewer;
		this.reviewedAt = now();
		this.resolutionNote = note;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = now();
	}

	// Same approach as the other entities: UTC, truncated to the DATETIME(6) precision.
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return this.id;
	}

	public User getReporter() {
		return this.reporter;
	}

	public ReportTargetType getTargetType() {
		return this.targetType;
	}

	public Long getTargetId() {
		return this.targetId;
	}

	public ReportReason getReason() {
		return this.reason;
	}

	public String getDetails() {
		return this.details;
	}

	public ReportStatus getStatus() {
		return this.status;
	}

	public User getReviewedBy() {
		return this.reviewedBy;
	}

	public Instant getReviewedAt() {
		return this.reviewedAt;
	}

	public String getResolutionNote() {
		return this.resolutionNote;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
