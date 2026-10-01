package com.conflux.report;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.conflux.auth.JwtTokenService;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The moderation audit log over HTTP: full context, real security filter chain,
 * Flyway-migrated H2 database. Same cast as {@link TrustAndSafetyIntegrationTest}: Alice is
 * ADMIN (Zoe is a second admin); Bob and Charlie are users; Dave is suspended. Bob has a
 * published and a draft listing; Charlie has a published one. Charlie's interest in Bob's
 * listing is accepted and Bob has sent a message. Nothing is moderated during setup, so the
 * log starts empty.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModerationAuditIntegrationTest {

	private static final String LISTINGS = "/api/v1/listings";

	private static final String REPORTS = "/api/v1/reports";

	private static final String ADMIN = "/api/v1/admin";

	private static final String ADMIN_REPORTS = ADMIN + "/reports";

	private static final String AUDIT = ADMIN + "/moderation-actions";

	private static final String REJECT_AUDIT_INSERTS = "ck_test_reject_audit_inserts";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private JdbcTemplate jdbc;

	private User alice;

	private User zoe;

	private User bob;

	private User charlie;

	private User dave;

	private String aliceToken;

	private String zoeToken;

	private String bobToken;

	private String charlieToken;

	private long bobPublished;

	private long bobDraft;

	private long charliePublished;

	private long bobMessage;

	@BeforeEach
	void setUp() throws Exception {
		cleanDatabase();
		this.alice = this.userRepository.save(admin("alice", "Alice Admin"));
		this.zoe = this.userRepository.save(admin("zoe", "Zoe Admin"));
		this.bob = this.userRepository.save(new User("bob@example.com", "unused-hash", "bob", "Bob B"));
		this.charlie = this.userRepository.save(new User("charlie@example.com", "unused-hash", "charlie", "Charlie C"));
		User suspended = new User("dave@example.com", "unused-hash", "dave", "Dave D");
		suspended.suspend();
		this.dave = this.userRepository.save(suspended);
		this.aliceToken = token(this.alice);
		this.zoeToken = token(this.zoe);
		this.bobToken = token(this.bob);
		this.charlieToken = token(this.charlie);

		this.bobPublished = published(this.bobToken, "Bob published");
		this.bobDraft = create(this.bobToken, "Bob draft");
		this.charliePublished = published(this.charlieToken, "Charlie published");

		long connection = idOf(perform(post(LISTINGS + "/" + this.bobPublished + "/interest"), this.charlieToken)
			.andExpect(status().isCreated()));
		perform(post("/api/v1/connections/" + connection + "/accept"), this.bobToken).andExpect(status().isOk());
		long conversation = this.jdbc.queryForObject("SELECT id FROM conversations WHERE connection_id = ?",
				Long.class, connection);
		this.bobMessage = idOf(perform(post("/api/v1/conversations/" + conversation + "/messages")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"Hello from Bob\"}"), this.bobToken).andExpect(status().isCreated()));
		assertThat(auditCount()).isZero();
	}

	@AfterEach
	void cleanDatabase() {
		this.jdbc.execute("ALTER TABLE moderation_actions DROP CONSTRAINT IF EXISTS " + REJECT_AUDIT_INSERTS);
		for (String table : new String[] { "moderation_actions", "reports", "messages", "conversations",
				"connections", "saved_listings", "listings", "users" }) {
			this.jdbc.update("DELETE FROM " + table);
		}
	}

	// ---- Audit creation ------------------------------------------------------------------

	@Test
	void successfulUserSuspensionCreatesExactlyOneSuspendUserAction() throws Exception {
		Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
		perform(post(ADMIN + "/users/" + this.bob.getId() + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());
		Instant after = Instant.now();

		assertThat(auditRows()).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("actor_id", this.alice.getId())
				.containsEntry("action_type", "SUSPEND_USER")
				.containsEntry("target_type", "USER")
				.containsEntry("target_id", this.bob.getId())
				.containsEntry("report_id", null)
				.containsEntry("note", null));
		String response = audit("").andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].actor.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.content[0].actionType").value("SUSPEND_USER"))
			.andExpect(jsonPath("$.content[0].reportId").value(nullValue()))
			.andExpect(jsonPath("$.content[0].note").value(nullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		// createdAt is set by the server, in UTC, and matches the stored value.
		Instant createdAt = Instant.parse(JsonPath.read(response, "$.content[0].createdAt"));
		assertThat(createdAt).isBetween(before, after);
		assertThat(this.jdbc.queryForObject("SELECT created_at FROM moderation_actions", LocalDateTime.class))
			.isEqualTo(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC));
	}

	@Test
	void repeatedIdempotentOperationsCreateNoSecondAction() throws Exception {
		for (int i = 0; i < 3; i++) {
			perform(post(ADMIN + "/users/" + this.bob.getId() + "/suspend"), this.aliceToken)
				.andExpect(status().isNoContent());
			perform(post(ADMIN + "/listings/" + this.charliePublished + "/suspend"), this.aliceToken)
				.andExpect(status().isNoContent());
		}
		assertThat(actionTypes()).containsExactlyInAnyOrder("SUSPEND_USER", "SUSPEND_LISTING");

		for (int i = 0; i < 3; i++) {
			perform(post(ADMIN + "/users/" + this.bob.getId() + "/restore"), this.aliceToken)
				.andExpect(status().isNoContent());
			perform(post(ADMIN + "/listings/" + this.charliePublished + "/restore"), this.aliceToken)
				.andExpect(status().isNoContent());
		}
		assertThat(actionTypes()).containsExactlyInAnyOrder("SUSPEND_USER", "SUSPEND_LISTING", "RESTORE_USER",
				"RESTORE_LISTING");

		// Already in the requested state: 204 as before, nothing recorded.
		perform(post(ADMIN + "/users/" + this.charlie.getId() + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());
		perform(post(ADMIN + "/listings/" + this.bobPublished + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());
		assertThat(auditCount()).isEqualTo(4);
	}

	@Test
	void successfulUserRestoreCreatesRestoreUser() throws Exception {
		perform(post(ADMIN + "/users/" + this.dave.getId() + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());

		assertThat(userStatus(this.dave)).isEqualTo("ACTIVE");
		assertThat(auditRows()).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("actor_id", this.alice.getId())
				.containsEntry("action_type", "RESTORE_USER")
				.containsEntry("target_type", "USER")
				.containsEntry("target_id", this.dave.getId())
				.containsEntry("report_id", null)
				.containsEntry("note", null));
	}

	@Test
	void successfulListingSuspensionCreatesSuspendListing() throws Exception {
		perform(post(ADMIN + "/listings/" + this.bobPublished + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());

		assertThat(listingStatus(this.bobPublished)).isEqualTo("SUSPENDED");
		assertThat(auditRows()).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("actor_id", this.alice.getId())
				.containsEntry("action_type", "SUSPEND_LISTING")
				.containsEntry("target_type", "LISTING")
				.containsEntry("target_id", this.bobPublished)
				.containsEntry("report_id", null)
				.containsEntry("note", null));
	}

	@Test
	void successfulListingRestoreCreatesRestoreListing() throws Exception {
		perform(post(ADMIN + "/listings/" + this.bobPublished + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());
		perform(post(ADMIN + "/listings/" + this.bobPublished + "/restore"), this.zoeToken)
			.andExpect(status().isNoContent());

		assertThat(listingStatus(this.bobPublished)).isEqualTo("PUBLISHED");
		assertThat(this.jdbc.queryForMap("SELECT actor_id, target_type, target_id, report_id FROM moderation_actions "
				+ "WHERE action_type = 'RESTORE_LISTING'"))
			.containsEntry("actor_id", this.zoe.getId())
			.containsEntry("target_type", "LISTING")
			.containsEntry("target_id", this.bobPublished)
			.containsEntry("report_id", null);
		assertThat(auditCount()).isEqualTo(2);
	}

	@Test
	void successfulReportResolutionCreatesResolveReportLinkedToTheReport() throws Exception {
		long report = reportId(this.charlieToken, "LISTING", this.bobPublished);
		perform(post(ADMIN_REPORTS + "/" + report + "/resolve").contentType(MediaType.APPLICATION_JSON)
			.content("{\"resolutionNote\":\"  Valid report; owner contacted.  \"}"), this.aliceToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RESOLVED"));

		assertThat(auditRows()).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("actor_id", this.alice.getId())
				.containsEntry("action_type", "RESOLVE_REPORT")
				.containsEntry("target_type", "LISTING")
				.containsEntry("target_id", this.bobPublished)
				.containsEntry("report_id", report)
				.containsEntry("note", "Valid report; owner contacted."));
		audit("").andExpect(jsonPath("$.content[0].reportId").value(report))
			.andExpect(jsonPath("$.content[0].note").value("Valid report; owner contacted."));
		// The report itself was reviewed by the same admin.
		assertThat(this.jdbc.queryForObject("SELECT reviewed_by FROM reports WHERE id = ?", Long.class, report))
			.isEqualTo(this.alice.getId());
	}

	@Test
	void successfulReportDismissalCreatesDismissReportLinkedToTheReport() throws Exception {
		long messageReport = reportId(this.charlieToken, "MESSAGE", this.bobMessage);
		long userReport = reportId(this.charlieToken, "USER", this.bob.getId());
		perform(post(ADMIN_REPORTS + "/" + messageReport + "/dismiss"), this.aliceToken).andExpect(status().isOk());
		perform(post(ADMIN_REPORTS + "/" + userReport + "/dismiss").contentType(MediaType.APPLICATION_JSON)
			.content("{\"resolutionNote\":\"No violation.\"}"), this.zoeToken)
			.andExpect(status().isOk());

		assertThat(this.jdbc.queryForMap("SELECT actor_id, action_type, target_type, target_id, note "
				+ "FROM moderation_actions WHERE report_id = ?", messageReport))
			.containsEntry("actor_id", this.alice.getId())
			.containsEntry("action_type", "DISMISS_REPORT")
			.containsEntry("target_type", "MESSAGE")
			.containsEntry("target_id", this.bobMessage)
			.containsEntry("note", null);
		assertThat(this.jdbc.queryForMap("SELECT actor_id, action_type, target_type, target_id, note "
				+ "FROM moderation_actions WHERE report_id = ?", userReport))
			.containsEntry("actor_id", this.zoe.getId())
			.containsEntry("action_type", "DISMISS_REPORT")
			.containsEntry("target_type", "USER")
			.containsEntry("target_id", this.bob.getId())
			.containsEntry("note", "No violation.");
		assertThat(auditCount()).isEqualTo(2);
	}

	@Test
	void failedModerationOperationsCreateNoAuditAction() throws Exception {
		long reviewed = reportId(this.charlieToken, "LISTING", this.bobPublished);
		long open = reportId(this.charlieToken, "USER", this.bob.getId());
		perform(post(ADMIN_REPORTS + "/" + reviewed + "/dismiss"), this.aliceToken).andExpect(status().isOk());
		assertThat(auditCount()).isEqualTo(1);

		// 404, 409 and 400 from an administrator.
		for (String action : new String[] { "suspend", "restore" }) {
			perform(post(ADMIN + "/users/999999999/" + action), this.aliceToken).andExpect(status().isNotFound());
			perform(post(ADMIN + "/listings/999999999/" + action), this.aliceToken).andExpect(status().isNotFound());
			perform(post(ADMIN + "/listings/" + this.bobDraft + "/" + action), this.aliceToken)
				.andExpect(status().isConflict());
		}
		perform(post(ADMIN + "/users/" + this.alice.getId() + "/suspend"), this.aliceToken)
			.andExpect(status().isConflict());
		for (String action : new String[] { "resolve", "dismiss" }) {
			perform(post(ADMIN_REPORTS + "/" + reviewed + "/" + action), this.aliceToken)
				.andExpect(status().isConflict());
			perform(post(ADMIN_REPORTS + "/999999999/" + action), this.aliceToken).andExpect(status().isNotFound());
		}
		perform(post(ADMIN_REPORTS + "/" + open + "/resolve").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("resolutionNote", "n".repeat(1_001)))), this.aliceToken)
			.andExpect(status().isBadRequest());

		// 403 and 401: normal users, anonymous callers, a suspended admin.
		// (Fresh request builders each time: perform() adds the Authorization header to them.)
		Supplier<List<MockHttpServletRequestBuilder>> operations = () -> List.of(
				post(ADMIN + "/users/" + this.charlie.getId() + "/suspend"),
				post(ADMIN + "/users/" + this.dave.getId() + "/restore"),
				post(ADMIN + "/listings/" + this.charliePublished + "/suspend"),
				post(ADMIN_REPORTS + "/" + open + "/resolve"), post(ADMIN_REPORTS + "/" + open + "/dismiss"));
		for (MockHttpServletRequestBuilder operation : operations.get()) {
			perform(operation, this.bobToken).andExpect(status().isForbidden());
		}
		for (MockHttpServletRequestBuilder operation : operations.get()) {
			this.mockMvc.perform(operation).andExpect(status().isUnauthorized());
		}
		this.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", this.zoe.getId());
		for (MockHttpServletRequestBuilder operation : operations.get()) {
			perform(operation, this.zoeToken).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("This account is suspended."));
		}

		assertThat(auditCount()).isEqualTo(1);
		assertThat(actionTypes()).containsExactly("DISMISS_REPORT");
		assertThat(reportStatus(open)).isEqualTo("OPEN");
		assertThat(userStatus(this.charlie)).isEqualTo("ACTIVE");
		assertThat(listingStatus(this.charliePublished)).isEqualTo("PUBLISHED");
	}

	@Test
	void clientsCannotChooseTheActorOrTheAuditTime() throws Exception {
		Map<String, Object> forged = new LinkedHashMap<>();
		forged.put("actorId", this.zoe.getId());
		forged.put("actor", Map.of("id", this.zoe.getId()));
		forged.put("createdAt", "2020-01-01T00:00:00Z");
		forged.put("reportId", 123);
		forged.put("note", "forged note");
		long report = reportId(this.charlieToken, "LISTING", this.bobPublished);
		perform(post(ADMIN + "/users/" + this.bob.getId() + "/suspend").param("actorId", this.zoe.getId().toString())
			.param("createdAt", "2020-01-01T00:00:00Z")
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(forged)), this.aliceToken)
			.andExpect(status().isNoContent());
		forged.put("resolutionNote", "real note");
		perform(post(ADMIN_REPORTS + "/" + report + "/resolve").param("actorId", this.zoe.getId().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(forged)), this.aliceToken)
			.andExpect(status().isOk());

		assertThat(this.jdbc.queryForList("SELECT DISTINCT actor_id FROM moderation_actions", Long.class))
			.containsExactly(this.alice.getId());
		assertThat(this.jdbc.queryForList("SELECT note FROM moderation_actions ORDER BY id", String.class))
			.containsExactly(null, "real note");
		assertThat(this.jdbc.queryForList("SELECT report_id FROM moderation_actions ORDER BY id", Long.class))
			.containsExactly(null, report);
		assertThat(this.jdbc.queryForList("SELECT created_at FROM moderation_actions", LocalDateTime.class))
			.allSatisfy(createdAt -> assertThat(createdAt).isAfter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)));

		// And there is no way to write an entry directly.
		perform(post(AUDIT).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(forged)),
				this.aliceToken)
			.andExpect(status().isMethodNotAllowed());
		assertThat(auditCount()).isEqualTo(2);
	}

	// ---- Atomicity ---------------------------------------------------------------------------

	@Test
	void failedAuditInsertRollsBackTheUserSuspension() throws Exception {
		rejectAuditInserts();

		perform(post(ADMIN + "/users/" + this.bob.getId() + "/suspend"), this.aliceToken)
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
		perform(post(ADMIN + "/users/" + this.dave.getId() + "/restore"), this.aliceToken)
			.andExpect(status().isInternalServerError());

		assertThat(userStatus(this.bob)).isEqualTo("ACTIVE");
		assertThat(userStatus(this.dave)).isEqualTo("SUSPENDED");
		this.mockMvc.perform(get("/api/v1/users/bob")).andExpect(status().isOk());
		assertThat(auditCount()).isZero();
	}

	@Test
	void failedAuditInsertRollsBackListingModeration() throws Exception {
		perform(post(ADMIN + "/listings/" + this.charliePublished + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());
		rejectAuditInserts();

		perform(post(ADMIN + "/listings/" + this.bobPublished + "/suspend"), this.aliceToken)
			.andExpect(status().isInternalServerError());
		perform(post(ADMIN + "/listings/" + this.charliePublished + "/restore"), this.aliceToken)
			.andExpect(status().isInternalServerError());

		assertThat(listingStatus(this.bobPublished)).isEqualTo("PUBLISHED");
		assertThat(listingStatus(this.charliePublished)).isEqualTo("SUSPENDED");
		assertThat(actionTypes()).containsExactly("SUSPEND_LISTING");
	}

	@Test
	void failedAuditInsertRollsBackReportResolutionAndDismissal() throws Exception {
		long resolve = reportId(this.charlieToken, "LISTING", this.bobPublished);
		long dismiss = reportId(this.charlieToken, "USER", this.bob.getId());
		rejectAuditInserts();

		String response = perform(post(ADMIN_REPORTS + "/" + resolve + "/resolve").contentType(MediaType.APPLICATION_JSON)
			.content("{\"resolutionNote\":\"note\"}"), this.aliceToken)
			.andExpect(status().isInternalServerError())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(response).doesNotContain(REJECT_AUDIT_INSERTS).doesNotContain("moderation_actions");
		perform(post(ADMIN_REPORTS + "/" + dismiss + "/dismiss"), this.aliceToken)
			.andExpect(status().isInternalServerError());

		for (long report : new long[] { resolve, dismiss }) {
			assertThat(this.jdbc.queryForMap("SELECT status, reviewed_by, reviewed_at, resolution_note FROM reports "
					+ "WHERE id = ?", report))
				.containsEntry("status", "OPEN")
				.containsEntry("reviewed_by", null)
				.containsEntry("reviewed_at", null)
				.containsEntry("resolution_note", null);
		}
		assertThat(auditCount()).isZero();

		// Once inserts work again, the same reviews succeed and are recorded.
		this.jdbc.execute("ALTER TABLE moderation_actions DROP CONSTRAINT " + REJECT_AUDIT_INSERTS);
		perform(post(ADMIN_REPORTS + "/" + resolve + "/resolve"), this.aliceToken).andExpect(status().isOk());
		perform(post(ADMIN_REPORTS + "/" + dismiss + "/dismiss"), this.aliceToken).andExpect(status().isOk());
		assertThat(actionTypes()).containsExactlyInAnyOrder("RESOLVE_REPORT", "DISMISS_REPORT");
	}

	// ---- Audit queries -----------------------------------------------------------------------

	@Test
	void onlyActiveAdminsCanReadTheLog() throws Exception {
		perform(post(ADMIN + "/users/" + this.bob.getId() + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());

		perform(get(AUDIT), this.aliceToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1));
		perform(get(AUDIT), this.zoeToken).andExpect(status().isOk());
		perform(get(AUDIT), this.charlieToken).andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("You do not have permission to access this resource."));
		this.mockMvc.perform(get(AUDIT)).andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(get(AUDIT).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
			.andExpect(status().isUnauthorized());
		// A token minted while admin stops working once demoted or suspended.
		this.jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", this.zoe.getId());
		perform(get(AUDIT), this.zoeToken).andExpect(status().isForbidden());
		this.jdbc.update("UPDATE users SET role = 'ADMIN', status = 'SUSPENDED' WHERE id = ?", this.zoe.getId());
		perform(get(AUDIT), this.zoeToken).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("This account is suspended."));
	}

	@Test
	void logIsOrderedByCreatedAtThenIdDescendingAndPaginated() throws Exception {
		long a = directAction("/users/" + this.bob.getId() + "/suspend");
		long b = directAction("/users/" + this.bob.getId() + "/restore");
		long c = directAction("/listings/" + this.bobPublished + "/suspend");
		long d = directAction("/listings/" + this.bobPublished + "/restore");
		long e = directAction("/listings/" + this.charliePublished + "/suspend");
		Instant base = Instant.parse("2026-10-01T08:00:00Z");
		setCreatedAt(a, base.plusSeconds(60));
		setCreatedAt(b, base);
		setCreatedAt(c, base.plusSeconds(60)); // same as a: higher id first
		setCreatedAt(d, base.plusSeconds(120));
		setCreatedAt(e, base.minusSeconds(60));

		assertThat(ids(audit(""))).containsExactly(d, c, a, b, e);
		audit("").andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.totalElements").value(5));

		audit("page=0&size=2").andExpect(jsonPath("$.totalElements").value(5))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(false));
		assertThat(ids(audit("page=0&size=2"))).containsExactly(d, c);
		assertThat(ids(audit("page=1&size=2"))).containsExactly(a, b);
		assertThat(ids(audit("page=2&size=2"))).containsExactly(e);
		audit("page=2&size=2").andExpect(jsonPath("$.last").value(true));
		assertThat(ids(audit("page=3&size=2"))).isEmpty();
		audit("page=10000").andExpect(status().isOk());
		audit("size=50").andExpect(status().isOk());
	}

	@Test
	void logCanBeFilteredByActionTypeTargetAndActor() throws Exception {
		long bobSuspended = directAction("/users/" + this.bob.getId() + "/suspend");
		long bobRestored = directAction("/users/" + this.bob.getId() + "/restore");
		long listingSuspended = directAction("/listings/" + this.bobPublished + "/suspend");
		long charlieListingSuspended = directAction(this.zoeToken, "/listings/" + this.charliePublished + "/suspend");
		long daveRestored = directAction(this.zoeToken, "/users/" + this.dave.getId() + "/restore");
		long report = reportId(this.charlieToken, "USER", this.bob.getId());
		perform(post(ADMIN_REPORTS + "/" + report + "/resolve"), this.zoeToken).andExpect(status().isOk());
		long reportResolved = this.jdbc.queryForObject("SELECT id FROM moderation_actions WHERE report_id = ?",
				Long.class, report);

		// actionType
		assertThat(ids(audit("actionType=SUSPEND_USER"))).containsExactly(bobSuspended);
		assertThat(ids(audit("actionType=RESTORE_USER"))).containsExactlyInAnyOrder(bobRestored, daveRestored);
		assertThat(ids(audit("actionType=SUSPEND_LISTING"))).containsExactlyInAnyOrder(listingSuspended,
				charlieListingSuspended);
		assertThat(ids(audit("actionType=RESOLVE_REPORT"))).containsExactly(reportResolved);
		assertThat(ids(audit("actionType=DISMISS_REPORT"))).isEmpty();
		// targetType
		assertThat(ids(audit("targetType=USER"))).containsExactlyInAnyOrder(bobSuspended, bobRestored, daveRestored,
				reportResolved);
		assertThat(ids(audit("targetType=LISTING"))).containsExactlyInAnyOrder(listingSuspended,
				charlieListingSuspended);
		assertThat(ids(audit("targetType=MESSAGE"))).isEmpty();
		// targetId
		assertThat(ids(audit("targetId=" + this.bob.getId()))).containsExactlyInAnyOrder(bobSuspended, bobRestored,
				reportResolved);
		assertThat(ids(audit("targetId=" + this.charliePublished))).containsExactly(charlieListingSuspended);
		// actorId
		assertThat(ids(audit("actorId=" + this.alice.getId()))).containsExactlyInAnyOrder(bobSuspended, bobRestored,
				listingSuspended);
		assertThat(ids(audit("actorId=" + this.zoe.getId()))).containsExactlyInAnyOrder(charlieListingSuspended,
				daveRestored, reportResolved);
		assertThat(ids(audit("actorId=" + this.bob.getId()))).isEmpty();
		// Combined (AND)
		assertThat(ids(audit("targetType=USER&targetId=" + this.bob.getId() + "&actorId=" + this.alice.getId())))
			.containsExactlyInAnyOrder(bobSuspended, bobRestored);
		assertThat(ids(audit("actionType=RESOLVE_REPORT&targetType=USER&targetId=" + this.bob.getId() + "&actorId="
				+ this.zoe.getId())))
			.containsExactly(reportResolved);
		assertThat(ids(audit("actionType=SUSPEND_LISTING&actorId=" + this.zoe.getId())))
			.containsExactly(charlieListingSuspended);
		assertThat(ids(audit("actionType=SUSPEND_USER&actorId=" + this.zoe.getId()))).isEmpty();
		audit("actionType=RESTORE_USER&page=0&size=1").andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void invalidFiltersAndPagingAre400() throws Exception {
		for (String query : new String[] { "actionType=suspend_user", "actionType=BAN_USER", "targetType=user", "targetType=REPORT", "targetId=0", "targetId=-1", "targetId=abc", "actorId=0",
				"actorId=-7", "actorId=x", "page=-1", "page=10001", "size=0", "size=51", "size=abc" }) {
			audit(query).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		// An empty value means "not supplied", as for the report queue's status filter.
		audit("actionType=").andExpect(status().isOk());
	}

	@Test
	void emptyResultsAre200() throws Exception {
		audit("").andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0));
		directAction("/users/" + this.bob.getId() + "/suspend");
		audit("targetId=999999999").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
	}

	// ---- Data exposure -------------------------------------------------------------------------

	@Test
	void entriesContainOnlyTheDocumentedFieldsAndAnActorSummary() throws Exception {
		long report = reportId(this.charlieToken, "LISTING", this.bobPublished);
		directAction("/users/" + this.bob.getId() + "/suspend");
		perform(post(ADMIN_REPORTS + "/" + report + "/resolve").contentType(MediaType.APPLICATION_JSON)
			.content("{\"resolutionNote\":\"Handled.\"}"), this.aliceToken)
			.andExpect(status().isOk());

		String response = audit("").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		JsonNode page = JSON.readTree(response);
		assertThat(page.propertyNames()).containsExactlyInAnyOrder("content", "page", "size", "totalElements",
				"totalPages", "first", "last");
		assertThat(page.get("content")).hasSize(2).allSatisfy(entry -> {
			assertThat(entry.propertyNames()).containsExactlyInAnyOrder("id", "actor", "actionType", "targetType",
					"targetId", "reportId", "note", "createdAt");
			assertThat(entry.get("actor").propertyNames()).containsExactlyInAnyOrder("id", "username",
					"displayName");
			assertThat(entry.get("actor").get("id").asLong()).isEqualTo(this.alice.getId());
			assertThat(entry.get("actor").get("username").asString()).isEqualTo("alice");
			assertThat(entry.get("actor").get("displayName").asString()).isEqualTo("Alice Admin");
			// The report is a reference, not an embedded entity.
			assertThat(entry.get("reportId").isObject()).isFalse();
		});
		assertThat(response).doesNotContain("@example.com")
			.doesNotContain("email")
			.doesNotContain("passwordHash")
			.doesNotContain("password")
			.doesNotContain("unused-hash")
			.doesNotContain("role")
			.doesNotContain("hibernateLazyInitializer")
			.doesNotContain("reporter")
			.doesNotContain("eyJ");
	}

	// ---- Immutability ---------------------------------------------------------------------------

	@Test
	void thereIsNoWayToChangeOrDeleteEntries() throws Exception {
		long id = directAction("/users/" + this.bob.getId() + "/suspend");
		Map<String, Object> before = auditRow(id);
		String body = "{\"actionType\":\"RESTORE_USER\",\"note\":\"rewritten\"}";

		for (MockHttpServletRequestBuilder request : List.of(put(AUDIT), patch(AUDIT), delete(AUDIT), post(AUDIT))) {
			perform(request.contentType(MediaType.APPLICATION_JSON).content(body), this.aliceToken)
				.andExpect(status().isMethodNotAllowed());
		}
		for (MockHttpServletRequestBuilder request : List.of(get(AUDIT + "/" + id), put(AUDIT + "/" + id),
				patch(AUDIT + "/" + id), delete(AUDIT + "/" + id), post(AUDIT + "/" + id))) {
			perform(request.contentType(MediaType.APPLICATION_JSON).content(body), this.aliceToken)
				.andExpect(status().is(anyOf(is(404), is(405))));
		}
		// Normal users and anonymous callers are stopped before any of that.
		perform(delete(AUDIT + "/" + id), this.bobToken).andExpect(status().isForbidden());
		this.mockMvc.perform(delete(AUDIT + "/" + id)).andExpect(status().isUnauthorized());

		assertThat(auditCount()).isEqualTo(1);
		assertThat(auditRow(id)).isEqualTo(before);
	}

	@Test
	void entriesRemainUnchangedAfterLaterModeration() throws Exception {
		long report = reportId(this.charlieToken, "LISTING", this.bobPublished);
		perform(post(ADMIN_REPORTS + "/" + report + "/resolve").contentType(MediaType.APPLICATION_JSON)
			.content("{\"resolutionNote\":\"First.\"}"), this.aliceToken)
			.andExpect(status().isOk());
		long first = directAction("/users/" + this.bob.getId() + "/suspend");
		long resolved = this.jdbc.queryForObject("SELECT id FROM moderation_actions WHERE report_id = ?", Long.class,
				report);
		Map<String, Object> firstBefore = auditRow(first);
		Map<String, Object> resolvedBefore = auditRow(resolved);

		directAction("/users/" + this.bob.getId() + "/restore");
		directAction(this.zoeToken, "/users/" + this.bob.getId() + "/suspend");
		directAction("/users/" + this.bob.getId() + "/restore");
		directAction("/listings/" + this.bobPublished + "/suspend");
		directAction("/listings/" + this.bobPublished + "/restore");

		assertThat(auditRow(first)).isEqualTo(firstBefore);
		assertThat(auditRow(resolved)).isEqualTo(resolvedBefore);
		assertThat(actionTypes()).containsExactlyInAnyOrder("RESOLVE_REPORT", "SUSPEND_USER", "RESTORE_USER",
				"SUSPEND_USER", "RESTORE_USER", "SUSPEND_LISTING", "RESTORE_LISTING");
		assertThat(ids(audit("targetType=USER&targetId=" + this.bob.getId()))).hasSize(4).endsWith(first);
	}

	// ---- Regression ----------------------------------------------------------------------------

	@Test
	void selfServiceActionsAndReportsAreNeverAudited() throws Exception {
		// Publishing is immediate and needs no approval; reporting moderates and records nothing.
		long listing = published(this.charlieToken, "Charlie's second idea");
		perform(get(LISTINGS + "/" + slugOf(listing)), null).andExpect(status().isOk());
		perform(delete(LISTINGS + "/" + listing), this.charlieToken).andExpect(status().isNoContent());
		reportId(this.bobToken, "LISTING", this.charliePublished);
		reportId(this.bobToken, "USER", this.charlie.getId());
		perform(post(LISTINGS + "/" + this.charliePublished + "/save"), this.bobToken)
			.andExpect(status().isNoContent());

		assertThat(listingStatus(this.charliePublished)).isEqualTo("PUBLISHED");
		assertThat(userStatus(this.charlie)).isEqualTo("ACTIVE");
		assertThat(auditCount()).isZero();
	}

	// ---- Helpers ------------------------------------------------------------------------------

	private void rejectAuditInserts() {
		// A real database-level failure of every further audit INSERT; existing rows stay valid.
		long maxId = this.jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM moderation_actions", Long.class);
		this.jdbc.execute("ALTER TABLE moderation_actions ADD CONSTRAINT " + REJECT_AUDIT_INSERTS + " CHECK (id <= "
				+ maxId + ")");
	}

	private long directAction(String path) throws Exception {
		return directAction(this.aliceToken, path);
	}

	/**
	 * Performs a state-changing moderation operation and returns the id of its audit entry.
	 */
	private long directAction(String token, String path) throws Exception {
		long before = auditCount();
		perform(post(ADMIN + path), token).andExpect(status().isNoContent());
		assertThat(auditCount()).as(path).isEqualTo(before + 1);
		return this.jdbc.queryForObject("SELECT MAX(id) FROM moderation_actions", Long.class);
	}

	private ResultActions audit(String query) throws Exception {
		MockHttpServletRequestBuilder request = get(AUDIT);
		if (!query.isEmpty()) {
			for (String pair : query.split("&")) {
				String[] parts = pair.split("=", 2);
				request.param(parts[0], parts[1]);
			}
		}
		return perform(request, this.aliceToken);
	}

	private static List<Long> ids(ResultActions result) throws Exception {
		String response = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(response, "$.content[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private long reportId(String token, String type, long targetId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("targetType", type);
		body.put("targetId", targetId);
		body.put("reason", "SPAM");
		return idOf(perform(post(REPORTS).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)),
				token)
			.andExpect(status().isCreated()));
	}

	private List<Map<String, Object>> auditRows() {
		return this.jdbc.queryForList("SELECT actor_id, action_type, target_type, target_id, report_id, note "
				+ "FROM moderation_actions ORDER BY id");
	}

	private Map<String, Object> auditRow(long id) {
		return this.jdbc.queryForMap("SELECT * FROM moderation_actions WHERE id = ?", id);
	}

	private List<String> actionTypes() {
		return this.jdbc.queryForList("SELECT action_type FROM moderation_actions ORDER BY id", String.class);
	}

	private long auditCount() {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM moderation_actions", Long.class);
	}

	private void setCreatedAt(long actionId, Instant at) {
		this.jdbc.update("UPDATE moderation_actions SET created_at = ? WHERE id = ?",
				LocalDateTime.ofInstant(at, ZoneOffset.UTC), actionId);
	}

	private String reportStatus(long id) {
		return this.jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, id);
	}

	private String listingStatus(long id) {
		return this.jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, id);
	}

	private String userStatus(User user) {
		return this.jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, user.getId());
	}

	private String slugOf(long listing) {
		return this.jdbc.queryForObject("SELECT slug FROM listings WHERE id = ?", String.class, listing);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
		return this.mockMvc
			.perform((token != null) ? request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token) : request);
	}

	private String token(User user) {
		return this.tokenService.issueAccessToken(user).accessToken();
	}

	private static User admin(String username, String displayName) {
		User admin = new User(username + "@example.com", "unused-hash", username, displayName);
		admin.setRole(UserRole.ADMIN);
		return admin;
	}

	private long published(String token, String title) throws Exception {
		long id = create(token, title);
		perform(post(LISTINGS + "/" + id + "/publish"), token).andExpect(status().isOk());
		return id;
	}

	private long create(String token, String title) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("shortPitch", "A short pitch.");
		body.put("description", "A full description.");
		body.put("assetType", "PROJECT");
		body.put("marketplaceMode", "COLLABORATE");
		body.put("category", "SAAS");
		body.put("stage", "PROTOTYPE");
		return idOf(perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)),
				token)
			.andExpect(status().isCreated()));
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
