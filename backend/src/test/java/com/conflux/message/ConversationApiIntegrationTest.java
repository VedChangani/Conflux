package com.conflux.message;

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
import com.conflux.connection.Connection;
import com.conflux.connection.ConnectionRepository;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP behaviour of conversations and messages: full context, real security filter chain,
 * Flyway-migrated H2 database. Fixture, all through the real endpoints: Bob's interest in
 * Alice's listing and Charlie's interest in Bob's listing are ACCEPTED (each with its
 * conversation); Charlie's interest in Alice's listing is PENDING; Bob's interest in
 * Alice's second listing is REJECTED; Charlie's is WITHDRAWN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ConversationApiIntegrationTest {

	private static final String LISTINGS = "/api/v1/listings";

	private static final String CONNECTIONS = "/api/v1/connections";

	private static final String CONVERSATIONS = "/api/v1/conversations";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ConnectionRepository connectionRepository;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private TransactionTemplate transactionTemplate;

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

	private long bobToAlice;

	private long charlieToBob;

	private long charlieToAlicePending;

	private long bobToAlice2Rejected;

	private long charlieToAlice2Withdrawn;

	private long conversationAB;

	private long conversationCB;

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

		this.bobToAlice = interest(this.bobToken, this.aliceListing);
		accept(this.aliceToken, this.bobToAlice).andExpect(status().isOk());
		this.charlieToBob = interest(this.charlieToken, this.bobListing);
		accept(this.bobToken, this.charlieToBob).andExpect(status().isOk());
		this.charlieToAlicePending = interest(this.charlieToken, this.aliceListing);
		this.bobToAlice2Rejected = interest(this.bobToken, this.aliceListing2);
		perform(post(CONNECTIONS + "/" + this.bobToAlice2Rejected + "/reject"), this.aliceToken)
			.andExpect(status().isOk());
		this.charlieToAlice2Withdrawn = interest(this.charlieToken, this.aliceListing2);
		perform(delete(CONNECTIONS + "/" + this.charlieToAlice2Withdrawn), this.charlieToken)
			.andExpect(status().isNoContent());

		this.conversationAB = conversationOf(this.bobToAlice);
		this.conversationCB = conversationOf(this.charlieToBob);
	}

	// Everything references users and listings, so it is deleted first, newest tables first.
	@AfterEach
	void cleanDatabase() {
		for (String table : new String[] { "messages", "conversations", "connections", "saved_listings", "listings",
				"users" }) {
			this.jdbc.update("DELETE FROM " + table);
		}
	}

	// ---- Conversation creation ------------------------------------------------------------

	@Test
	void acceptingAConnectionCreatesExactlyOneConversationForIt() throws Exception {
		assertThat(conversationCount(this.bobToAlice)).isEqualTo(1);
		assertThat(conversationCount(this.charlieToBob)).isEqualTo(1);

		long accepted = this.charlieToAlicePending;
		accept(this.aliceToken, accepted).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));

		assertThat(conversationCount(accepted)).isEqualTo(1);
		long conversation = conversationOf(accepted);
		detail(this.charlieToken, conversation).andExpect(status().isOk())
			.andExpect(jsonPath("$.connectionId").value(accepted))
			.andExpect(jsonPath("$.listing.id").value(this.aliceListing))
			.andExpect(jsonPath("$.otherParticipant.username").value("alice"))
			.andExpect(jsonPath("$.lastMessagePreview").doesNotExist())
			.andExpect(jsonPath("$.lastMessageAt").doesNotExist());
		// Accepting again does not create another one.
		accept(this.aliceToken, accepted).andExpect(status().isConflict());
		assertThat(conversationCount(accepted)).isEqualTo(1);
	}

	@Test
	void rejectedWithdrawnAndPendingConnectionsHaveNoConversation() {
		assertThat(conversationCount(this.bobToAlice2Rejected)).isZero();
		assertThat(conversationCount(this.charlieToAlice2Withdrawn)).isZero();
		assertThat(conversationCount(this.charlieToAlicePending)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Long.class)).isEqualTo(2);
	}

	@Test
	void concurrentAcceptsCreateOneConversation() throws Exception {
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				Callable<Integer> call = () -> {
					start.await();
					return accept(this.aliceToken, this.charlieToAlicePending).andReturn().getResponse().getStatus();
				};
				results.add(executor.submit(call));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(30, TimeUnit.SECONDS));
			}
			assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
			assertThat(statuses).filteredOn(s -> s == 409).hasSize(threads - 1);
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(conversationCount(this.charlieToAlicePending)).isEqualTo(1);
		assertThat(connectionStatus(this.charlieToAlicePending)).isEqualTo("ACCEPTED");
	}

	@Test
	void racingAcceptRejectAndWithdrawNeverLeaveAConversationForAnUnacceptedConnection() throws Exception {
		for (int round = 0; round < 3; round++) {
			long listing = published(this.aliceToken, "Race listing " + round);
			long connection = interest(this.bobToken, listing);
			ExecutorService executor = Executors.newFixedThreadPool(6);
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Integer>> results = new ArrayList<>();
			try {
				for (int i = 0; i < 6; i++) {
					MockHttpServletRequestBuilder request = switch (i % 3) {
						case 0 -> authorized(post(CONNECTIONS + "/" + connection + "/accept"), this.aliceToken);
						case 1 -> authorized(post(CONNECTIONS + "/" + connection + "/reject"), this.aliceToken);
						default -> authorized(delete(CONNECTIONS + "/" + connection), this.bobToken);
					};
					results.add(executor.submit(() -> {
						start.await();
						return this.mockMvc.perform(request).andReturn().getResponse().getStatus();
					}));
				}
				start.countDown();
				List<Integer> statuses = new ArrayList<>();
				for (Future<Integer> result : results) {
					statuses.add(result.get(30, TimeUnit.SECONDS));
				}
				// Exactly one transition wins; every other request sees its committed result.
				assertThat(statuses).filteredOn(s -> s == 200 || s == 204).hasSize(1);
				assertThat(statuses).filteredOn(s -> s == 409).hasSize(5);
			}
			finally {
				executor.shutdownNow();
			}
			String status = connectionStatus(connection);
			assertThat(conversationCount(connection)).as(status).isEqualTo("ACCEPTED".equals(status) ? 1 : 0);
		}
	}

	@Test
	void creatingTheConversationAgainReturnsTheExistingOne() {
		Long first = this.transactionTemplate.execute(status -> this.conversationService
			.createFor(this.connectionRepository.findById(this.bobToAlice).orElseThrow())
			.getId());
		Long second = this.transactionTemplate.execute(status -> this.conversationService
			.createFor(this.connectionRepository.findById(this.bobToAlice).orElseThrow())
			.getId());

		assertThat(first).isEqualTo(this.conversationAB).isEqualTo(second);
		assertThat(conversationCount(this.bobToAlice)).isEqualTo(1);
		// Never for a connection that is not accepted.
		assertThatIllegalStateException()
			.isThrownBy(() -> this.transactionTemplate.execute(status -> this.conversationService
				.createFor(this.connectionRepository.findById(this.charlieToAlicePending).orElseThrow())));
		assertThat(conversationCount(this.charlieToAlicePending)).isZero();
	}

	@Test
	void thereIsNoEndpointToStartAConversation() throws Exception {
		perform(post(CONVERSATIONS).contentType(MediaType.APPLICATION_JSON).content("{\"userId\": 1}"), this.charlieToken)
			.andExpect(status().isMethodNotAllowed());
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Long.class)).isEqualTo(2);
	}

	// ---- Conversation access -------------------------------------------------------------

	@Test
	void bothParticipantsCanViewAConversationRelativeToThemselves() throws Exception {
		detail(this.bobToken, this.conversationAB).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(this.conversationAB))
			.andExpect(jsonPath("$.connectionId").value(this.bobToAlice))
			.andExpect(jsonPath("$.listing.id").value(this.aliceListing))
			.andExpect(jsonPath("$.listing.slug").exists())
			.andExpect(jsonPath("$.listing.title").value("Alice opportunity"))
			.andExpect(jsonPath("$.listing.shortPitch").value("A short pitch."))
			.andExpect(jsonPath("$.otherParticipant.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.otherParticipant.username").value("alice"))
			.andExpect(jsonPath("$.otherParticipant.displayName").value("Alice A"))
			.andExpect(jsonPath("$.createdAt").exists())
			.andExpect(jsonPath("$.updatedAt").exists());
		detail(this.aliceToken, this.conversationAB).andExpect(status().isOk())
			.andExpect(jsonPath("$.otherParticipant.id").value(this.bob.getId()))
			.andExpect(jsonPath("$.otherParticipant.username").value("bob"));
	}

	@Test
	void unrelatedUsersCannotTellAConversationExists() throws Exception {
		String missing = detail(this.charlieToken, 999_999_999L).andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Conversation not found."))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String unrelated = detail(this.charlieToken, this.conversationAB).andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(unrelated).isEqualTo(missing.replace("999999999", Long.toString(this.conversationAB)));

		perform(get(CONVERSATIONS + "/" + this.conversationAB + "/messages"), this.charlieToken)
			.andExpect(status().isNotFound());
		for (MockHttpServletRequestBuilder anonymous : List.of(get(CONVERSATIONS),
				get(CONVERSATIONS + "/" + this.conversationAB), get(CONVERSATIONS + "/" + this.conversationAB + "/messages"),
				post(CONVERSATIONS + "/" + this.conversationAB + "/messages").contentType(MediaType.APPLICATION_JSON)
					.content("{\"content\":\"hi\"}"))) {
			this.mockMvc.perform(anonymous)
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Test
	void eachUserListsOnlyTheirOwnConversations() throws Exception {
		assertThat(conversationIds(this.aliceToken, "")).containsExactly(this.conversationAB);
		assertThat(conversationIds(this.bobToken, "")).containsExactlyInAnyOrder(this.conversationAB,
				this.conversationCB);
		assertThat(conversationIds(this.charlieToken, "")).containsExactly(this.conversationCB);
		// User ids in the query cannot select someone else's conversations.
		assertThat(conversationIds(this.charlieToken, "userId=" + this.alice.getId())).containsExactly(this.conversationCB);

		// Bob is the requester in one and the owner in the other: "other participant" follows him.
		String bobs = perform(get(CONVERSATIONS), this.bobToken).andReturn().getResponse().getContentAsString();
		List<String> others = JsonPath.read(bobs, "$.content[*].otherParticipant.username");
		assertThat(others).containsExactlyInAnyOrder("alice", "charlie");
	}

	// ---- Sending messages ------------------------------------------------------------------

	@Test
	void participantSendsAMessageAsThemselves() throws Exception {
		String response = send(this.bobToken, this.conversationAB, "  Hello Alice,\n\n  keen to   talk!  ")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.sender.id").value(this.bob.getId()))
			.andExpect(jsonPath("$.sender.username").value("bob"))
			.andExpect(jsonPath("$.sender.displayName").value("Bob B"))
			.andExpect(jsonPath("$.content").value("Hello Alice,\n\n  keen to   talk!"))
			.andExpect(jsonPath("$.createdAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertNoPrivateData(response);

		send(this.aliceToken, this.conversationAB, "Hi Bob").andExpect(status().isCreated())
			.andExpect(jsonPath("$.sender.username").value("alice"));
		assertThat(this.jdbc.queryForList("SELECT sender_id FROM messages ORDER BY id", Long.class))
			.containsExactly(this.bob.getId(), this.alice.getId());
	}

	@Test
	void theSenderCannotBeChosenByTheClient() throws Exception {
		String body = JSON.writeValueAsString(Map.of("content", "Who am I?", "senderId", this.alice.getId(), "sender",
				Map.of("id", this.alice.getId()), "conversationId", this.conversationCB));
		this.mockMvc
			.perform(authorized(post(CONVERSATIONS + "/" + this.conversationAB + "/messages")
				.param("senderId", this.alice.getId().toString())
				.param("userId", this.alice.getId().toString()), this.bobToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sender.id").value(this.bob.getId()));
		assertThat(this.jdbc.queryForMap("SELECT sender_id, conversation_id FROM messages"))
			.containsEntry("sender_id", this.bob.getId())
			.containsEntry("conversation_id", this.conversationAB);
	}

	@Test
	void unrelatedOrAnonymousUsersCannotSend() throws Exception {
		send(this.charlieToken, this.conversationAB, "Let me in").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Conversation not found."));
		send(this.bobToken, 999_999_999L, "Nowhere").andExpect(status().isNotFound());
		this.mockMvc
			.perform(post(CONVERSATIONS + "/" + this.conversationAB + "/messages").contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"anonymous\"}"))
			.andExpect(status().isUnauthorized());
		assertThat(messageCount()).isZero();
	}

	@Test
	void suspendedUsersCannotSendButCanStillRead() throws Exception {
		send(this.bobToken, this.conversationAB, "Before suspension").andExpect(status().isCreated());
		this.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", this.bob.getId());

		send(this.bobToken, this.conversationAB, "After suspension").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("This account is suspended."));
		messages(this.bobToken, this.conversationAB, "").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		detail(this.bobToken, this.conversationAB).andExpect(status().isOk());
		perform(get(CONVERSATIONS), this.bobToken).andExpect(status().isOk());
		assertThat(messageCount()).isEqualTo(1);
	}

	@Test
	void contentMustBeNonBlankAndAtMost5000CharactersAfterTrimming() throws Exception {
		for (String body : new String[] { "{}", "{\"content\":null}", "{\"content\":\"\"}", "{\"content\":\"   \\n\\t \"}",
				JSON.writeValueAsString(Map.of("content", "x".repeat(5_001))) }) {
			sendRaw(this.bobToken, this.conversationAB, body).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[0].field").value("content"));
		}
		sendRaw(this.bobToken, this.conversationAB, "{ not json").andExpect(status().isBadRequest());
		assertThat(messageCount()).isZero();

		// The limit applies to the trimmed text.
		send(this.bobToken, this.conversationAB, "  " + "y".repeat(5_000) + " \n").andExpect(status().isCreated())
			.andExpect(jsonPath("$.content").value("y".repeat(5_000)));
	}

	// ---- Accepted-connection rule ----------------------------------------------------------

	@Test
	void onlyConversationsOfAcceptedConnectionsCanBeMessaged() throws Exception {
		// Conversations cannot exist for these through the API; insert them to prove the send check itself.
		for (long connection : new long[] { this.charlieToAlicePending, this.bobToAlice2Rejected,
				this.charlieToAlice2Withdrawn }) {
			this.jdbc.update("INSERT INTO conversations (connection_id, created_at, updated_at) "
					+ "VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", connection);
			long conversation = conversationOf(connection);
			String requesterToken = (connection == this.bobToAlice2Rejected) ? this.bobToken : this.charlieToken;

			send(requesterToken, conversation, "Can I message?").andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Messages can only be sent in conversations of accepted connections."));
			send(this.aliceToken, conversation, "Owner reply?").andExpect(status().isConflict());
		}
		assertThat(messageCount()).isZero();

		send(this.charlieToken, this.conversationCB, "Accepted: fine").andExpect(status().isCreated());
		send(this.bobToken, this.conversationCB, "Owner: fine").andExpect(status().isCreated());
	}

	// ---- Message listing -------------------------------------------------------------------

	@Test
	void messagesAreListedNewestFirstWithDeterministicTiesAndPagination() throws Exception {
		long m1 = sentId(this.bobToken, this.conversationAB, "one");
		long m2 = sentId(this.aliceToken, this.conversationAB, "two");
		long m3 = sentId(this.bobToken, this.conversationAB, "three");
		long m4 = sentId(this.aliceToken, this.conversationAB, "four");
		sentId(this.charlieToken, this.conversationCB, "other conversation");
		Instant base = Instant.parse("2026-08-01T10:00:00Z");
		setMessageTime(m1, base);
		setMessageTime(m2, base.plusSeconds(10));
		setMessageTime(m3, base.plusSeconds(10)); // tie with m2: higher id first
		setMessageTime(m4, base.plusSeconds(20));

		for (String token : new String[] { this.bobToken, this.aliceToken }) {
			assertThat(messageIds(token, this.conversationAB, "")).containsExactly(m4, m3, m2, m1);
		}
		messages(this.bobToken, this.conversationAB, "").andExpect(jsonPath("$.size").value(50))
			.andExpect(jsonPath("$.totalElements").value(4));
		messages(this.bobToken, this.conversationAB, "page=0&size=3").andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(false));
		assertThat(messageIds(this.bobToken, this.conversationAB, "page=0&size=3")).containsExactly(m4, m3, m2);
		assertThat(messageIds(this.bobToken, this.conversationAB, "page=1&size=3")).containsExactly(m1);
		messages(this.bobToken, this.conversationAB, "size=100").andExpect(status().isOk());
		for (String query : new String[] { "size=101", "size=0", "page=-1", "page=10001", "page=x" }) {
			messages(this.bobToken, this.conversationAB, query).andExpect(status().isBadRequest());
		}

		String page = messages(this.aliceToken, this.conversationAB, "").andReturn().getResponse().getContentAsString();
		assertNoPrivateData(page);
		assertThat((List<String>) JsonPath.read(page, "$.content[*].sender.username")).containsExactly("alice", "bob",
				"alice", "bob");
	}

	@Test
	void emptyConversationHasAnEmptyMessagePage() throws Exception {
		messages(this.charlieToken, this.conversationCB, "").andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	// ---- Conversation ordering -------------------------------------------------------------

	@Test
	void conversationsAreOrderedByLatestActivityThenIdWithPreviews() throws Exception {
		accept(this.aliceToken, this.charlieToAlicePending).andExpect(status().isOk());
		long conversationCA = conversationOf(this.charlieToAlicePending);
		long aliceListing3 = published(this.aliceToken, "Alice third opportunity");
		long bobToAlice3 = interest(this.bobToken, aliceListing3);
		accept(this.aliceToken, bobToAlice3).andExpect(status().isOk());
		long conversationAB3 = conversationOf(bobToAlice3);

		setConversationCreatedAt(this.conversationAB, Instant.parse("2026-09-01T00:00:00Z"));
		setConversationCreatedAt(conversationCA, Instant.parse("2026-09-05T00:00:00Z")); // no messages
		setConversationCreatedAt(conversationAB3, Instant.parse("2026-09-02T00:00:00Z"));
		long early = sentId(this.bobToken, this.conversationAB, "Earlier message");
		long latestAB = sentId(this.aliceToken, this.conversationAB, "Latest message in AB");
		long longOne = sentId(this.bobToken, conversationAB3, "L".repeat(200));
		setMessageTime(early, Instant.parse("2026-09-08T00:00:00Z"));
		setMessageTime(latestAB, Instant.parse("2026-09-10T00:00:00Z"));
		setMessageTime(longOne, Instant.parse("2026-09-10T00:00:00Z")); // tie with AB: higher conversation id first

		// AB3 and AB share the latest activity (AB3 has the higher id); CA has no messages (its creation).
		assertThat(conversationIds(this.aliceToken, "")).containsExactly(conversationAB3, this.conversationAB,
				conversationCA);
		String page = perform(get(CONVERSATIONS), this.aliceToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.content[0].lastMessagePreview").value("L".repeat(120) + "…"))
			.andExpect(jsonPath("$.content[0].lastMessageAt").value("2026-09-10T00:00:00Z"))
			.andExpect(jsonPath("$.content[1].lastMessagePreview").value("Latest message in AB"))
			.andExpect(jsonPath("$.content[1].lastMessageAt").value("2026-09-10T00:00:00Z"))
			.andExpect(jsonPath("$.content[2].lastMessagePreview").value(nullValue()))
			.andExpect(jsonPath("$.content[2].lastMessageAt").value(nullValue()))
			.andExpect(jsonPath("$.content[2].createdAt").value("2026-09-05T00:00:00Z"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertNoPrivateData(page);
		detail(this.aliceToken, this.conversationAB).andExpect(jsonPath("$.lastMessagePreview").value("Latest message in AB"));

		perform(get(CONVERSATIONS).param("page", "0").param("size", "2"), this.aliceToken)
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.last").value(false));
		assertThat(conversationIds(this.aliceToken, "page=0&size=2")).containsExactly(conversationAB3, this.conversationAB);
		assertThat(conversationIds(this.aliceToken, "page=1&size=2")).containsExactly(conversationCA);
		for (String query : new String[] { "size=51", "size=0", "page=-1", "page=10001" }) {
			perform(withQuery(get(CONVERSATIONS), query), this.aliceToken).andExpect(status().isBadRequest());
		}
	}

	// ---- Listing lifecycle -----------------------------------------------------------------

	@Test
	void conversationsSurviveArchivedAndSuspendedListings() throws Exception {
		send(this.bobToken, this.conversationAB, "Before archive").andExpect(status().isCreated());
		perform(delete(LISTINGS + "/" + this.aliceListing), this.aliceToken).andExpect(status().isNoContent());
		this.jdbc.update("UPDATE listings SET status = 'SUSPENDED' WHERE id = ?", this.bobListing);

		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Long.class)).isEqualTo(2);
		detail(this.bobToken, this.conversationAB).andExpect(status().isOk());
		detail(this.charlieToken, this.conversationCB).andExpect(status().isOk());
		messages(this.aliceToken, this.conversationAB, "").andExpect(jsonPath("$.totalElements").value(1));
		// Still usable: messaging depends on the accepted connection, not on listing visibility.
		send(this.aliceToken, this.conversationAB, "After archive").andExpect(status().isCreated());
		send(this.charlieToken, this.conversationCB, "After suspension").andExpect(status().isCreated());

		// New interest still needs a published listing.
		perform(post(LISTINGS + "/" + this.aliceListing + "/interest"), this.charlieToken)
			.andExpect(status().isNotFound());
	}

	// ---- Security / data exposure --------------------------------------------------------

	@Test
	void messageContentNeverAppearsInLogs(CapturedOutput output) throws Exception {
		String secret = "SECRET-MESSAGE-TEXT-7f3a";
		send(this.bobToken, this.conversationAB, secret + " ok").andExpect(status().isCreated());
		sendRaw(this.bobToken, this.conversationAB, JSON.writeValueAsString(Map.of("content", secret + "x".repeat(5_000))))
			.andExpect(status().isBadRequest());
		send(this.charlieToken, this.conversationAB, secret).andExpect(status().isNotFound());
		this.jdbc.update("INSERT INTO conversations (connection_id, created_at, updated_at) "
				+ "VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", this.charlieToAlicePending);
		send(this.charlieToken, conversationOf(this.charlieToAlicePending), secret).andExpect(status().isConflict());
		this.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", this.bob.getId());
		send(this.bobToken, this.conversationAB, secret).andExpect(status().isForbidden());

		assertThat(output.getAll()).doesNotContain(secret);
	}

	@Test
	void responsesHaveAFixedShapeWithoutEntitiesOrPrivateData() throws Exception {
		send(this.bobToken, this.conversationAB, "Shape check").andExpect(status().isCreated());
		String detail = detail(this.bobToken, this.conversationAB).andReturn().getResponse().getContentAsString();
		assertNoPrivateData(detail);
		detail(this.bobToken, this.conversationAB).andExpect(jsonPath("$.connection").doesNotExist())
			.andExpect(jsonPath("$.messages").doesNotExist())
			.andExpect(jsonPath("$.requester").doesNotExist())
			.andExpect(jsonPath("$.owner").doesNotExist())
			.andExpect(jsonPath("$.listing.owner").doesNotExist())
			.andExpect(jsonPath("$.listing.description").doesNotExist())
			.andExpect(jsonPath("$.otherParticipant.email").doesNotExist());
		messages(this.bobToken, this.conversationAB, "").andExpect(jsonPath("$.content[0].conversation").doesNotExist())
			.andExpect(jsonPath("$.content[0].sender.email").doesNotExist())
			.andExpect(jsonPath("$.content[0].receiver").doesNotExist());
	}

	// ---- Regression ------------------------------------------------------------------------

	@Test
	void connectionsSavedListingsDiscoveryListingsAndAuthStillWork() throws Exception {
		perform(get(CONNECTIONS + "/sent"), this.bobToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2));
		perform(get(CONNECTIONS + "/received").param("status", "ACCEPTED"), this.aliceToken)
			.andExpect(jsonPath("$.content[0].id").value(this.bobToAlice));
		perform(post(LISTINGS + "/" + this.bobListing + "/save"), this.aliceToken).andExpect(status().isNoContent());
		perform(get("/api/v1/saved-listings"), this.aliceToken).andExpect(jsonPath("$.content[0].id").value(this.bobListing));
		this.mockMvc.perform(get(LISTINGS)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
		this.mockMvc
			.perform(authorized(put(LISTINGS + "/" + this.aliceListing), this.aliceToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(listingBody("Alice opportunity, renamed"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		detail(this.bobToken, this.conversationAB).andExpect(jsonPath("$.listing.title").value("Alice opportunity, renamed"));
		perform(get("/api/v1/auth/me"), this.charlieToken).andExpect(jsonPath("$.username").value("charlie"));
	}

	// ---- Helpers ------------------------------------------------------------------------

	private long interest(String token, long listingId) throws Exception {
		String response = perform(post(LISTINGS + "/" + listingId + "/interest"), token).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private ResultActions accept(String token, long connectionId) throws Exception {
		return perform(post(CONNECTIONS + "/" + connectionId + "/accept"), token);
	}

	private ResultActions detail(String token, long conversationId) throws Exception {
		return perform(get(CONVERSATIONS + "/" + conversationId), token);
	}

	private ResultActions messages(String token, long conversationId, String query) throws Exception {
		return perform(withQuery(get(CONVERSATIONS + "/" + conversationId + "/messages"), query), token);
	}

	private ResultActions send(String token, long conversationId, String content) throws Exception {
		return sendRaw(token, conversationId, JSON.writeValueAsString(Map.of("content", content)));
	}

	private ResultActions sendRaw(String token, long conversationId, String body) throws Exception {
		return perform(post(CONVERSATIONS + "/" + conversationId + "/messages").contentType(MediaType.APPLICATION_JSON)
			.content(body), token);
	}

	private long sentId(String token, long conversationId, String content) throws Exception {
		String response = send(token, conversationId, content).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private List<Long> conversationIds(String token, String query) throws Exception {
		return ids(perform(withQuery(get(CONVERSATIONS), query), token));
	}

	private List<Long> messageIds(String token, long conversationId, String query) throws Exception {
		return ids(messages(token, conversationId, query));
	}

	private static List<Long> ids(ResultActions result) throws Exception {
		String response = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(response, "$.content[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
		return this.mockMvc.perform(authorized(request, token));
	}

	private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
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

	private void assertNoPrivateData(String response) {
		assertThat(response).doesNotContain("@example.com")
			.doesNotContain("email")
			.doesNotContain("passwordHash")
			.doesNotContain("unused-hash")
			.doesNotContain("\"role\"")
			.doesNotContain("eyJ");
	}

	private long conversationOf(long connectionId) {
		return this.jdbc.queryForObject("SELECT id FROM conversations WHERE connection_id = ?", Long.class, connectionId);
	}

	private long conversationCount(long connectionId) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE connection_id = ?", Long.class,
				connectionId);
	}

	private long messageCount() {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class);
	}

	private String connectionStatus(long connectionId) {
		return this.jdbc.queryForObject("SELECT status FROM connections WHERE id = ?", String.class, connectionId);
	}

	private void setMessageTime(long messageId, Instant at) {
		this.jdbc.update("UPDATE messages SET created_at = ? WHERE id = ?", LocalDateTime.ofInstant(at, ZoneOffset.UTC),
				messageId);
	}

	private void setConversationCreatedAt(long conversationId, Instant at) {
		LocalDateTime utc = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
		this.jdbc.update("UPDATE conversations SET created_at = ?, updated_at = ? WHERE id = ?", utc, utc,
				conversationId);
	}

	private long published(String token, String title) throws Exception {
		String response = this.mockMvc
			.perform(authorized(post(LISTINGS), token).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(listingBody(title))))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long id = ((Number) JsonPath.read(response, "$.id")).longValue();
		perform(post(LISTINGS + "/" + id + "/publish"), token).andExpect(status().isOk());
		return id;
	}

	private static Map<String, Object> listingBody(String title) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("shortPitch", "A short pitch.");
		body.put("description", "A longer description.");
		body.put("assetType", "PROJECT");
		body.put("marketplaceMode", "COLLABORATE");
		body.put("category", "SAAS");
		body.put("stage", "PROTOTYPE");
		return body;
	}

}
