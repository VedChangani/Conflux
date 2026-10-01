package com.conflux.report;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.conflux.auth.JwtTokenService;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reporting and reactive trust &amp; safety over HTTP: full context, real security filter
 * chain, Flyway-migrated H2 database. Alice is ADMIN; Bob and Charlie are users; Dave is
 * suspended. Bob has a published, a draft, an archived and a (moderator-)suspended listing;
 * Charlie has a published one. Charlie's interest in Bob's listing is accepted and both have
 * sent a message. Everything is set up through the real endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrustAndSafetyIntegrationTest {

	private static final String LISTINGS = "/api/v1/listings";

	private static final String REPORTS = "/api/v1/reports";

	private static final String ADMIN_REPORTS = "/api/v1/admin/reports";

	private static final String ADMIN = "/api/v1/admin";

	private static final String FORBIDDEN = "You do not have permission to access this resource.";

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

	private User bob;

	private User charlie;

	private User dave;

	private String aliceToken;

	private String bobToken;

	private String charlieToken;

	private String daveToken;

	private long bobPublished;

	private long bobDraft;

	private long bobArchived;

	private long bobSuspended;

	private long charliePublished;

	private long connection;

	private long conversation;

	private long bobMessage;

	private long charlieMessage;

	@BeforeEach
	void setUp() throws Exception {
		cleanDatabase();
		User admin = new User("alice@example.com", "unused-hash", "alice", "Alice Admin");
		admin.setRole(UserRole.ADMIN);
		this.alice = this.userRepository.save(admin);
		this.bob = this.userRepository.save(new User("bob@example.com", "unused-hash", "bob", "Bob B"));
		this.charlie = this.userRepository.save(new User("charlie@example.com", "unused-hash", "charlie", "Charlie C"));
		User suspended = new User("dave@example.com", "unused-hash", "dave", "Dave D");
		suspended.suspend();
		this.dave = this.userRepository.save(suspended);
		this.aliceToken = token(this.alice);
		this.bobToken = token(this.bob);
		this.charlieToken = token(this.charlie);
		this.daveToken = token(this.dave);

		this.bobPublished = published(this.bobToken, "Bob published");
		this.bobDraft = create(this.bobToken, "Bob draft");
		this.bobArchived = published(this.bobToken, "Bob archived");
		perform(delete(LISTINGS + "/" + this.bobArchived), this.bobToken).andExpect(status().isNoContent());
		this.bobSuspended = published(this.bobToken, "Bob suspended");
		perform(post(ADMIN + "/listings/" + this.bobSuspended + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());
		this.charliePublished = published(this.charlieToken, "Charlie published");

		this.connection = idOf(perform(post(LISTINGS + "/" + this.bobPublished + "/interest"), this.charlieToken)
			.andExpect(status().isCreated()));
		perform(post("/api/v1/connections/" + this.connection + "/accept"), this.bobToken).andExpect(status().isOk());
		this.conversation = this.jdbc.queryForObject("SELECT id FROM conversations WHERE connection_id = ?", Long.class,
				this.connection);
		this.bobMessage = sendMessage(this.bobToken, "Hello from Bob");
		this.charlieMessage = sendMessage(this.charlieToken, "Hello from Charlie");
	}

	@AfterEach
	void cleanDatabase() {
		for (String table : new String[] { "moderation_actions", "reports", "messages", "conversations", "connections", "saved_listings",
				"listings", "users" }) {
			this.jdbc.update("DELETE FROM " + table);
		}
	}

	// ---- Report creation -----------------------------------------------------------------

	@Test
	void userReportsAPublishedListingAsThemselves() throws Exception {
		String response = report(this.charlieToken, "LISTING", this.bobPublished, "SCAM_OR_FRAUD",
				"The listing appears to make false claims.")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.targetType").value("LISTING"))
			.andExpect(jsonPath("$.targetId").value(this.bobPublished))
			.andExpect(jsonPath("$.reason").value("SCAM_OR_FRAUD"))
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.createdAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(JSON.readTree(response).propertyNames()).containsExactlyInAnyOrder("id", "targetType", "targetId",
				"reason", "status", "createdAt");
		assertNoPrivateData(response);
		assertThat(this.jdbc.queryForMap("SELECT reporter_id, target_type, target_id, reason, details, status, "
				+ "reviewed_by, reviewed_at, resolution_note FROM reports"))
			.containsEntry("reporter_id", this.charlie.getId())
			.containsEntry("target_type", "LISTING")
			.containsEntry("target_id", this.bobPublished)
			.containsEntry("reason", "SCAM_OR_FRAUD")
			.containsEntry("details", "The listing appears to make false claims.")
			.containsEntry("status", "OPEN")
			.containsEntry("reviewed_by", null)
			.containsEntry("reviewed_at", null)
			.containsEntry("resolution_note", null);
	}

	@Test
	void usersCanReportActiveUsersAndMessagesInTheirOwnConversations() throws Exception {
		report(this.charlieToken, "USER", this.bob.getId(), "HARASSMENT", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.targetType").value("USER"));
		report(this.charlieToken, "MESSAGE", this.bobMessage, "HARASSMENT", "Rude.").andExpect(status().isCreated())
			.andExpect(jsonPath("$.targetType").value("MESSAGE"));
		// The listing owner is a participant too.
		report(this.bobToken, "MESSAGE", this.charlieMessage, "SPAM", null).andExpect(status().isCreated());
		assertThat(reportCount()).isEqualTo(3);
	}

	@Test
	void clientsCannotChooseReporterStatusOrReview() throws Exception {
		Map<String, Object> body = reportBody("LISTING", this.bobPublished, "SPAM", "details");
		body.put("reporterId", this.bob.getId());
		body.put("reporter", Map.of("id", this.alice.getId()));
		body.put("status", "RESOLVED");
		body.put("reviewedBy", this.alice.getId());
		body.put("reviewedAt", "2020-01-01T00:00:00Z");
		body.put("resolutionNote", "pre-approved");
		perform(post(REPORTS).param("reporterId", this.bob.getId().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body)), this.charlieToken)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("OPEN"));

		assertThat(this.jdbc.queryForMap("SELECT reporter_id, status, reviewed_by, reviewed_at, resolution_note FROM reports"))
			.containsEntry("reporter_id", this.charlie.getId())
			.containsEntry("status", "OPEN")
			.containsEntry("reviewed_by", null)
			.containsEntry("reviewed_at", null)
			.containsEntry("resolution_note", null);
	}

	@Test
	void invalidReportsAre400() throws Exception {
		for (String json : new String[] {
				"{\"targetType\":\"LISTINGX\",\"targetId\":1,\"reason\":\"SPAM\"}",
				"{\"targetType\":\"LISTING\",\"targetId\":1,\"reason\":\"ANNOYING\"}",
				"{\"targetType\":\"listing\",\"targetId\":1,\"reason\":\"SPAM\"}",
				"{ not json" }) {
			perform(post(REPORTS).contentType(MediaType.APPLICATION_JSON).content(json), this.charlieToken)
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		assertRejected(b -> b.remove("targetType"), "targetType");
		assertRejected(b -> b.remove("targetId"), "targetId");
		assertRejected(b -> b.put("targetId", 0), "targetId");
		assertRejected(b -> b.put("targetId", -5), "targetId");
		assertRejected(b -> b.remove("reason"), "reason");
		assertRejected(b -> b.put("details", "d".repeat(1_001)), "details");
		assertThat(reportCount()).isZero();

		// The limit applies after trimming.
		report(this.charlieToken, "LISTING", this.bobPublished, "OTHER", "  " + "d".repeat(1_000) + "  ")
			.andExpect(status().isCreated());
		assertThat(this.jdbc.queryForObject("SELECT details FROM reports", String.class)).isEqualTo("d".repeat(1_000));
	}

	@Test
	void blankDetailsAreStoredAsNull() throws Exception {
		report(this.charlieToken, "LISTING", this.bobPublished, "SPAM", "   \n  ").andExpect(status().isCreated());
		assertThat(this.jdbc.queryForObject("SELECT details FROM reports", String.class)).isNull();
	}

	@Test
	void targetsTheReporterCannotSeeAre404() throws Exception {
		for (Object[] target : new Object[][] { { "USER", 999_999_999L }, { "USER", this.dave.getId() },
				{ "LISTING", 999_999_999L }, { "LISTING", this.bobDraft }, { "LISTING", this.bobArchived },
				{ "LISTING", this.bobSuspended }, { "MESSAGE", 999_999_999L } }) {
			report(this.charlieToken, (String) target[0], (Long) target[1], "SPAM", null)
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		// A message outside your conversations looks exactly like a missing one.
		String missing = report(this.aliceToken, "MESSAGE", 999_999_999L, "SPAM", null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Message not found."))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String foreign = report(this.aliceToken, "MESSAGE", this.bobMessage, "SPAM", null)
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(foreign).isEqualTo(missing);
		assertThat(reportCount()).isZero();
	}

	@Test
	void youCannotReportYourselfYourListingOrYourMessage() throws Exception {
		report(this.charlieToken, "USER", this.charlie.getId(), "SPAM", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You cannot report yourself."));
		report(this.bobToken, "LISTING", this.bobPublished, "SPAM", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You cannot report your own listing."));
		report(this.bobToken, "MESSAGE", this.bobMessage, "SPAM", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You cannot report your own message."));
		assertThat(reportCount()).isZero();
	}

	@Test
	void aTargetCanBeReportedOnlyOncePerReporter() throws Exception {
		report(this.charlieToken, "LISTING", this.bobPublished, "SPAM", null).andExpect(status().isCreated());
		String duplicate = report(this.charlieToken, "LISTING", this.bobPublished, "SCAM_OR_FRAUD", "again")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You have already reported this."))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(duplicate).doesNotContain("uk_reports").doesNotContain("SQL");

		// Also after the first report has been reviewed.
		long id = this.jdbc.queryForObject("SELECT id FROM reports", Long.class);
		perform(post(ADMIN_REPORTS + "/" + id + "/dismiss"), this.aliceToken).andExpect(status().isOk());
		report(this.charlieToken, "LISTING", this.bobPublished, "OTHER", null).andExpect(status().isConflict());
		assertThat(reportCount()).isEqualTo(1);

		// Someone else may still report it.
		report(this.aliceToken, "LISTING", this.bobPublished, "SPAM", null).andExpect(status().isCreated());
	}

	@Test
	void reportingRequiresAnActiveAuthenticatedAccount() throws Exception {
		this.mockMvc.perform(post(REPORTS).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(reportBody("LISTING", this.bobPublished, "SPAM", null))))
			.andExpect(status().isUnauthorized());
		report(this.daveToken, "LISTING", this.bobPublished, "SPAM", null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("This account is suspended."));
		assertThat(reportCount()).isZero();
	}

	@Test
	void submittingAReportModeratesNothing() throws Exception {
		report(this.charlieToken, "LISTING", this.bobPublished, "SCAM_OR_FRAUD", null).andExpect(status().isCreated());
		report(this.charlieToken, "USER", this.bob.getId(), "SCAM_OR_FRAUD", null).andExpect(status().isCreated());
		report(this.aliceToken, "LISTING", this.bobPublished, "SPAM", null).andExpect(status().isCreated());

		assertThat(listingStatus(this.bobPublished)).isEqualTo("PUBLISHED");
		assertThat(userStatus(this.bob)).isEqualTo("ACTIVE");
		assertThat(publicSlugs()).contains(slugOf(this.bobPublished));
		this.mockMvc.perform(get("/api/v1/users/bob")).andExpect(status().isOk());
	}

	// ---- Admin access --------------------------------------------------------------------

	@Test
	void normalUsersGet403AndAnonymousUsers401OnEveryAdminEndpoint() throws Exception {
		long id = reportId(this.charlieToken, "LISTING", this.bobPublished);
		for (String token : new String[] { this.bobToken, this.charlieToken }) {
			for (MockHttpServletRequestBuilder request : adminRequests(id)) {
				perform(request, token).andExpect(status().isForbidden())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.detail").value(FORBIDDEN));
			}
		}
		for (MockHttpServletRequestBuilder request : adminRequests(id)) {
			this.mockMvc.perform(request).andExpect(status().isUnauthorized());
		}
		assertThat(reportStatus(id)).isEqualTo("OPEN");
		assertThat(userStatus(this.charlie)).isEqualTo("ACTIVE");
		assertThat(listingStatus(this.charliePublished)).isEqualTo("PUBLISHED");
		assertThat(listingStatus(this.bobSuspended)).isEqualTo("SUSPENDED");
	}

	@Test
	void adminTokenStopsWorkingOnceDemotedOrSuspended() throws Exception {
		perform(get(ADMIN_REPORTS), this.aliceToken).andExpect(status().isOk());

		this.jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", this.alice.getId());
		perform(get(ADMIN_REPORTS), this.aliceToken).andExpect(status().isForbidden());
		perform(post(ADMIN + "/users/" + this.bob.getId() + "/suspend"), this.aliceToken)
			.andExpect(status().isForbidden());

		this.jdbc.update("UPDATE users SET role = 'ADMIN', status = 'SUSPENDED' WHERE id = ?", this.alice.getId());
		perform(post(ADMIN + "/listings/" + this.charliePublished + "/suspend"), this.aliceToken)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("This account is suspended."));
		assertThat(userStatus(this.bob)).isEqualTo("ACTIVE");
		assertThat(listingStatus(this.charliePublished)).isEqualTo("PUBLISHED");
	}

	// ---- Report queue ----------------------------------------------------------------------

	@Test
	void queueDefaultsToOpenAndFiltersPaginatesAndOrders() throws Exception {
		long r1 = reportId(this.charlieToken, "LISTING", this.bobPublished);
		long r2 = reportId(this.charlieToken, "USER", this.bob.getId());
		long r3 = reportId(this.bobToken, "LISTING", this.charliePublished);
		long r4 = reportId(this.charlieToken, "MESSAGE", this.bobMessage);
		long resolved = reportId(this.bobToken, "USER", this.charlie.getId());
		long dismissed = reportId(this.bobToken, "MESSAGE", this.charlieMessage);
		Instant base = Instant.parse("2026-10-01T08:00:00Z");
		setCreatedAt(r1, base.plusSeconds(60));
		setCreatedAt(r2, base);
		setCreatedAt(r3, base.plusSeconds(60)); // same as r1: higher id first
		setCreatedAt(r4, base.plusSeconds(120));
		perform(post(ADMIN_REPORTS + "/" + resolved + "/resolve"), this.aliceToken).andExpect(status().isOk());
		perform(post(ADMIN_REPORTS + "/" + dismissed + "/dismiss"), this.aliceToken).andExpect(status().isOk());

		assertThat(ids(queue(""))).containsExactly(r4, r3, r1, r2);
		assertThat(ids(queue("status=OPEN"))).containsExactly(r4, r3, r1, r2);
		assertThat(ids(queue("status=RESOLVED"))).containsExactly(resolved);
		assertThat(ids(queue("status=DISMISSED"))).containsExactly(dismissed);

		String first = queue("page=0&size=3").andExpect(jsonPath("$.totalElements").value(4))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.size").value(3))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(ids(queue("page=0&size=3"))).containsExactly(r4, r3, r1);
		assertThat(ids(queue("page=1&size=3"))).containsExactly(r2);
		queue("").andExpect(jsonPath("$.size").value(20));

		// Summaries carry no details, notes, people or target content.
		assertThat(JSON.readTree(first).get("content").get(0).propertyNames()).containsExactlyInAnyOrder("id",
				"targetType", "targetId", "reason", "status", "createdAt", "reviewedAt");
		queue("status=RESOLVED").andExpect(jsonPath("$.content[0].reviewedAt").exists());

		for (String query : new String[] { "status=CLOSED", "status=open", "size=51", "size=0", "page=-1", "page=10001" }) {
			queue(query).andExpect(status().isBadRequest());
		}
	}

	@Test
	void emptyQueueIs200() throws Exception {
		queue("").andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	// ---- Report detail -----------------------------------------------------------------------

	@Test
	void adminSeesReporterAndTargetDetailsButNoEmailsOrPasswords() throws Exception {
		long listingReport = reportId(this.charlieToken, "LISTING", this.bobPublished);
		long userReport = reportId(this.charlieToken, "USER", this.bob.getId());
		long messageReport = reportId(this.charlieToken, "MESSAGE", this.bobMessage);

		String listing = perform(get(ADMIN_REPORTS + "/" + listingReport), this.aliceToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(listingReport))
			.andExpect(jsonPath("$.details").value("details"))
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.reporter.id").value(this.charlie.getId()))
			.andExpect(jsonPath("$.reporter.username").value("charlie"))
			.andExpect(jsonPath("$.reporter.displayName").value("Charlie C"))
			.andExpect(jsonPath("$.reviewer").value(nullValue()))
			.andExpect(jsonPath("$.target.id").value(this.bobPublished))
			.andExpect(jsonPath("$.target.title").value("Bob published"))
			.andExpect(jsonPath("$.target.shortPitch").value("A short pitch."))
			.andExpect(jsonPath("$.target.description").value("A full description."))
			.andExpect(jsonPath("$.target.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.target.owner.username").value("bob"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String user = perform(get(ADMIN_REPORTS + "/" + userReport), this.aliceToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.target.username").value("bob"))
			.andExpect(jsonPath("$.target.displayName").value("Bob B"))
			.andExpect(jsonPath("$.target.bio").value(nullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String message = perform(get(ADMIN_REPORTS + "/" + messageReport), this.aliceToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.target.id").value(this.bobMessage))
			.andExpect(jsonPath("$.target.content").value("Hello from Bob"))
			.andExpect(jsonPath("$.target.sender.username").value("bob"))
			.andExpect(jsonPath("$.target.conversationId").value(this.conversation))
			.andExpect(jsonPath("$.target.listing.id").value(this.bobPublished))
			.andExpect(jsonPath("$.target.createdAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		for (String response : List.of(listing, user, message)) {
			assertNoPrivateData(response);
		}

		// Admins still see content the public no longer can.
		perform(post(ADMIN + "/listings/" + this.bobPublished + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());
		perform(get(ADMIN_REPORTS + "/" + listingReport), this.aliceToken)
			.andExpect(jsonPath("$.target.status").value("SUSPENDED"));
		perform(get(ADMIN_REPORTS + "/999999999"), this.aliceToken).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Report not found."));
	}

	// ---- Resolution ------------------------------------------------------------------------

	@Test
	void adminResolvesAnOpenReportWithANote() throws Exception {
		long id = reportId(this.charlieToken, "LISTING", this.bobPublished);
		Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

		String response = perform(post(ADMIN_REPORTS + "/" + id + "/resolve").contentType(MediaType.APPLICATION_JSON)
			.content("{\"resolutionNote\":\"  Reviewed and found the report valid.  \",\"reviewedBy\":"
					+ this.bob.getId() + "}"), this.aliceToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RESOLVED"))
			.andExpect(jsonPath("$.resolutionNote").value("Reviewed and found the report valid."))
			.andExpect(jsonPath("$.reviewer.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.reviewer.username").value("alice"))
			.andExpect(jsonPath("$.target.id").value(this.bobPublished))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertNoPrivateData(response);

		Instant reviewedAt = Instant.parse(JsonPath.read(response, "$.reviewedAt"));
		assertThat(reviewedAt).isBetween(before, Instant.now());
		assertThat(this.jdbc.queryForMap("SELECT status, reviewed_by, reviewed_at, resolution_note FROM reports WHERE id = ?",
				id))
			.containsEntry("status", "RESOLVED")
			.containsEntry("reviewed_by", this.alice.getId())
			.containsEntry("resolution_note", "Reviewed and found the report valid.");
		assertThat(this.jdbc.queryForObject("SELECT reviewed_at FROM reports WHERE id = ?", LocalDateTime.class, id))
			.isEqualTo(LocalDateTime.ofInstant(reviewedAt, ZoneOffset.UTC));
		// Resolving does not moderate anything by itself.
		assertThat(listingStatus(this.bobPublished)).isEqualTo("PUBLISHED");
	}

	@Test
	void adminDismissesAnOpenReportWithOrWithoutANote() throws Exception {
		long withoutBody = reportId(this.charlieToken, "LISTING", this.bobPublished);
		long blankNote = reportId(this.charlieToken, "USER", this.bob.getId());

		perform(post(ADMIN_REPORTS + "/" + withoutBody + "/dismiss"), this.aliceToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISMISSED"))
			.andExpect(jsonPath("$.resolutionNote").value(nullValue()))
			.andExpect(jsonPath("$.reviewer.username").value("alice"));
		perform(post(ADMIN_REPORTS + "/" + blankNote + "/dismiss").contentType(MediaType.APPLICATION_JSON)
			.content("{\"resolutionNote\":\"   \"}"), this.aliceToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resolutionNote").value(nullValue()));
		assertThat(reportStatus(withoutBody)).isEqualTo("DISMISSED");
	}

	@Test
	void reviewedReportsAreFinalAndBadInputIsRejected() throws Exception {
		long resolved = reportId(this.charlieToken, "LISTING", this.bobPublished);
		long dismissed = reportId(this.charlieToken, "USER", this.bob.getId());
		long open = reportId(this.charlieToken, "MESSAGE", this.bobMessage);
		perform(post(ADMIN_REPORTS + "/" + resolved + "/resolve"), this.aliceToken).andExpect(status().isOk());
		perform(post(ADMIN_REPORTS + "/" + dismissed + "/dismiss"), this.aliceToken).andExpect(status().isOk());

		for (Map.Entry<String, String> action : Map.of("resolve", "resolved", "dismiss", "dismissed").entrySet()) {
			perform(post(ADMIN_REPORTS + "/" + resolved + "/" + action.getKey()), this.aliceToken)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail")
					.value("A report with status RESOLVED cannot be " + action.getValue() + "."));
			perform(post(ADMIN_REPORTS + "/" + dismissed + "/" + action.getKey()), this.aliceToken)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail")
					.value("A report with status DISMISSED cannot be " + action.getValue() + "."));
			perform(post(ADMIN_REPORTS + "/999999999/" + action.getKey()), this.aliceToken)
				.andExpect(status().isNotFound());
		}
		perform(post(ADMIN_REPORTS + "/" + open + "/resolve").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("resolutionNote", "n".repeat(1_001)))), this.aliceToken)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("resolutionNote"));
		assertThat(reportStatus(resolved)).isEqualTo("RESOLVED");
		assertThat(reportStatus(dismissed)).isEqualTo("DISMISSED");
		assertThat(reportStatus(open)).isEqualTo("OPEN");
	}

	// ---- User moderation -----------------------------------------------------------------

	@Test
	void suspendingAUserHidesThemPubliclyAndRestoringBringsEverythingBack() throws Exception {
		String bobSlug = slugOf(this.bobPublished);
		assertThat(publicSlugs()).contains(bobSlug);

		perform(post(ADMIN + "/users/" + this.bob.getId() + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
		perform(post(ADMIN + "/users/" + this.bob.getId() + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());
		assertThat(userStatus(this.bob)).isEqualTo("SUSPENDED");

		// Hidden publicly, without touching his listings.
		this.mockMvc.perform(get("/api/v1/users/bob")).andExpect(status().isNotFound());
		assertThat(publicSlugs()).doesNotContain(bobSlug).contains(slugOf(this.charliePublished));
		this.mockMvc.perform(get(LISTINGS + "/" + bobSlug)).andExpect(status().isNotFound());
		this.mockMvc.perform(get(LISTINGS).param("search", "Bob")).andExpect(jsonPath("$.totalElements").value(0));
		assertThat(listingStatus(this.bobPublished)).isEqualTo("PUBLISHED");
		// Nobody can act on his hidden listing either.
		perform(post(LISTINGS + "/" + this.bobPublished + "/save"), this.charlieToken).andExpect(status().isNotFound());
		report(this.charlieToken, "LISTING", this.bobPublished, "SPAM", null).andExpect(status().isNotFound());
		report(this.charlieToken, "USER", this.bob.getId(), "SPAM", null).andExpect(status().isNotFound());

		// Private history stays, and stays readable for the other participant.
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM connections", Long.class)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Long.class)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class)).isEqualTo(2);
		perform(get("/api/v1/connections/" + this.connection), this.charlieToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"));
		perform(get("/api/v1/conversations/" + this.conversation + "/messages"), this.charlieToken)
			.andExpect(jsonPath("$.totalElements").value(2));
		// Bob can read but not write.
		perform(get("/api/v1/conversations/" + this.conversation + "/messages"), this.bobToken)
			.andExpect(status().isOk());
		perform(post("/api/v1/conversations/" + this.conversation + "/messages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"still here?\"}"), this.bobToken)
			.andExpect(status().isForbidden());
		perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(listingBody("New"))),
				this.bobToken)
			.andExpect(status().isForbidden());

		perform(post(ADMIN + "/users/" + this.bob.getId() + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());
		perform(post(ADMIN + "/users/" + this.bob.getId() + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());
		assertThat(userStatus(this.bob)).isEqualTo("ACTIVE");
		this.mockMvc.perform(get("/api/v1/users/bob")).andExpect(status().isOk());
		assertThat(publicSlugs()).contains(bobSlug);
		this.mockMvc.perform(get(LISTINGS + "/" + bobSlug)).andExpect(status().isOk());
		// Moderator-suspended, draft and archived listings stay hidden: only eligible listings come back.
		assertThat(publicSlugs()).doesNotContain(slugOf(this.bobSuspended), slugOf(this.bobDraft), slugOf(this.bobArchived));
	}

	@Test
	void userModerationEdgeCases() throws Exception {
		perform(post(ADMIN + "/users/" + this.alice.getId() + "/suspend"), this.aliceToken)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You cannot suspend your own account."));
		assertThat(userStatus(this.alice)).isEqualTo("ACTIVE");
		for (String action : new String[] { "suspend", "restore" }) {
			perform(post(ADMIN + "/users/999999999/" + action), this.aliceToken).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("User not found."));
		}
		// Restoring an active user is a no-op.
		perform(post(ADMIN + "/users/" + this.charlie.getId() + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());
		assertThat(userStatus(this.charlie)).isEqualTo("ACTIVE");
		perform(post(ADMIN + "/users/" + this.dave.getId() + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());
		assertThat(userStatus(this.dave)).isEqualTo("ACTIVE");
	}

	// ---- Listing moderation --------------------------------------------------------------

	@Test
	void suspendingAListingHidesItAndRestoringRepublishesIt() throws Exception {
		String slug = slugOf(this.charliePublished);
		LocalDateTime publishedAt = this.jdbc.queryForObject("SELECT published_at FROM listings WHERE id = ?",
				LocalDateTime.class, this.charliePublished);

		perform(post(ADMIN + "/listings/" + this.charliePublished + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());
		perform(post(ADMIN + "/listings/" + this.charliePublished + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());
		assertThat(listingStatus(this.charliePublished)).isEqualTo("SUSPENDED");
		assertThat(publicSlugs()).doesNotContain(slug);
		this.mockMvc.perform(get(LISTINGS + "/" + slug)).andExpect(status().isNotFound());
		// The owner cannot undo it.
		perform(post(LISTINGS + "/" + this.charliePublished + "/publish"), this.charlieToken)
			.andExpect(status().isConflict());

		perform(post(ADMIN + "/listings/" + this.charliePublished + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());
		perform(post(ADMIN + "/listings/" + this.charliePublished + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());
		assertThat(listingStatus(this.charliePublished)).isEqualTo("PUBLISHED");
		assertThat(publicSlugs()).contains(slug);
		this.mockMvc.perform(get(LISTINGS + "/" + slug)).andExpect(status().isOk());
		assertThat(this.jdbc.queryForObject("SELECT published_at FROM listings WHERE id = ?", LocalDateTime.class,
				this.charliePublished))
			.isEqualTo(publishedAt);
	}

	@Test
	void draftAndArchivedListingsCannotBeModeratedAndMissingOnesAre404() throws Exception {
		for (long listing : new long[] { this.bobDraft, this.bobArchived }) {
			String status = listingStatus(listing);
			perform(post(ADMIN + "/listings/" + listing + "/suspend"), this.aliceToken).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("A listing with status " + status + " cannot be suspended."));
			perform(post(ADMIN + "/listings/" + listing + "/restore"), this.aliceToken).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("A listing with status " + status + " cannot be restored."));
			assertThat(listingStatus(listing)).isEqualTo(status);
		}
		for (String action : new String[] { "suspend", "restore" }) {
			perform(post(ADMIN + "/listings/999999999/" + action), this.aliceToken).andExpect(status().isNotFound());
		}
	}

	// ---- Saved listings --------------------------------------------------------------------

	@Test
	void savesOfASuspendedOwnersListingAreHiddenButKept() throws Exception {
		perform(post(LISTINGS + "/" + this.bobPublished + "/save"), this.charlieToken).andExpect(status().isNoContent());
		assertThat(savedIds(this.charlieToken)).containsExactly(this.bobPublished);

		perform(post(ADMIN + "/users/" + this.bob.getId() + "/suspend"), this.aliceToken)
			.andExpect(status().isNoContent());
		assertThat(savedIds(this.charlieToken)).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM saved_listings", Long.class)).isEqualTo(1);

		perform(post(ADMIN + "/users/" + this.bob.getId() + "/restore"), this.aliceToken)
			.andExpect(status().isNoContent());
		assertThat(savedIds(this.charlieToken)).containsExactly(this.bobPublished);
	}

	// ---- Regression ------------------------------------------------------------------------

	@Test
	void selfPublishingAndTheRestOfTheProductStillWork() throws Exception {
		String register = JSON.writeValueAsString(Map.of("email", "erin@example.com", "username", "erin", "password",
				"correct-horse-battery", "displayName", "Erin"));
		this.mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("USER"));
		String erin = JsonPath.read(this.mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(Map.of("identifier", "erin", "password", "correct-horse-battery"))))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.accessToken");

		// Publishing is immediate: no approval, no report needed.
		long listing = published(erin, "Erin's idea");
		assertThat(publicSlugs()).contains(slugOf(listing));
		perform(put(LISTINGS + "/" + listing).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(listingBody("Erin's idea, edited"))), erin)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		// Connections, conversations, profiles still work.
		long connection = idOf(perform(post(LISTINGS + "/" + listing + "/interest"), this.charlieToken)
			.andExpect(status().isCreated()));
		perform(post("/api/v1/connections/" + connection + "/accept"), erin).andExpect(status().isOk());
		perform(get("/api/v1/conversations"), erin).andExpect(jsonPath("$.totalElements").value(1));
		perform(get("/api/v1/profile"), erin).andExpect(jsonPath("$.username").value("erin"));
		this.mockMvc.perform(get("/api/v1/users/erin")).andExpect(status().isOk());
		// A normal user still cannot reach admin routes.
		perform(get(ADMIN_REPORTS), erin).andExpect(status().isForbidden());
	}

	// ---- Helpers ------------------------------------------------------------------------

	private List<MockHttpServletRequestBuilder> adminRequests(long reportId) {
		return List.of(get(ADMIN_REPORTS), get(ADMIN_REPORTS + "/" + reportId),
				post(ADMIN_REPORTS + "/" + reportId + "/resolve"), post(ADMIN_REPORTS + "/" + reportId + "/dismiss"),
				post(ADMIN + "/users/" + this.charlie.getId() + "/suspend"),
				post(ADMIN + "/users/" + this.dave.getId() + "/restore"),
				post(ADMIN + "/listings/" + this.charliePublished + "/suspend"),
				post(ADMIN + "/listings/" + this.bobSuspended + "/restore"));
	}

	private ResultActions report(String token, String type, long targetId, String reason, String details)
			throws Exception {
		return perform(post(REPORTS).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(reportBody(type, targetId, reason, details))), token);
	}

	private long reportId(String token, String type, long targetId) throws Exception {
		return idOf(report(token, type, targetId, "SPAM", "details").andExpect(status().isCreated()));
	}

	private static Map<String, Object> reportBody(String type, long targetId, String reason, String details) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("targetType", type);
		body.put("targetId", targetId);
		body.put("reason", reason);
		body.put("details", details);
		return body;
	}

	private void assertRejected(java.util.function.Consumer<Map<String, Object>> change, String field) throws Exception {
		Map<String, Object> body = reportBody("LISTING", this.bobPublished, "SPAM", null);
		change.accept(body);
		perform(post(REPORTS).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)),
				this.charlieToken)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").exists());
	}

	private ResultActions queue(String query) throws Exception {
		MockHttpServletRequestBuilder request = get(ADMIN_REPORTS);
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

	private List<String> publicSlugs() throws Exception {
		String response = this.mockMvc.perform(get(LISTINGS).param("size", "50"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.content[*].slug");
	}

	private List<Long> savedIds(String token) throws Exception {
		return ids(perform(get("/api/v1/saved-listings"), token));
	}

	private long sendMessage(String token, String content) throws Exception {
		return idOf(perform(post("/api/v1/conversations/" + this.conversation + "/messages")
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("content", content))), token).andExpect(status().isCreated()));
	}

	private void assertNoPrivateData(String response) {
		assertThat(response).doesNotContain("@example.com")
			.doesNotContain("email")
			.doesNotContain("passwordHash")
			.doesNotContain("unused-hash")
			.doesNotContain("eyJ");
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
		return this.mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String token(User user) {
		return this.tokenService.issueAccessToken(user).accessToken();
	}

	private long reportCount() {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM reports", Long.class);
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

	private void setCreatedAt(long reportId, Instant at) {
		this.jdbc.update("UPDATE reports SET created_at = ? WHERE id = ?", LocalDateTime.ofInstant(at, ZoneOffset.UTC),
				reportId);
	}

	private long published(String token, String title) throws Exception {
		long id = create(token, title);
		perform(post(LISTINGS + "/" + id + "/publish"), token).andExpect(status().isOk());
		return id;
	}

	private long create(String token, String title) throws Exception {
		return idOf(perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(listingBody(title))), token).andExpect(status().isCreated()));
	}

	private static Map<String, Object> listingBody(String title) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("shortPitch", "A short pitch.");
		body.put("description", "A full description.");
		body.put("assetType", "PROJECT");
		body.put("marketplaceMode", "COLLABORATE");
		body.put("category", "SAAS");
		body.put("stage", "PROTOTYPE");
		return body;
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
