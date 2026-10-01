package com.conflux.connection;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConnectionApiIntegrationTest {

	private static final String LISTINGS = "/api/v1/listings";

	private static final String CONNECTIONS = "/api/v1/connections";

	private static final String FORBIDDEN_SUSPENDED = "This account is suspended.";

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

	private String aliceToken;

	private String bobToken;

	private String charlieToken;

	private long aliceListing;

	private long aliceListing2;

	private long bobListing;

	private long draft;

	private long archived;

	private long suspended;

	@BeforeEach
	void setUp() throws Exception {
		cleanDatabase();
		this.alice = this.userRepository.save(new User("alice@example.com", "unused-hash", "alice", "Alice A"));
		this.bob = this.userRepository.save(new User("bob@example.com", "unused-hash", "bob", "Bob B"));
		this.charlie = this.userRepository.save(new User("charlie@example.com", "unused-hash", "charlie", "Charlie C"));
		this.aliceToken = this.tokenService.issueAccessToken(this.alice).accessToken();
		this.bobToken = this.tokenService.issueAccessToken(this.bob).accessToken();
		this.charlieToken = this.tokenService.issueAccessToken(this.charlie).accessToken();

		this.aliceListing = published(this.aliceToken, "Alice opportunity");
		this.aliceListing2 = published(this.aliceToken, "Alice second opportunity");
		this.bobListing = published(this.bobToken, "Bob opportunity");
		this.draft = create(this.aliceToken, "Alice draft");
		this.archived = published(this.aliceToken, "Alice archived");
		perform(delete(LISTINGS + "/" + this.archived), this.aliceToken).andExpect(status().isNoContent());
		this.suspended = published(this.aliceToken, "Alice suspended");
		this.jdbc.update("UPDATE listings SET status = 'SUSPENDED' WHERE id = ?", this.suspended);
	}

	@AfterEach
	void cleanDatabase() {
		cleanConnections();
		this.jdbc.update("DELETE FROM saved_listings");
		this.jdbc.update("DELETE FROM listings");
		this.jdbc.update("DELETE FROM users");
	}

	@Test
	void expressingInterestCreatesAPendingConnectionFromTheTokenUserToTheListingOwner() throws Exception {
		String response = interest(this.bobToken, this.aliceListing).andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.createdAt").exists())
			.andExpect(jsonPath("$.updatedAt").exists())
			.andExpect(jsonPath("$.requester.id").value(this.bob.getId()))
			.andExpect(jsonPath("$.requester.username").value("bob"))
			.andExpect(jsonPath("$.requester.displayName").value("Bob B"))
			.andExpect(jsonPath("$.owner.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.owner.username").value("alice"))
			.andExpect(jsonPath("$.owner.displayName").value("Alice A"))
			.andExpect(jsonPath("$.listing.id").value(this.aliceListing))
			.andExpect(jsonPath("$.listing.slug").exists())
			.andExpect(jsonPath("$.listing.title").value("Alice opportunity"))
			.andExpect(jsonPath("$.listing.shortPitch").value("A short pitch."))
			.andExpect(jsonPath("$.listing.status").value("PUBLISHED"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		long id = ((Number) JsonPath.read(response, "$.id")).longValue();
		assertThat(this.jdbc.queryForMap("SELECT requester_id, listing_id, status FROM connections WHERE id = ?", id))
			.containsEntry("requester_id", this.bob.getId())
			.containsEntry("listing_id", this.aliceListing)
			.containsEntry("status", "PENDING");
		assertNoPrivateData(response);
	}

	@Test
	void theRequesterIsAlwaysTheTokenUserAndTheOwnerAlwaysTheListingOwner() throws Exception {
		String body = JSON.writeValueAsString(Map.of("requesterId", this.charlie.getId(), "ownerId", this.bob.getId(),
				"status", "ACCEPTED"));
		this.mockMvc
			.perform(authorized(post(LISTINGS + "/" + this.aliceListing + "/interest")
				.param("requesterId", this.charlie.getId().toString())
				.param("ownerId", this.bob.getId().toString()), this.bobToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.requester.id").value(this.bob.getId()))
			.andExpect(jsonPath("$.owner.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(rows(this.charlie, this.aliceListing)).isZero();
		assertThat(rows(this.bob, this.aliceListing)).isEqualTo(1);

		assertThat(ids(sent(this.charlieToken, "requesterId=" + this.bob.getId()))).isEmpty();
		assertThat(ids(received(this.charlieToken, "ownerId=" + this.alice.getId()))).isEmpty();
	}

	@Test
	void interestRequiresAuthentication() throws Exception {
		this.mockMvc.perform(post(LISTINGS + "/" + this.aliceListing + "/interest"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(authorized(post(LISTINGS + "/" + this.aliceListing + "/interest"), "not-a-jwt"))
			.andExpect(status().isUnauthorized());
		assertThat(connectionCount()).isZero();
	}

	@Test
	void interestInNonPublicListingsIs404ExactlyLikeAMissingListing() throws Exception {
		String missing = interest(this.bobToken, 999_999_999L).andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Listing not found."))
			.andReturn()
			.getResponse()
			.getContentAsString();
		for (long hidden : new long[] { this.draft, this.archived, this.suspended }) {
			String response = interest(this.bobToken, hidden).andExpect(status().isNotFound())
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(response).isEqualTo(missing.replace("999999999", Long.toString(hidden)));
		}
		assertThat(connectionCount()).isZero();
	}

	@Test
	void ownerCannotExpressInterestInTheirOwnListing() throws Exception {
		interest(this.aliceToken, this.aliceListing).andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("You cannot express interest in your own listing."));
		assertThat(connectionCount()).isZero();
	}

	@Test
	void repeatedInterestReturnsTheExistingPendingOrAcceptedConnection() throws Exception {
		long pending = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);
		interest(this.bobToken, this.aliceListing).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(pending))
			.andExpect(jsonPath("$.status").value("PENDING"));

		long accepted = connectionIn(ConnectionStatus.ACCEPTED, this.charlieToken, this.aliceListing);
		interest(this.charlieToken, this.aliceListing).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(accepted))
			.andExpect(jsonPath("$.status").value("ACCEPTED"));

		assertThat(rows(this.bob, this.aliceListing)).isEqualTo(1);
		assertThat(rows(this.charlie, this.aliceListing)).isEqualTo(1);
	}

	@Test
	void rejectedOrWithdrawnInterestIsNotReopened() throws Exception {
		long rejected = connectionIn(ConnectionStatus.REJECTED, this.bobToken, this.aliceListing);
		interest(this.bobToken, this.aliceListing).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Your interest in this listing was declined and cannot be sent again."));

		long withdrawn = connectionIn(ConnectionStatus.WITHDRAWN, this.charlieToken, this.aliceListing);
		interest(this.charlieToken, this.aliceListing).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You withdrew your interest in this listing; it cannot be sent again."));

		assertThat(statusOf(rejected)).isEqualTo("REJECTED");
		assertThat(statusOf(withdrawn)).isEqualTo("WITHDRAWN");
		assertThat(connectionCount()).isEqualTo(2);
	}

	@Test
	void concurrentInterestCreatesExactlyOneConnection() throws Exception {
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				Callable<String> call = () -> {
					start.await();
					var response = interest(this.bobToken, this.aliceListing).andReturn().getResponse();
					return response.getStatus() + " " + JsonPath.read(response.getContentAsString(), "$.id");
				};
				results.add(executor.submit(call));
			}
			start.countDown();
			List<String> outcomes = new ArrayList<>();
			for (Future<String> result : results) {
				outcomes.add(result.get(30, TimeUnit.SECONDS));
			}
			assertThat(outcomes).filteredOn(o -> o.startsWith("201 ")).hasSize(1);
			assertThat(outcomes).allMatch(o -> o.startsWith("201 ") || o.startsWith("200 "));
			assertThat(outcomes.stream().map(o -> o.split(" ")[1]).distinct()).hasSize(1);
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(rows(this.bob, this.aliceListing)).isEqualTo(1);
	}

	@Test
	void sentContainsOnlyTheCallersRequests() throws Exception {
		long bobToAlice = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);
		long bobToAlice2 = connectionIn(ConnectionStatus.ACCEPTED, this.bobToken, this.aliceListing2);
		long charlieToAlice = connectionIn(ConnectionStatus.PENDING, this.charlieToken, this.aliceListing);
		long charlieToBob = connectionIn(ConnectionStatus.PENDING, this.charlieToken, this.bobListing);

		assertThat(ids(sent(this.bobToken, ""))).containsExactlyInAnyOrder(bobToAlice, bobToAlice2);
		assertThat(ids(sent(this.charlieToken, ""))).containsExactlyInAnyOrder(charlieToAlice, charlieToBob);
		sent(this.aliceToken, "").andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(true));
		this.mockMvc.perform(get(CONNECTIONS + "/sent")).andExpect(status().isUnauthorized());
	}

	@Test
	void sentIsOrderedFilteredAndPaginated() throws Exception {
		long toAlice = connectionIn(ConnectionStatus.PENDING, this.charlieToken, this.aliceListing);
		long toAlice2 = connectionIn(ConnectionStatus.ACCEPTED, this.charlieToken, this.aliceListing2);
		long toBob = connectionIn(ConnectionStatus.REJECTED, this.charlieToken, this.bobListing);
		Instant base = Instant.parse("2026-07-01T09:00:00Z");
		setCreatedAt(toAlice, base.plusSeconds(60));
		setCreatedAt(toAlice2, base);
		setCreatedAt(toBob, base.plusSeconds(60));

		assertThat(ids(sent(this.charlieToken, ""))).containsExactly(toBob, toAlice, toAlice2);
		assertThat(ids(sent(this.charlieToken, "status=PENDING"))).containsExactly(toAlice);
		assertThat(ids(sent(this.charlieToken, "status=ACCEPTED"))).containsExactly(toAlice2);
		assertThat(ids(sent(this.charlieToken, "status=REJECTED"))).containsExactly(toBob);
		assertThat(ids(sent(this.charlieToken, "status=WITHDRAWN"))).isEmpty();

		sent(this.charlieToken, "page=0&size=2").andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(false));
		assertThat(ids(sent(this.charlieToken, "page=0&size=2"))).containsExactly(toBob, toAlice);
		assertThat(ids(sent(this.charlieToken, "page=1&size=2"))).containsExactly(toAlice2);
		sent(this.charlieToken, "").andExpect(jsonPath("$.size").value(12));

		assertInvalidQueriesRejected(CONNECTIONS + "/sent", this.charlieToken);
	}

	@Test
	void receivedContainsOnlyConnectionsForListingsTheCallerOwns() throws Exception {
		long bobToAlice = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);
		long charlieToAlice = connectionIn(ConnectionStatus.PENDING, this.charlieToken, this.aliceListing);
		long charlieToAlice2 = connectionIn(ConnectionStatus.PENDING, this.charlieToken, this.aliceListing2);
		long charlieToBob = connectionIn(ConnectionStatus.PENDING, this.charlieToken, this.bobListing);

		assertThat(ids(received(this.aliceToken, ""))).containsExactlyInAnyOrder(bobToAlice, charlieToAlice,
				charlieToAlice2);
		assertThat(ids(received(this.bobToken, ""))).containsExactly(charlieToBob);
		received(this.charlieToken, "").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
		this.mockMvc.perform(get(CONNECTIONS + "/received")).andExpect(status().isUnauthorized());
	}

	@Test
	void receivedIsOrderedFilteredAndPaginated() throws Exception {
		long bobPending = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);
		long charliePending = connectionIn(ConnectionStatus.PENDING, this.charlieToken, this.aliceListing);
		long charlieAccepted = connectionIn(ConnectionStatus.ACCEPTED, this.charlieToken, this.aliceListing2);
		Instant base = Instant.parse("2026-07-02T09:00:00Z");
		setCreatedAt(bobPending, base.plusSeconds(120));
		setCreatedAt(charliePending, base);
		setCreatedAt(charlieAccepted, base);

		assertThat(ids(received(this.aliceToken, ""))).containsExactly(bobPending, charlieAccepted, charliePending);
		assertThat(ids(received(this.aliceToken, "status=PENDING"))).containsExactly(bobPending, charliePending);
		assertThat(ids(received(this.aliceToken, "status=ACCEPTED"))).containsExactly(charlieAccepted);
		assertThat(ids(received(this.aliceToken, "status=REJECTED"))).isEmpty();
		assertThat(ids(received(this.aliceToken, "page=1&size=2"))).containsExactly(charliePending);
		received(this.aliceToken, "page=1&size=2").andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.last").value(true));

		assertInvalidQueriesRejected(CONNECTIONS + "/received", this.aliceToken);
	}

	@Test
	void onlyTheTwoParticipantsCanViewAConnection() throws Exception {
		long id = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);

		detail(this.bobToken, id).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
		detail(this.aliceToken, id).andExpect(status().isOk())
			.andExpect(jsonPath("$.requester.username").value("bob"))
			.andExpect(jsonPath("$.owner.username").value("alice"));

		String missing = detail(this.charlieToken, 999_999_999L).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Connection not found."))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String unrelated = detail(this.charlieToken, id).andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(unrelated).isEqualTo(missing.replace("999999999", Long.toString(id)));
		this.mockMvc.perform(get(CONNECTIONS + "/" + id))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void listingOwnerAcceptsAPendingRequest() throws Exception {
		long id = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);
		setCreatedAndUpdatedAt(id, Instant.parse("2026-07-03T09:00:00Z"));

		accept(this.aliceToken, id).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.createdAt").value("2026-07-03T09:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value(not("2026-07-03T09:00:00Z")));
		assertThat(statusOf(id)).isEqualTo("ACCEPTED");
	}

	@Test
	void listingOwnerRejectsAPendingRequestAndTheRowIsKept() throws Exception {
		long id = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);

		reject(this.aliceToken, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
		assertThat(statusOf(id)).isEqualTo("REJECTED");
		assertThat(connectionCount()).isEqualTo(1);
	}

	@Test
	void requesterWithdrawsAPendingRequestAndTheRowIsKept() throws Exception {
		long id = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);

		withdraw(this.bobToken, id).andExpect(status().isNoContent()).andExpect(content().string(""));
		assertThat(statusOf(id)).isEqualTo("WITHDRAWN");
		assertThat(connectionCount()).isEqualTo(1);
		detail(this.bobToken, id).andExpect(jsonPath("$.status").value("WITHDRAWN"));
	}

	@Test
	void onlyTheOwnerAnswersAndOnlyTheRequesterWithdraws() throws Exception {
		long id = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);

		accept(this.bobToken, id).andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Only the listing owner can accept or reject this request."));
		reject(this.bobToken, id).andExpect(status().isForbidden());
		withdraw(this.aliceToken, id).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Only the requester can withdraw this request."));

		for (ResultActions attempt : List.of(accept(this.charlieToken, id), reject(this.charlieToken, id),
				withdraw(this.charlieToken, id))) {
			attempt.andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Connection not found."));
		}
		for (MockHttpServletRequestBuilder anonymous : List.of(post(CONNECTIONS + "/" + id + "/accept"),
				post(CONNECTIONS + "/" + id + "/reject"), delete(CONNECTIONS + "/" + id))) {
			this.mockMvc.perform(anonymous).andExpect(status().isUnauthorized());
		}
		assertThat(statusOf(id)).isEqualTo("PENDING");
	}

	@Test
	void terminalConnectionsCannotChangeAgain() throws Exception {
		for (ConnectionStatus terminal : new ConnectionStatus[] { ConnectionStatus.ACCEPTED, ConnectionStatus.REJECTED,
				ConnectionStatus.WITHDRAWN }) {
			cleanConnections();
			long id = connectionIn(terminal, this.bobToken, this.aliceListing);

			accept(this.aliceToken, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("A connection with status " + terminal + " cannot be accepted."));
			reject(this.aliceToken, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("A connection with status " + terminal + " cannot be rejected."));
			withdraw(this.bobToken, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("A connection with status " + terminal + " cannot be withdrawn."));
			assertThat(statusOf(id)).as(terminal.name()).isEqualTo(terminal.name());
		}
	}

	@Test
	void connectionHistorySurvivesArchivedAndSuspendedListings() throws Exception {
		long accepted = connectionIn(ConnectionStatus.ACCEPTED, this.bobToken, this.aliceListing);
		long pending = connectionIn(ConnectionStatus.PENDING, this.charlieToken, this.aliceListing2);

		perform(delete(LISTINGS + "/" + this.aliceListing), this.aliceToken).andExpect(status().isNoContent());
		this.jdbc.update("UPDATE listings SET status = 'SUSPENDED' WHERE id = ?", this.aliceListing2);

		assertThat(connectionCount()).isEqualTo(2);
		assertThat(statusOf(accepted)).isEqualTo("ACCEPTED");
		assertThat(statusOf(pending)).isEqualTo("PENDING");
		detail(this.bobToken, accepted).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.listing.status").value("ARCHIVED"));
		detail(this.charlieToken, pending).andExpect(status().isOk())
			.andExpect(jsonPath("$.listing.status").value("SUSPENDED"));
		assertThat(ids(received(this.aliceToken, ""))).containsExactlyInAnyOrder(accepted, pending);
		assertThat(ids(sent(this.bobToken, ""))).containsExactly(accepted);

		interest(this.charlieToken, this.aliceListing).andExpect(status().isNotFound());
		interest(this.bobToken, this.aliceListing2).andExpect(status().isNotFound());
		assertThat(connectionCount()).isEqualTo(2);
	}

	@Test
	void suspendedAccountsCannotWriteButCanStillRead() throws Exception {
		long bobPending = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);
		long charliePending = connectionIn(ConnectionStatus.PENDING, this.charlieToken, this.aliceListing);

		suspendUser(this.bob);
		interest(this.bobToken, this.bobListing).andExpect(status().isForbidden());
		interest(this.bobToken, this.aliceListing2).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(FORBIDDEN_SUSPENDED));
		withdraw(this.bobToken, bobPending).andExpect(status().isForbidden());
		sent(this.bobToken, "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		detail(this.bobToken, bobPending).andExpect(status().isOk());

		suspendUser(this.alice);
		accept(this.aliceToken, charliePending).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(FORBIDDEN_SUSPENDED));
		reject(this.aliceToken, charliePending).andExpect(status().isForbidden());
		received(this.aliceToken, "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));

		assertThat(statusOf(bobPending)).isEqualTo("PENDING");
		assertThat(statusOf(charliePending)).isEqualTo("PENDING");
		assertThat(connectionCount()).isEqualTo(2);
	}

	@Test
	void noConnectionResponseContainsPrivateOrSecurityData() throws Exception {
		long id = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);
		List<String> responses = List.of(
				interest(this.bobToken, this.aliceListing).andReturn().getResponse().getContentAsString(),
				detail(this.aliceToken, id).andReturn().getResponse().getContentAsString(),
				sent(this.bobToken, "").andReturn().getResponse().getContentAsString(),
				received(this.aliceToken, "").andReturn().getResponse().getContentAsString(),
				accept(this.aliceToken, id).andReturn().getResponse().getContentAsString());
		for (String response : responses) {
			assertNoPrivateData(response);
		}
		detail(this.aliceToken, id).andExpect(jsonPath("$.requester.email").doesNotExist())
			.andExpect(jsonPath("$.owner.email").doesNotExist())
			.andExpect(jsonPath("$.listing.owner").doesNotExist())
			.andExpect(jsonPath("$.listing.description").doesNotExist());
	}

	@Test
	void savedListingsDiscoveryListingManagementAndAuthStillWork() throws Exception {
		long id = connectionIn(ConnectionStatus.PENDING, this.bobToken, this.aliceListing);

		perform(post(LISTINGS + "/" + this.aliceListing + "/save"), this.bobToken).andExpect(status().isNoContent());
		perform(get("/api/v1/saved-listings"), this.bobToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(this.aliceListing));
		this.mockMvc.perform(get(LISTINGS)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
		this.mockMvc
			.perform(authorized(put(LISTINGS + "/" + this.aliceListing), this.aliceToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(listingBody("Alice opportunity, edited"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		detail(this.bobToken, id).andExpect(jsonPath("$.listing.title").value("Alice opportunity, edited"));
		perform(get(LISTINGS + "/mine"), this.aliceToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(5));
		perform(get("/api/v1/auth/me"), this.charlieToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("charlie"));
	}

	private long connectionIn(ConnectionStatus status, String requesterToken, long listingId) throws Exception {
		String response = interest(requesterToken, listingId).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long id = ((Number) JsonPath.read(response, "$.id")).longValue();
		String ownerToken = ownerTokenOf(listingId);
		switch (status) {
			case PENDING -> {
			}
			case ACCEPTED -> accept(ownerToken, id).andExpect(status().isOk());
			case REJECTED -> reject(ownerToken, id).andExpect(status().isOk());
			case WITHDRAWN -> withdraw(requesterToken, id).andExpect(status().isNoContent());
		}
		assertThat(statusOf(id)).isEqualTo(status.name());
		return id;
	}

	private String ownerTokenOf(long listingId) {
		Long ownerId = this.jdbc.queryForObject("SELECT owner_id FROM listings WHERE id = ?", Long.class, listingId);
		return ownerId.equals(this.alice.getId()) ? this.aliceToken : this.bobToken;
	}

	private ResultActions interest(String token, long listingId) throws Exception {
		return perform(post(LISTINGS + "/" + listingId + "/interest"), token);
	}

	private ResultActions detail(String token, long id) throws Exception {
		return perform(get(CONNECTIONS + "/" + id), token);
	}

	private ResultActions accept(String token, long id) throws Exception {
		return perform(post(CONNECTIONS + "/" + id + "/accept"), token);
	}

	private ResultActions reject(String token, long id) throws Exception {
		return perform(post(CONNECTIONS + "/" + id + "/reject"), token);
	}

	private ResultActions withdraw(String token, long id) throws Exception {
		return perform(delete(CONNECTIONS + "/" + id), token);
	}

	private ResultActions sent(String token, String query) throws Exception {
		return perform(withQuery(get(CONNECTIONS + "/sent"), query), token);
	}

	private ResultActions received(String token, String query) throws Exception {
		return perform(withQuery(get(CONNECTIONS + "/received"), query), token);
	}

	private void assertInvalidQueriesRejected(String path, String token) throws Exception {
		for (String query : new String[] { "status=OPEN", "status=pending", "size=51", "size=0", "page=-1",
				"page=10001", "page=x" }) {
			perform(withQuery(get(path), query), token).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		perform(withQuery(get(path), "size=50"), token).andExpect(status().isOk());
	}

	private static MockHttpServletRequestBuilder withQuery(MockHttpServletRequestBuilder request, String query) {
		if (!query.isEmpty()) {
			for (String pair : query.split("&")) {
				String[] parts = pair.split("=", 2);
				request.param(parts[0], parts[1]);
			}
		}
		return request;
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
		return this.mockMvc.perform(authorized(request, token));
	}

	private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static List<Long> ids(ResultActions result) throws Exception {
		String response = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(response, "$.content[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private void assertNoPrivateData(String response) {
		assertThat(response).doesNotContain("@example.com")
			.doesNotContain("email")
			.doesNotContain("passwordHash")
			.doesNotContain("unused-hash")
			.doesNotContain(this.aliceToken)
			.doesNotContain(this.bobToken)
			.doesNotContain("eyJ")
			.doesNotContain("\"role\"");
	}

	private String statusOf(long id) {
		return this.jdbc.queryForObject("SELECT status FROM connections WHERE id = ?", String.class, id);
	}

	private int rows(User requester, long listingId) {
		Integer count = this.jdbc.queryForObject(
				"SELECT COUNT(*) FROM connections WHERE requester_id = ? AND listing_id = ?", Integer.class,
				requester.getId(), listingId);
		return (count != null) ? count : 0;
	}

	private long connectionCount() {
		Long count = this.jdbc.queryForObject("SELECT COUNT(*) FROM connections", Long.class);
		return (count != null) ? count : 0;
	}

	private void cleanConnections() {
		this.jdbc.update("DELETE FROM messages");
		this.jdbc.update("DELETE FROM conversations");
		this.jdbc.update("DELETE FROM connections");
	}

	private void setCreatedAt(long id, Instant createdAt) {
		this.jdbc.update("UPDATE connections SET created_at = ? WHERE id = ?",
				LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC), id);
	}

	private void setCreatedAndUpdatedAt(long id, Instant at) {
		LocalDateTime value = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
		this.jdbc.update("UPDATE connections SET created_at = ?, updated_at = ? WHERE id = ?", value, value, id);
	}

	private void suspendUser(User user) {
		this.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", user.getId());
	}

	private long published(String token, String title) throws Exception {
		long id = create(token, title);
		perform(post(LISTINGS + "/" + id + "/publish"), token).andExpect(status().isOk());
		return id;
	}

	private long create(String token, String title) throws Exception {
		String response = this.mockMvc
			.perform(authorized(post(LISTINGS), token).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(listingBody(title))))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private static Map<String, Object> listingBody(String title) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("shortPitch", "A short pitch.");
		body.put("description", "A private-looking long description.");
		body.put("assetType", "PROJECT");
		body.put("marketplaceMode", "COLLABORATE");
		body.put("category", "SAAS");
		body.put("stage", "PROTOTYPE");
		return body;
	}

}
