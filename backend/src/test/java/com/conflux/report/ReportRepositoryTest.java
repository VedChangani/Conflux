package com.conflux.report;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.conflux.user.User;
import com.conflux.user.UserRepository;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * V8 schema and {@link ReportRepository} against the Flyway-migrated H2 database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReportRepositoryTest {

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private Flyway flyway;

	private User reporter;

	private User admin;

	@BeforeEach
	void setUp() {
		this.reporter = this.userRepository.save(new User("reporter@example.com", "hash", "reporter", "Reporter"));
		this.admin = this.userRepository.save(new User("admin@example.com", "hash", "admin", "Admin"));
	}

	@Test
	void v8MigrationCreatesReportsTableWithConstraintsAndIndexes() {
		assertThat(this.flyway.info().applied()).filteredOn(m -> "8".equals(m.getVersion().getVersion()))
			.singleElement()
			.satisfies(m -> {
				assertThat(m.getScript()).isEqualTo("V8__create_reports_table.sql");
				assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS);
			});
		assertThat(nativeList("SELECT column_name FROM information_schema.columns WHERE table_schema = SCHEMA() "
				+ "AND table_name = 'reports' ORDER BY ordinal_position"))
			.containsExactly("id", "reporter_id", "target_type", "target_id", "reason", "details", "status",
					"reviewed_by", "reviewed_at", "resolution_note", "created_at");
		Map<String, String> nullable = nativeMap("SELECT column_name, is_nullable FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'reports'");
		for (String required : new String[] { "reporter_id", "target_type", "target_id", "reason", "status",
				"created_at" }) {
			assertThat(nullable).as(required).containsEntry(required, "NO");
		}
		for (String optional : new String[] { "details", "reviewed_by", "reviewed_at", "resolution_note" }) {
			assertThat(nullable).as(optional).containsEntry(optional, "YES");
		}
		assertThat(nativeMap("SELECT column_name, data_type FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'reports' "
				+ "AND column_name IN ('target_type', 'reason', 'status')"))
			.allSatisfy((column, type) -> assertThat(type).as(column).isEqualTo("CHARACTER VARYING"));
		assertThat(nativeMap("SELECT constraint_name, constraint_type FROM information_schema.table_constraints "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'reports'"))
			.containsEntry("pk_reports", "PRIMARY KEY")
			.containsEntry("uk_reports_reporter_target", "UNIQUE")
			.containsEntry("fk_reports_reporter", "FOREIGN KEY")
			.containsEntry("fk_reports_reviewed_by", "FOREIGN KEY");
		assertThat(nativeList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema = SCHEMA() "
				+ "AND constraint_name = 'uk_reports_reporter_target' ORDER BY ordinal_position"))
			.containsExactly("reporter_id", "target_type", "target_id");
		assertThat(nativeMap("SELECT constraint_name, delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = SCHEMA() AND constraint_name LIKE 'fk_reports_%'"))
			.containsEntry("fk_reports_reporter", "RESTRICT")
			.containsEntry("fk_reports_reviewed_by", "RESTRICT");
		assertThat(indexColumns("idx_reports_status_created_id")).containsExactly("status", "created_at", "id");
		assertThat(indexColumns("idx_reports_target")).containsExactly("target_type", "target_id");
		// No polymorphic foreign key to the target.
		assertThat(nativeList("SELECT constraint_name FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = SCHEMA() AND constraint_name LIKE 'fk_reports_%'"))
			.hasSize(2);
	}

	@Test
	void aReporterReportsATargetOnlyOnce() {
		this.reportRepository.saveAndFlush(new Report(this.reporter, ReportTargetType.LISTING, 7L, ReportReason.SPAM, null));
		// Other targets, types and reporters are fine.
		this.reportRepository.saveAndFlush(new Report(this.reporter, ReportTargetType.LISTING, 8L, ReportReason.SPAM, null));
		this.reportRepository.saveAndFlush(new Report(this.reporter, ReportTargetType.USER, 7L, ReportReason.SPAM, null));
		this.reportRepository.saveAndFlush(new Report(this.admin, ReportTargetType.LISTING, 7L, ReportReason.SPAM, null));

		assertThat(this.reportRepository.existsByReporterIdAndTargetTypeAndTargetId(this.reporter.getId(),
				ReportTargetType.LISTING, 7L))
			.isTrue();
		assertThat(this.reportRepository.existsByReporterIdAndTargetTypeAndTargetId(this.reporter.getId(),
				ReportTargetType.MESSAGE, 7L))
			.isFalse();

		// Last: after a failed insert the session cannot be used any further.
		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> this.reportRepository
			.saveAndFlush(new Report(this.reporter, ReportTargetType.LISTING, 7L, ReportReason.OTHER, "again")));
	}

	@Test
	void reviewIsStoredAndReporterAndReviewerAreFetchedTogether() {
		Report report = new Report(this.reporter, ReportTargetType.USER, 5L, ReportReason.HARASSMENT, "details");
		report.resolve(this.admin, "Handled.");
		Long id = this.reportRepository.saveAndFlush(report).getId();
		this.entityManager.clear();

		Report found = this.reportRepository.findWithPeopleById(id).orElseThrow();
		assertThat(Hibernate.isInitialized(found.getReporter())).isTrue();
		assertThat(Hibernate.isInitialized(found.getReviewedBy())).isTrue();
		assertThat(found.getStatus()).isEqualTo(ReportStatus.RESOLVED);
		assertThat(found.getResolutionNote()).isEqualTo("Handled.");
		assertThat(found.getReviewedAt()).isNotNull();
		assertThat(nativeList("SELECT status FROM reports WHERE id = " + id)).containsExactly("RESOLVED");
	}

	@Test
	void reportersAndReviewersCannotBeDeletedWhileReferenced() {
		Report report = new Report(this.reporter, ReportTargetType.USER, 5L, ReportReason.SPAM, null);
		report.dismiss(this.admin, null);
		this.reportRepository.saveAndFlush(report);

		assertThatExceptionOfType(PersistenceException.class)
			.isThrownBy(() -> execute("DELETE FROM users WHERE id = " + this.reporter.getId()));
		assertThatExceptionOfType(PersistenceException.class)
			.isThrownBy(() -> execute("DELETE FROM users WHERE id = " + this.admin.getId()));
	}

	private List<Object> indexColumns(String index) {
		return nativeList("SELECT column_name FROM information_schema.index_columns WHERE table_schema = SCHEMA() "
				+ "AND index_name = '" + index + "' ORDER BY ordinal_position");
	}

	private List<Object> nativeList(String sql) {
		return List.copyOf(this.entityManager.getEntityManager().createNativeQuery(sql).getResultList());
	}

	private Map<String, String> nativeMap(String sql) {
		List<?> rows = this.entityManager.getEntityManager().createNativeQuery(sql).getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.collect(Collectors.toMap(row -> (String) row[0], row -> ((String) row[1]).toUpperCase()));
	}

	private void execute(String sql) {
		this.entityManager.getEntityManager().createNativeQuery(sql).executeUpdate();
	}

}
