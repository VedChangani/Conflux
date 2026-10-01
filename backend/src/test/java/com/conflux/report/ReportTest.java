package com.conflux.report;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.conflux.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ReportTest {

	private final User reporter = new User("reporter@example.com", "hash", "reporter", "Reporter");

	private final User admin = new User("admin@example.com", "hash", "admin", "Admin");

	@Test
	void enumsAreExactlyTheMvpValues() {
		assertThat(ReportTargetType.values()).containsExactly(ReportTargetType.USER, ReportTargetType.LISTING,
				ReportTargetType.MESSAGE);
		assertThat(ReportReason.values()).containsExactly(ReportReason.SPAM, ReportReason.SCAM_OR_FRAUD,
				ReportReason.HARASSMENT, ReportReason.INAPPROPRIATE_CONTENT, ReportReason.MISLEADING_INFORMATION,
				ReportReason.OTHER);
		assertThat(ReportStatus.values()).containsExactly(ReportStatus.OPEN, ReportStatus.RESOLVED,
				ReportStatus.DISMISSED);
	}

	@Test
	void newReportIsOpenAndUnreviewed() {
		Report report = report();

		assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
		assertThat(report.getReviewedBy()).isNull();
		assertThat(report.getReviewedAt()).isNull();
		assertThat(report.getResolutionNote()).isNull();
		assertThatNullPointerException()
			.isThrownBy(() -> new Report(null, ReportTargetType.USER, 1L, ReportReason.SPAM, null));
	}

	@Test
	void resolveAndDismissRecordTheReview() {
		Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Report resolved = report();
		resolved.resolve(this.admin, "Valid report.");
		Report dismissed = report();
		dismissed.dismiss(this.admin, null);

		assertThat(resolved.getStatus()).isEqualTo(ReportStatus.RESOLVED);
		assertThat(resolved.getReviewedBy()).isSameAs(this.admin);
		assertThat(resolved.getReviewedAt()).isBetween(before, Instant.now());
		assertThat(resolved.getResolutionNote()).isEqualTo("Valid report.");
		assertThat(dismissed.getStatus()).isEqualTo(ReportStatus.DISMISSED);
		assertThat(dismissed.getResolutionNote()).isNull();
	}

	@Test
	void resolvedAndDismissedAreFinal() {
		for (boolean resolveFirst : new boolean[] { true, false }) {
			Report report = report();
			if (resolveFirst) {
				report.resolve(this.admin, "first");
			}
			else {
				report.dismiss(this.admin, "first");
			}
			ReportStatus terminal = report.getStatus();

			assertThatExceptionOfType(ReportStateException.class).isThrownBy(() -> report.resolve(this.admin, "again"))
				.withMessage("A report with status " + terminal + " cannot be resolved.");
			assertThatExceptionOfType(ReportStateException.class).isThrownBy(() -> report.dismiss(this.admin, "again"))
				.withMessage("A report with status " + terminal + " cannot be dismissed.");
			assertThat(report.getStatus()).isEqualTo(terminal);
			assertThat(report.getResolutionNote()).isEqualTo("first");
		}
	}

	@Test
	void thereIsNoStatusSetter() {
		assertThat(Report.class.getMethods()).extracting(java.lang.reflect.Method::getName)
			.doesNotContain("setStatus", "setReviewedBy", "setReviewedAt");
	}

	private Report report() {
		return new Report(this.reporter, ReportTargetType.LISTING, 42L, ReportReason.SCAM_OR_FRAUD, "details");
	}

}
