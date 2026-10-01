package com.conflux.report;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.conflux.user.User;
import com.conflux.user.UserRepository;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * V9 schema, {@link ModerationAction} and {@link ModerationActionRepository} against the
 * Flyway-migrated H2 database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ModerationActionRepositoryTest {

	@Autowired
	private ModerationActionRepository moderationActionRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private Flyway flyway;

	private User admin;

	private User reporter;

	@BeforeEach
	void setUp() {
		this.admin = this.userRepository.save(new User("admin@example.com", "hash", "admin", "Admin A"));
		this.reporter = this.userRepository.save(new User("reporter@example.com", "hash", "reporter", "Reporter"));
	}

	@Test
	void v9MigrationCreatesModerationActionsTableWithConstraintsAndIndexes() {
		assertThat(this.flyway.info().applied()).filteredOn(m -> "9".equals(m.getVersion().getVersion()))
			.singleElement()
			.satisfies(m -> {
				assertThat(m.getScript()).isEqualTo("V9__create_moderation_actions_table.sql");
				assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS);
			});
		assertThat(nativeList("SELECT column_name FROM information_schema.columns WHERE table_schema = SCHEMA() "
				+ "AND table_name = 'moderation_actions' ORDER BY ordinal_position"))
			.containsExactly("id", "actor_id", "action_type", "target_type", "target_id", "report_id", "note",
					"created_at");
		Map<String, String> nullable = nativeMap("SELECT column_name, is_nullable FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'moderation_actions'");
		for (String required : new String[] { "id", "actor_id", "action_type", "target_type", "target_id",
				"created_at" }) {
			assertThat(nullable).as(required).containsEntry(required, "NO");
		}
		for (String optional : new String[] { "report_id", "note" }) {
			assertThat(nullable).as(optional).containsEntry(optional, "YES");
		}
		assertThat(nativeMap("SELECT column_name, data_type FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'moderation_actions' "
				+ "AND column_name IN ('action_type', 'target_type', 'note')"))
			.hasSize(3)
			.allSatisfy((column, type) -> assertThat(type).as(column).isEqualTo("CHARACTER VARYING"));
		assertThat(nativeList("SELECT character_maximum_length FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'moderation_actions' AND column_name = 'note'"))
			.singleElement()
			.satisfies(length -> assertThat(((Number) length).intValue()).isEqualTo(1_000));
		assertThat(nativeMap("SELECT constraint_name, constraint_type FROM information_schema.table_constraints "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'moderation_actions'"))
			.containsEntry("pk_moderation_actions", "PRIMARY KEY")
			.containsEntry("fk_moderation_actions_actor", "FOREIGN KEY")
			.containsEntry("fk_moderation_actions_report", "FOREIGN KEY");
		// Exactly the actor and report foreign keys: no polymorphic key to the target.
		assertThat(nativeMap("SELECT constraint_name, delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = SCHEMA() AND constraint_name LIKE 'fk_moderation_actions_%'"))
			.containsOnly(Map.entry("fk_moderation_actions_actor", "RESTRICT"),
					Map.entry("fk_moderation_actions_report", "RESTRICT"));
		assertThat(indexColumns("idx_moderation_actions_actor_created")).containsExactly("actor_id", "created_at");
		assertThat(indexColumns("idx_moderation_actions_target_created")).containsExactly("target_type", "target_id",
				"created_at");
		assertThat(indexColumns("idx_moderation_actions_report")).containsExactly("report_id");
	}

	@Test
	void directAndReviewActionsAreStoredWithServerSetFields() {
		Report report = this.reportRepository
			.saveAndFlush(new Report(this.reporter, ReportTargetType.MESSAGE, 12L, ReportReason.HARASSMENT, null));
		report.resolve(this.admin, "Warned.");
		ModerationAction direct = this.moderationActionRepository
			.saveAndFlush(ModerationAction.direct(this.admin, ModerationActionType.SUSPEND_LISTING, 7L));
		ModerationAction review = this.moderationActionRepository
			.saveAndFlush(ModerationAction.review(this.admin, ModerationActionType.RESOLVE_REPORT, report));

		assertThat(direct.getId()).isNotNull();
		assertThat(direct.getCreatedAt()).isNotNull();
		assertThat(direct.getTargetType()).isEqualTo(ReportTargetType.LISTING);
		assertThat(direct.getTargetId()).isEqualTo(7L);
		assertThat(direct.getReport()).isNull();
		assertThat(direct.getNote()).isNull();
		assertThat(review.getTargetType()).isEqualTo(ReportTargetType.MESSAGE);
		assertThat(review.getTargetId()).isEqualTo(12L);
		assertThat(review.getReport()).isSameAs(report);
		assertThat(review.getNote()).isEqualTo("Warned.");
		assertThat(ModerationAction.direct(this.admin, ModerationActionType.RESTORE_USER, 3L).getTargetType())
			.isEqualTo(ReportTargetType.USER);
	}

	@Test
	void invalidActionsAreRejectedBeforeReachingTheDatabase() {
		Report report = new Report(this.reporter, ReportTargetType.USER, 5L, ReportReason.SPAM, null);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ModerationAction.direct(this.admin, ModerationActionType.SUSPEND_USER, 0L));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ModerationAction.direct(this.admin, ModerationActionType.SUSPEND_USER, -1L));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ModerationAction.direct(this.admin, ModerationActionType.RESOLVE_REPORT, 1L));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ModerationAction.review(this.admin, ModerationActionType.SUSPEND_USER, report));
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> ModerationAction.direct(null, ModerationActionType.SUSPEND_USER, 1L));
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> ModerationAction.direct(this.admin, ModerationActionType.SUSPEND_USER, null));
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> ModerationAction.review(this.admin, ModerationActionType.DISMISS_REPORT, null));
	}

	@Test
	void searchProjectsTheActorSummaryAndReportIdInOneQuery() {
		Report report = this.reportRepository
			.saveAndFlush(new Report(this.reporter, ReportTargetType.USER, 9L, ReportReason.SPAM, null));
		report.dismiss(this.admin, null);
		this.moderationActionRepository
			.saveAndFlush(ModerationAction.review(this.admin, ModerationActionType.DISMISS_REPORT, report));
		this.moderationActionRepository
			.saveAndFlush(ModerationAction.direct(this.admin, ModerationActionType.SUSPEND_USER, 9L));
		this.entityManager.clear();

		Page<ModerationActionResponse> page = this.moderationActionRepository.search(null, null, null, null,
				PageRequest.of(0, 10));
		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent()).allSatisfy(action -> {
			assertThat(action.actor()).isEqualTo(new ModerationActionResponse.Actor(this.admin.getId(), "admin",
					"Admin A"));
			assertThat(action.targetType()).isEqualTo(ReportTargetType.USER);
			assertThat(action.targetId()).isEqualTo(9L);
		});
		assertThat(page.getContent()).extracting(ModerationActionResponse::reportId)
			.containsExactlyInAnyOrder(report.getId(), null);

		assertThat(this.moderationActionRepository.search(ModerationActionType.DISMISS_REPORT, ReportTargetType.USER,
				9L, this.admin.getId(), PageRequest.of(0, 10)).getContent())
			.singleElement()
			.satisfies(action -> assertThat(action.reportId()).isEqualTo(report.getId()));
		assertThat(this.moderationActionRepository.search(null, null, null, this.reporter.getId(),
				PageRequest.of(0, 10)))
			.isEmpty();
	}

	@Test
	void theLogIsAppendOnly() {
		// No public mutators on the entity, no delete or update methods on the repository.
		assertThat(Arrays.stream(ModerationAction.class.getDeclaredMethods())
			.filter(method -> Modifier.isPublic(method.getModifiers()))
			.map(Method::getName)).allMatch(name -> name.startsWith("get"));
		assertThat(Arrays.stream(ModerationAction.class.getDeclaredConstructors())
			.filter(constructor -> Modifier.isPublic(constructor.getModifiers()))).isEmpty();
		assertThat(Arrays.stream(ModerationActionRepository.class.getMethods()).map(Method::getName))
			.containsExactlyInAnyOrder("saveAndFlush", "search");
		assertThat(ModerationAction.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();
	}

	@Test
	void actorsAndReportsCannotBeDeletedWhileReferenced() {
		Report report = this.reportRepository
			.saveAndFlush(new Report(this.reporter, ReportTargetType.USER, 5L, ReportReason.SPAM, null));
		report.resolve(this.admin, null);
		this.moderationActionRepository
			.saveAndFlush(ModerationAction.review(this.admin, ModerationActionType.RESOLVE_REPORT, report));

		assertThatExceptionOfType(PersistenceException.class)
			.isThrownBy(() -> execute("DELETE FROM reports WHERE id = " + report.getId()));
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
