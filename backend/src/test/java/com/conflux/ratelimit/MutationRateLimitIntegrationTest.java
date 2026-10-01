package com.conflux.ratelimit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "conflux.rate-limit.listing-update.max-requests=2",
		"conflux.rate-limit.listing-update.window=10m", "conflux.rate-limit.listing-archive.max-requests=2",
		"conflux.rate-limit.listing-archive.window=10m", "conflux.rate-limit.listing-unsave.max-requests=2",
		"conflux.rate-limit.listing-unsave.window=10m", "conflux.rate-limit.connection-decision.max-requests=2",
		"conflux.rate-limit.connection-decision.window=10m", "conflux.rate-limit.connection-withdraw.max-requests=2",
		"conflux.rate-limit.connection-withdraw.window=10m", "conflux.rate-limit.profile-update.max-requests=2",
		"conflux.rate-limit.profile-update.window=10m", "conflux.rate-limit.admin-moderation.max-requests=2",
		"conflux.rate-limit.admin-moderation.window=10m" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MutationRateLimitIntegrationTest {

	private static final String LISTINGS = "/api/v1/listings";

	private static final String CONNECTIONS = "/api/v1/connections";

	private static final String PROFILE = "/api/v1/profile";

	private static final String REPORTS = "/api/v1/reports";

	private static final String ADMIN = "/api/v1/admin";

	private static final String TOO_MANY_REQUESTS = "Too many requests. Please try again later.";

	private static final String SUSPENDED = "This account is suspended.";

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

	private String erinToken;

	private String bobToken;

	private String charlieToken;

	private String daveToken;

	@BeforeEach
	void setUp() {
		cleanDatabase();
		this.alice = this.userRepository.save(admin(new User("alice@example.com", "unused-hash", "alice", "Alice A")));
		User erin = this.userRepository.save(admin(new User("erin@example.com", "unused-hash", "erin", "Erin E")));
		this.bob = this.userRepository.save(new User("bob@example.com", "unused-hash", "bob", "Bob B"));
		this.charlie = this.userRepository.save(new User("charlie@example.com", "unused-hash", "charlie", "Charlie C"));
		this.dave = this.userRepository.save(new User("dave@example.com", "unused-hash", "dave", "Dave D"));
		this.aliceToken = token(this.alice);
		this.erinToken = token(erin);
		this.bobToken = token(this.bob);
		this.charlieToken = token(this.charlie);
		this.daveToken = token(this.dave);
	}

	@AfterEach
	void cleanDatabase() {
		for (String table : new String[] { "moderation_actions", "reports", "messages", "conversations", "connections",
				"saved_listings", "listings", "users" }) {
			this.jdbc.update("DELETE FROM " + table);
		}
	}

	@Test
	void listingUpdatesAreLimitedPerUserAndARejectedUpdateChangesNothing() throws Exception {
		long listing = createListing(this.bobToken, "Original");
		long charliesListing = createListing(this.charlieToken, "Charlie's");
		update(this.bobToken, listing, "First edit").andExpect(status().isOk());
		update(this.bobToken, listing, "Second edit").andExpect(status().isOk());

		assertTooManyRequests(update(this.bobToken, listing, "Third edit"));
		assertThat(title(listing)).isEqualTo("Second edit");

		update(this.charlieToken, charliesListing, "Charlie's edit").andExpect(status().isOk());
	}

	@Test
	void listingUpdateFailuresKeepTheirStatusAndDoNotUseTheAllowance() throws Exception {
		long listing = createListing(this.bobToken, "Bob's");
		long archived = createListing(this.bobToken, "Bob's archived");
		setListingStatus(archived, "ARCHIVED");
		long charliesListing = createListing(this.charlieToken, "Charlie's");

		for (int i = 0; i < 3; i++) {
			assertListingUpdateFailures(listing, archived, charliesListing);
		}
		update(this.bobToken, listing, "First edit").andExpect(status().isOk());
		update(this.bobToken, listing, "Second edit").andExpect(status().isOk());
		assertTooManyRequests(update(this.bobToken, listing, "Third edit"));

		assertListingUpdateFailures(listing, archived, charliesListing);
	}

	private void assertListingUpdateFailures(long listing, long archived, long charliesListing) throws Exception {
		perform(put(LISTINGS + "/" + listing).contentType(MediaType.APPLICATION_JSON).content("{}"), this.bobToken)
			.andExpect(status().isBadRequest());
		update(this.bobToken, archived, "Edit").andExpect(status().isConflict());
		update(this.bobToken, charliesListing, "Not mine").andExpect(status().isNotFound());
		this.mockMvc
			.perform(put(LISTINGS + "/" + listing).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(listingBody("Anonymous"))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void archivingIsLimitedPerUserWhileFailuresKeepTheirStatus() throws Exception {
		long first = createListing(this.bobToken, "First");
		long second = createListing(this.bobToken, "Second");
		long third = createListing(this.bobToken, "Third");
		long suspended = publishedListing(this.bobToken, "Suspended");
		setListingStatus(suspended, "SUSPENDED");
		long charliesListing = createListing(this.charlieToken, "Charlie's");
		for (int i = 0; i < 3; i++) {
			archive(this.bobToken, suspended).andExpect(status().isConflict());
			archive(this.bobToken, charliesListing).andExpect(status().isNotFound());
		}

		archive(this.bobToken, first).andExpect(status().isNoContent());
		archive(this.bobToken, second).andExpect(status().isNoContent());
		assertTooManyRequests(archive(this.bobToken, third));
		assertThat(listingStatus(third)).isEqualTo("DRAFT");

		archive(this.bobToken, suspended).andExpect(status().isConflict());
		archive(this.bobToken, charliesListing).andExpect(status().isNotFound());
		archive(this.charlieToken, charliesListing).andExpect(status().isNoContent());
	}

	@Test
	void archivingAnArchivedListingIsANoOpThatDoesNotUseTheAllowance() throws Exception {
		long first = createListing(this.bobToken, "First");
		long second = createListing(this.bobToken, "Second");
		long third = createListing(this.bobToken, "Third");
		archive(this.bobToken, first).andExpect(status().isNoContent());
		for (int i = 0; i < 3; i++) {
			archive(this.bobToken, first).andExpect(status().isNoContent());
		}
		archive(this.bobToken, second).andExpect(status().isNoContent());

		assertTooManyRequests(archive(this.bobToken, third));
		assertThat(listingStatus(third)).isEqualTo("DRAFT");
		archive(this.bobToken, first).andExpect(status().isNoContent());
	}

	@Test
	void unsavingIsLimitedPerUserSeparateFromSavingAndRepeatedUnsavesDoNotCount() throws Exception {
		long listing = publishedListing(this.charlieToken, "Charlie's");
		long other = publishedListing(this.charlieToken, "Charlie's other");
		save(this.bobToken, listing).andExpect(status().isNoContent());
		save(this.bobToken, other).andExpect(status().isNoContent());
		unsave(this.bobToken, listing).andExpect(status().isNoContent());
		for (int i = 0; i < 3; i++) {
			unsave(this.bobToken, listing).andExpect(status().isNoContent());
		}
		save(this.bobToken, listing).andExpect(status().isNoContent());
		unsave(this.bobToken, other).andExpect(status().isNoContent());

		assertTooManyRequests(unsave(this.bobToken, listing));
		assertThat(savedCount(this.bob)).isEqualTo(1);
		unsave(this.bobToken, other).andExpect(status().isNoContent());

		save(this.daveToken, listing).andExpect(status().isNoContent());
		unsave(this.daveToken, listing).andExpect(status().isNoContent());
	}

	@Test
	void acceptAndRejectShareOneAllowancePerOwner() throws Exception {
		long listing = publishedListing(this.aliceToken, "Alice's");
		long fromBob = interest(this.bobToken, listing);
		long fromCharlie = interest(this.charlieToken, listing);
		long fromDave = interest(this.daveToken, listing);
		accept(this.aliceToken, fromBob).andExpect(status().isOk());
		reject(this.aliceToken, fromCharlie).andExpect(status().isOk());

		assertTooManyRequests(accept(this.aliceToken, fromDave));
		assertTooManyRequests(reject(this.aliceToken, fromDave));
		assertThat(connectionStatus(fromDave)).isEqualTo("PENDING");
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE connection_id = ?", Long.class,
				fromDave))
			.isZero();

		long bobsListing = publishedListing(this.bobToken, "Bob's");
		accept(this.bobToken, interest(this.charlieToken, bobsListing)).andExpect(status().isOk());
	}

	@Test
	void connectionDecisionFailuresKeepTheirStatusAndDoNotUseTheAllowance() throws Exception {
		long listing = publishedListing(this.aliceToken, "Alice's");
		long fromBob = interest(this.bobToken, listing);
		long fromCharlie = interest(this.charlieToken, listing);
		long fromDave = interest(this.daveToken, listing);
		for (int i = 0; i < 3; i++) {
			assertConnectionDecisionFailures(fromBob);
		}
		accept(this.aliceToken, fromBob).andExpect(status().isOk());
		for (int i = 0; i < 3; i++) {
			accept(this.aliceToken, fromBob).andExpect(status().isConflict());
		}
		reject(this.aliceToken, fromCharlie).andExpect(status().isOk());

		assertTooManyRequests(accept(this.aliceToken, fromDave));
		accept(this.aliceToken, fromBob).andExpect(status().isConflict());
		assertConnectionDecisionFailures(fromDave);
	}

	private void assertConnectionDecisionFailures(long connection) throws Exception {
		String requesterToken = this.jdbc.queryForObject("SELECT requester_id FROM connections WHERE id = ?",
				Long.class, connection)
			.equals(this.bob.getId()) ? this.bobToken : this.daveToken;
		accept(requesterToken, connection).andExpect(status().isForbidden());
		reject(requesterToken, connection).andExpect(status().isForbidden());
		accept(this.erinToken, connection).andExpect(status().isNotFound());
		this.mockMvc.perform(post(CONNECTIONS + "/" + connection + "/accept")).andExpect(status().isUnauthorized());
	}

	@Test
	void withdrawingIsLimitedPerUserWhileFailuresKeepTheirStatus() throws Exception {
		long first = interest(this.bobToken, publishedListing(this.aliceToken, "Alice's first"));
		long second = interest(this.bobToken, publishedListing(this.aliceToken, "Alice's second"));
		long third = interest(this.bobToken, publishedListing(this.charlieToken, "Charlie's"));
		long charlies = interest(this.charlieToken, publishedListing(this.daveToken, "Dave's"));
		for (int i = 0; i < 3; i++) {
			assertWithdrawFailures(first, charlies);
		}

		withdraw(this.bobToken, first).andExpect(status().isNoContent());
		withdraw(this.bobToken, second).andExpect(status().isNoContent());
		assertTooManyRequests(withdraw(this.bobToken, third));
		assertThat(connectionStatus(third)).isEqualTo("PENDING");

		withdraw(this.bobToken, first).andExpect(status().isConflict());
		assertWithdrawFailures(third, charlies);
		withdraw(this.charlieToken, charlies).andExpect(status().isNoContent());
	}

	private void assertWithdrawFailures(long bobsConnection, long charliesConnection) throws Exception {
		String ownerToken = this.jdbc.queryForObject(
				"SELECT l.owner_id FROM connections c JOIN listings l ON l.id = c.listing_id WHERE c.id = ?",
				Long.class, bobsConnection)
			.equals(this.alice.getId()) ? this.aliceToken : this.charlieToken;
		withdraw(ownerToken, bobsConnection).andExpect(status().isForbidden());
		withdraw(this.bobToken, charliesConnection).andExpect(status().isNotFound());
		this.mockMvc.perform(delete(CONNECTIONS + "/" + bobsConnection)).andExpect(status().isUnauthorized());
	}

	@Test
	void profileUpdatesAreLimitedPerUserWhileValidationAndAuthenticationFailuresKeepTheirStatus()
			throws Exception {
		for (int i = 0; i < 3; i++) {
			updateProfile(this.bobToken, "   ").andExpect(status().isBadRequest());
			this.mockMvc
				.perform(put(PROFILE).contentType(MediaType.APPLICATION_JSON)
					.content(JSON.writeValueAsString(Map.of("displayName", "Anonymous"))))
				.andExpect(status().isUnauthorized());
		}
		updateProfile(this.bobToken, "Bob One").andExpect(status().isOk());
		updateProfile(this.bobToken, "Bob Two").andExpect(status().isOk());

		assertTooManyRequests(updateProfile(this.bobToken, "Bob Three"));
		assertThat(displayName(this.bob)).isEqualTo("Bob Two");
		updateProfile(this.bobToken, "   ").andExpect(status().isBadRequest());

		updateProfile(this.charlieToken, "Charlie One").andExpect(status().isOk());
	}

	@Test
	void allAdminModerationActionsShareOneAllowancePerAdministrator() throws Exception {
		long listing = publishedListing(this.charlieToken, "Charlie's");
		long firstReport = report(this.bobToken, "USER", this.charlie.getId());
		long secondReport = report(this.daveToken, "USER", this.charlie.getId());
		suspendUser(this.aliceToken, this.bob.getId()).andExpect(status().isNoContent());
		restoreUser(this.aliceToken, this.bob.getId()).andExpect(status().isNoContent());
		long auditEntries = auditCount();

		assertTooManyRequests(suspendListing(this.aliceToken, listing));
		assertTooManyRequests(suspendUser(this.aliceToken, this.dave.getId()));
		assertTooManyRequests(resolve(this.aliceToken, firstReport));
		assertTooManyRequests(dismiss(this.aliceToken, secondReport));
		assertThat(listingStatus(listing)).isEqualTo("PUBLISHED");
		assertThat(userStatus(this.dave)).isEqualTo("ACTIVE");
		assertThat(reportStatus(firstReport)).isEqualTo("OPEN");
		assertThat(reportStatus(secondReport)).isEqualTo("OPEN");
		assertThat(auditCount()).isEqualTo(auditEntries);

		suspendListing(this.erinToken, listing).andExpect(status().isNoContent());
		resolve(this.erinToken, firstReport).andExpect(status().isOk());
		assertTooManyRequests(restoreListing(this.erinToken, listing));
		assertTooManyRequests(dismiss(this.erinToken, secondReport));
	}

	@Test
	void adminModerationFailuresAndNoOpsKeepTheirStatusAndDoNotUseTheAllowance() throws Exception {
		long draft = createListing(this.charlieToken, "Charlie's draft");
		long published = publishedListing(this.charlieToken, "Charlie's");
		long decided = report(this.bobToken, "USER", this.charlie.getId());
		resolve(this.erinToken, decided).andExpect(status().isOk());
		long open = report(this.daveToken, "USER", this.charlie.getId());
		for (int i = 0; i < 3; i++) {
			assertAdminFailures(draft, decided, open);
		}

		suspendUser(this.aliceToken, this.bob.getId()).andExpect(status().isNoContent());
		suspendListing(this.aliceToken, published).andExpect(status().isNoContent());
		assertTooManyRequests(suspendUser(this.aliceToken, this.dave.getId()));

		assertAdminFailures(draft, decided, open);
		suspendUser(this.aliceToken, this.bob.getId()).andExpect(status().isNoContent());
		suspendListing(this.aliceToken, published).andExpect(status().isNoContent());
	}

	private void assertAdminFailures(long draft, long decided, long open) throws Exception {
		suspendUser(this.aliceToken, this.alice.getId()).andExpect(status().isConflict());
		suspendUser(this.aliceToken, Long.MAX_VALUE).andExpect(status().isNotFound());
		suspendListing(this.aliceToken, draft).andExpect(status().isConflict());
		restoreListing(this.aliceToken, Long.MAX_VALUE).andExpect(status().isNotFound());
		resolve(this.aliceToken, decided).andExpect(status().isConflict());
		dismiss(this.aliceToken, Long.MAX_VALUE).andExpect(status().isNotFound());
		perform(post(ADMIN + "/reports/" + open + "/resolve").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("resolutionNote", "x".repeat(10_001)))), this.aliceToken)
			.andExpect(status().isBadRequest());
		restoreUser(this.aliceToken, this.charlie.getId()).andExpect(status().isNoContent());
		assertThat(reportStatus(open)).isEqualTo("OPEN");
	}

	@Test
	void adminAuthorizationIsUnchangedAndRefusedCallsDoNotUseTheAllowance() throws Exception {
		long listing = publishedListing(this.charlieToken, "Charlie's");
		long report = report(this.bobToken, "USER", this.charlie.getId());
		for (int i = 0; i < 3; i++) {
			assertAdminRefused(this.bobToken, report, listing, 403);
			assertAdminRefused(null, report, listing, 401);
		}
		this.jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", this.alice.getId());
		for (int i = 0; i < 3; i++) {
			assertAdminRefused(this.aliceToken, report, listing, 403);
		}
		this.jdbc.update("UPDATE users SET role = 'ADMIN', status = 'SUSPENDED' WHERE id = ?", this.alice.getId());
		for (int i = 0; i < 3; i++) {
			assertAdminRefused(this.aliceToken, report, listing, 403);
		}
		assertThat(auditCount()).isZero();

		this.jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", this.alice.getId());
		suspendListing(this.aliceToken, listing).andExpect(status().isNoContent());
		resolve(this.aliceToken, report).andExpect(status().isOk());
	}

	private void assertAdminRefused(String token, long report, long listing, int expectedStatus) throws Exception {
		for (MockHttpServletRequestBuilder request : List.of(post(ADMIN + "/users/" + this.dave.getId() + "/suspend"),
				post(ADMIN + "/users/" + this.dave.getId() + "/restore"),
				post(ADMIN + "/listings/" + listing + "/suspend"), post(ADMIN + "/listings/" + listing + "/restore"),
				post(ADMIN + "/reports/" + report + "/resolve"), post(ADMIN + "/reports/" + report + "/dismiss"))) {
			ResultActions result = (token != null) ? perform(request, token) : this.mockMvc.perform(request);
			result.andExpect(status().is(expectedStatus));
		}
		assertThat(listingStatus(listing)).isEqualTo("PUBLISHED");
		assertThat(reportStatus(report)).isEqualTo("OPEN");
	}

	@Test
	void suspendedUsersKeepGettingTheirNormal403() throws Exception {
		long bobsListing = publishedListing(this.bobToken, "Bob's");
		long incoming = interest(this.charlieToken, bobsListing);
		long outgoing = interest(this.bobToken, publishedListing(this.aliceToken, "Alice's"));
		save(this.bobToken, bobsListing).andExpect(status().isNoContent());
		this.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", this.bob.getId());

		for (int i = 0; i < 4; i++) {
			assertSuspended(update(this.bobToken, bobsListing, "Edit"));
			assertSuspended(archive(this.bobToken, bobsListing));
			assertSuspended(unsave(this.bobToken, bobsListing));
			assertSuspended(accept(this.bobToken, incoming));
			assertSuspended(reject(this.bobToken, incoming));
			assertSuspended(withdraw(this.bobToken, outgoing));
			assertSuspended(updateProfile(this.bobToken, "Bob"));
		}
	}

	@Test
	void suspendedAccountsGet403BeforeOwnershipOrStateIsChecked() throws Exception {
		long charliesListing = publishedListing(this.charlieToken, "Charlie's");
		long bobsArchived = createListing(this.bobToken, "Bob's archived");
		setListingStatus(bobsArchived, "ARCHIVED");
		this.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", this.bob.getId());

		for (long listing : new long[] { charliesListing, Long.MAX_VALUE, bobsArchived }) {
			assertSuspended(update(this.bobToken, listing, "Edit"));
			assertSuspended(publish(this.bobToken, listing));
			assertSuspended(archive(this.bobToken, listing));
		}
		assertThat(title(charliesListing)).isEqualTo("Charlie's");
		assertThat(listingStatus(charliesListing)).isEqualTo("PUBLISHED");
	}

	@Test
	void differentOperationsDoNotShareAnAllowance() throws Exception {
		long listing = publishedListing(this.aliceToken, "Alice's");
		long other = createListing(this.aliceToken, "Alice's other");
		long fromBob = interest(this.bobToken, listing);
		long fromCharlie = interest(this.charlieToken, listing);
		long aliceToCharlie = interest(this.aliceToken, publishedListing(this.charlieToken, "Charlie's"));
		long charliesListing = publishedListing(this.charlieToken, "Charlie's second");

		update(this.aliceToken, listing, "Edit 1").andExpect(status().isOk());
		update(this.aliceToken, listing, "Edit 2").andExpect(status().isOk());
		assertTooManyRequests(update(this.aliceToken, listing, "Edit 3"));
		accept(this.aliceToken, fromBob).andExpect(status().isOk());
		reject(this.aliceToken, fromCharlie).andExpect(status().isOk());
		assertTooManyRequests(accept(this.aliceToken, interest(this.daveToken, listing)));
		suspendUser(this.aliceToken, this.dave.getId()).andExpect(status().isNoContent());
		restoreUser(this.aliceToken, this.dave.getId()).andExpect(status().isNoContent());
		assertTooManyRequests(suspendListing(this.aliceToken, charliesListing));

		archive(this.aliceToken, other).andExpect(status().isNoContent());
		withdraw(this.aliceToken, aliceToCharlie).andExpect(status().isNoContent());
		save(this.aliceToken, charliesListing).andExpect(status().isNoContent());
		unsave(this.aliceToken, charliesListing).andExpect(status().isNoContent());
		updateProfile(this.aliceToken, "Alice Again").andExpect(status().isOk());
		publish(this.aliceToken, createListing(this.aliceToken, "Alice's third")).andExpect(status().isOk());
		report(this.aliceToken, "USER", this.bob.getId());
	}

	@Test
	void concurrentRequestsOfOneUserCannotExceedTheLimitAndUsersStayIsolated() throws Exception {
		int threadsPerUser = 6;
		ExecutorService executor = Executors.newFixedThreadPool(threadsPerUser * 2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> bobResults = new ArrayList<>();
		List<Future<Integer>> charlieResults = new ArrayList<>();
		try {
			for (int i = 0; i < threadsPerUser; i++) {
				String suffix = Integer.toString(i);
				bobResults.add(executor.submit(concurrentProfileUpdate(start, this.bobToken, "Bob " + suffix)));
				charlieResults
					.add(executor.submit(concurrentProfileUpdate(start, this.charlieToken, "Charlie " + suffix)));
			}
			start.countDown();
			for (List<Future<Integer>> results : List.of(bobResults, charlieResults)) {
				List<Integer> statuses = new ArrayList<>();
				for (Future<Integer> result : results) {
					statuses.add(result.get(60, TimeUnit.SECONDS));
				}
				assertThat(statuses).filteredOn(s -> s == 200).hasSize(2);
				assertThat(statuses).filteredOn(s -> s == 429).hasSize(threadsPerUser - 2);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentAdminActionsCannotExceedTheLimit() throws Exception {
		List<Long> targets = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			targets.add(this.userRepository.save(new User("target" + i + "@example.com", "unused-hash", "target_" + i,
					"Target " + i))
				.getId());
		}
		ExecutorService executor = Executors.newFixedThreadPool(targets.size());
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (Long target : targets) {
				Callable<Integer> call = () -> {
					start.await();
					return suspendUser(this.aliceToken, target).andReturn().getResponse().getStatus();
				};
				results.add(executor.submit(call));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(60, TimeUnit.SECONDS));
			}
			assertThat(statuses).filteredOn(s -> s == 204).hasSize(2);
			assertThat(statuses).filteredOn(s -> s == 429).hasSize(targets.size() - 2);
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE status = 'SUSPENDED'", Long.class))
			.isEqualTo(2);
		assertThat(auditCount()).isEqualTo(2);
	}

	private Callable<Integer> concurrentProfileUpdate(CountDownLatch start, String token, String displayName) {
		return () -> {
			start.await();
			return updateProfile(token, displayName).andReturn().getResponse().getStatus();
		};
	}

	private static User admin(User user) {
		user.setRole(UserRole.ADMIN);
		return user;
	}

	private String token(User user) {
		return this.tokenService.issueAccessToken(user).accessToken();
	}

	private static void assertTooManyRequests(ResultActions result) throws Exception {
		MvcResult response = result.andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.detail").value(TOO_MANY_REQUESTS))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
			.andReturn();
		assertThat(Long.parseLong(response.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1L, 600L);
	}

	private static void assertSuspended(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value(SUSPENDED));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
		return this.mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private long createListing(String token, String title) throws Exception {
		return idOf(perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(listingBody(title))), token).andExpect(status().isCreated()));
	}

	private ResultActions publish(String token, long listingId) throws Exception {
		return perform(post(LISTINGS + "/" + listingId + "/publish"), token);
	}

	private long publishedListing(String token, String title) throws Exception {
		long id = createListing(token, title);
		publish(token, id).andExpect(status().isOk());
		return id;
	}

	private ResultActions update(String token, long listingId, String title) throws Exception {
		return perform(put(LISTINGS + "/" + listingId).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(listingBody(title))), token);
	}

	private ResultActions archive(String token, long listingId) throws Exception {
		return perform(delete(LISTINGS + "/" + listingId), token);
	}

	private ResultActions save(String token, long listingId) throws Exception {
		return perform(post(LISTINGS + "/" + listingId + "/save"), token);
	}

	private ResultActions unsave(String token, long listingId) throws Exception {
		return perform(delete(LISTINGS + "/" + listingId + "/save"), token);
	}

	private long interest(String token, long listingId) throws Exception {
		return idOf(perform(post(LISTINGS + "/" + listingId + "/interest"), token).andExpect(status().isCreated()));
	}

	private ResultActions accept(String token, long connectionId) throws Exception {
		return perform(post(CONNECTIONS + "/" + connectionId + "/accept"), token);
	}

	private ResultActions reject(String token, long connectionId) throws Exception {
		return perform(post(CONNECTIONS + "/" + connectionId + "/reject"), token);
	}

	private ResultActions withdraw(String token, long connectionId) throws Exception {
		return perform(delete(CONNECTIONS + "/" + connectionId), token);
	}

	private ResultActions updateProfile(String token, String displayName) throws Exception {
		return perform(put(PROFILE).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("displayName", displayName))), token);
	}

	private long report(String token, String type, long targetId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("targetType", type);
		body.put("targetId", targetId);
		body.put("reason", "SPAM");
		body.put("details", "details");
		return idOf(perform(post(REPORTS).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)),
				token)
			.andExpect(status().isCreated()));
	}

	private ResultActions suspendUser(String token, long userId) throws Exception {
		return perform(post(ADMIN + "/users/" + userId + "/suspend"), token);
	}

	private ResultActions restoreUser(String token, long userId) throws Exception {
		return perform(post(ADMIN + "/users/" + userId + "/restore"), token);
	}

	private ResultActions suspendListing(String token, long listingId) throws Exception {
		return perform(post(ADMIN + "/listings/" + listingId + "/suspend"), token);
	}

	private ResultActions restoreListing(String token, long listingId) throws Exception {
		return perform(post(ADMIN + "/listings/" + listingId + "/restore"), token);
	}

	private ResultActions resolve(String token, long reportId) throws Exception {
		return perform(post(ADMIN + "/reports/" + reportId + "/resolve"), token);
	}

	private ResultActions dismiss(String token, long reportId) throws Exception {
		return perform(post(ADMIN + "/reports/" + reportId + "/dismiss"), token);
	}

	private void setListingStatus(long listingId, String status) {
		this.jdbc.update("UPDATE listings SET status = ? WHERE id = ?", status, listingId);
	}

	private String title(long listingId) {
		return this.jdbc.queryForObject("SELECT title FROM listings WHERE id = ?", String.class, listingId);
	}

	private String listingStatus(long listingId) {
		return this.jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, listingId);
	}

	private String connectionStatus(long connectionId) {
		return this.jdbc.queryForObject("SELECT status FROM connections WHERE id = ?", String.class, connectionId);
	}

	private String reportStatus(long reportId) {
		return this.jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, reportId);
	}

	private String userStatus(User user) {
		return this.jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, user.getId());
	}

	private String displayName(User user) {
		return this.jdbc.queryForObject("SELECT display_name FROM users WHERE id = ?", String.class, user.getId());
	}

	private long savedCount(User user) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM saved_listings WHERE user_id = ?", Long.class,
				user.getId());
	}

	private long auditCount() {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM moderation_actions", Long.class);
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
