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
import org.hibernate.annotations.Immutable;

/**
 * One entry of the moderation audit log: an administrator ({@code actor}) performed a
 * successful trust &amp; safety action. Append-only: created through
 * {@link ModerationActionService} in the transaction of the action it records, never
 * changed or deleted (no setters, {@link Immutable}, every column non-updatable). Purely
 * historical: nothing reads it to decide whether an action is allowed.
 * <p>
 * Kept separate from {@link Report}: a report is what a user said, an action is what an
 * administrator did. The target is {@code targetType} + {@code targetId} as for reports;
 * {@code report} is set for report reviews only.
 */
@Entity
@Immutable
@Table(name = "moderation_actions")
public class ModerationAction {

	public static final int NOTE_MAX_LENGTH = 1_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_id", nullable = false, updatable = false)
	private User actor;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_type", nullable = false, length = 30, updatable = false)
	private ModerationActionType actionType;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20, updatable = false)
	private ReportTargetType targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private Long targetId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "report_id", updatable = false)
	private Report report;

	@Column(name = "note", length = NOTE_MAX_LENGTH, updatable = false)
	private String note;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/**
	 * For JPA only.
	 */
	protected ModerationAction() {
	}

	private ModerationAction(User actor, ModerationActionType actionType, ReportTargetType targetType, Long targetId,
			Report report, String note) {
		this.actor = Objects.requireNonNull(actor, "actor must not be null");
		this.actionType = Objects.requireNonNull(actionType, "actionType must not be null");
		this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
		this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
		if (targetId <= 0) {
			throw new IllegalArgumentException("targetId must be positive");
		}
		if (note != null && note.length() > NOTE_MAX_LENGTH) {
			throw new IllegalArgumentException("note must be at most " + NOTE_MAX_LENGTH + " characters");
		}
		this.report = report;
		this.note = note;
	}

	/**
	 * A direct user or listing moderation action (not linked to a report, no note).
	 * @param actionType one of SUSPEND_USER, RESTORE_USER, SUSPEND_LISTING, RESTORE_LISTING;
	 * it determines the target type
	 */
	static ModerationAction direct(User actor, ModerationActionType actionType, Long targetId) {
		ReportTargetType targetType = switch (Objects.requireNonNull(actionType, "actionType must not be null")) {
			case SUSPEND_USER, RESTORE_USER -> ReportTargetType.USER;
			case SUSPEND_LISTING, RESTORE_LISTING -> ReportTargetType.LISTING;
			case RESOLVE_REPORT, DISMISS_REPORT -> throw new IllegalArgumentException(actionType + " needs a report");
		};
		return new ModerationAction(actor, actionType, targetType, targetId, null, null);
	}

	/**
	 * The review of {@code report}: its target, and its resolution note as the note.
	 * @param actionType RESOLVE_REPORT or DISMISS_REPORT
	 */
	static ModerationAction review(User actor, ModerationActionType actionType, Report report) {
		Objects.requireNonNull(report, "report must not be null");
		if (actionType != ModerationActionType.RESOLVE_REPORT && actionType != ModerationActionType.DISMISS_REPORT) {
			throw new IllegalArgumentException(actionType + " is not a report review");
		}
		return new ModerationAction(actor, actionType, report.getTargetType(), report.getTargetId(), report,
				report.getResolutionNote());
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return this.id;
	}

	public User getActor() {
		return this.actor;
	}

	public ModerationActionType getActionType() {
		return this.actionType;
	}

	public ReportTargetType getTargetType() {
		return this.targetType;
	}

	public Long getTargetId() {
		return this.targetId;
	}

	public Report getReport() {
		return this.report;
	}

	public String getNote() {
		return this.note;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
