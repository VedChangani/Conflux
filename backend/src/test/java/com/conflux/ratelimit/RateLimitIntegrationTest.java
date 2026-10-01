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
import java.util.concurrent.atomic.AtomicInteger;

import com.conflux.auth.JwtTokenService;
import com.conflux.listing.Listing;
import com.conflux.listing.ListingAssetType;
import com.conflux.listing.ListingCategory;
import com.conflux.listing.ListingMarketplaceMode;
import com.conflux.listing.ListingRepository;
import com.conflux.listing.ListingStage;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "conflux.rate-limit.login.max-requests=3", "conflux.rate-limit.login.window=10m",
		"conflux.rate-limit.register.max-requests=2", "conflux.rate-limit.register.window=10m",
		"conflux.rate-limit.listing-create.max-requests=2", "conflux.rate-limit.listing-create.window=10m",
		"conflux.rate-limit.listing-publish.max-requests=2", "conflux.rate-limit.listing-publish.window=10m",
		"conflux.rate-limit.listing-save.max-requests=2", "conflux.rate-limit.listing-save.window=10m",
		"conflux.rate-limit.listing-interest.max-requests=2", "conflux.rate-limit.listing-interest.window=10m",
		"conflux.rate-limit.message-send.max-requests=2", "conflux.rate-limit.message-send.window=10m",
		"conflux.rate-limit.report-create.max-requests=2", "conflux.rate-limit.report-create.window=10m" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

	private static final String REGISTER = "/api/v1/auth/register";

	private static final String LOGIN = "/api/v1/auth/login";

	private static final String LISTINGS = "/api/v1/listings";

	private static final String CONNECTIONS = "/api/v1/connections";

	private static final String CONVERSATIONS = "/api/v1/conversations";

	private static final String REPORTS = "/api/v1/reports";

	private static final String PASSWORD = "correct-horse-battery";

	private static final String TOO_MANY_REQUESTS = "Too many requests. Please try again later.";

	private static final String SUSPENDED = "This account is suspended.";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final AtomicInteger IP_COUNTER = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ListingRepository listingRepository;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	private User alice;

	private User bob;

	private User charlie;

	private User dave;

	private String aliceToken;

	private String bobToken;

	private String daveToken;

	@BeforeEach
	void setUp() {
		cleanDatabase();
		String hash = this.passwordEncoder.encode(PASSWORD);
		this.alice = this.userRepository.save(new User("alice@example.com", hash, "alice", "Alice A"));
		this.bob = this.userRepository.save(new User("bob@example.com", hash, "bob", "Bob B"));
		this.charlie = this.userRepository.save(new User("charlie@example.com", hash, "charlie", "Charlie C"));
		this.dave = this.userRepository.save(new User("dave@example.com", hash, "dave", "Dave D"));
		this.aliceToken = this.tokenService.issueAccessToken(this.alice).accessToken();
		this.bobToken = this.tokenService.issueAccessToken(this.bob).accessToken();
		this.daveToken = this.tokenService.issueAccessToken(this.dave).accessToken();
	}

	@AfterEach
	void cleanDatabase() {
		for (String table : new String[] { "moderation_actions", "reports", "messages", "conversations", "connections",
				"saved_listings", "listings", "users" }) {
			this.jdbc.update("DELETE FROM " + table);
		}
	}

	@Test
	void loginsBelowTheLimitSucceedAndTheNextOneGets429ProblemDetailWithRetryAfter() throws Exception {
		String ip = nextIp();
		for (int i = 0; i < 3; i++) {
			login(ip, "alice", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
		}

		assertTooManyRequests(login(ip, "alice", PASSWORD)).andExpect(jsonPath("$.accessToken").doesNotExist());
	}

	@Test
	void repeatedFailedLoginsAreLimitedWithoutRevealingWhetherTheAccountExists() throws Exception {
		String knownIp = nextIp();
		String unknownIp = nextIp();
		for (int i = 0; i < 3; i++) {
			login(knownIp, "alice", "wrong-password-" + i).andExpect(status().isUnauthorized());
			login(unknownIp, "nobody_here", "wrong-password-" + i).andExpect(status().isUnauthorized());
		}

		String known = assertTooManyRequests(login(knownIp, "alice", "wrong-password")).andReturn()
			.getResponse()
			.getContentAsString();
		String unknown = assertTooManyRequests(login(unknownIp, "nobody_here", "wrong-password")).andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(known).isEqualTo(unknown).doesNotContain("alice").doesNotContain("nobody_here");
		assertTooManyRequests(login(knownIp, "alice", PASSWORD));
	}

	@Test
	void repeatedRegistrationsAreLimitedAndTheRejectedOneCreatesNoAccount() throws Exception {
		String ip = nextIp();
		register(ip, "new_user_1").andExpect(status().isCreated());
		register(ip, "new_user_2").andExpect(status().isCreated());

		assertTooManyRequests(register(ip, "new_user_3"));
		assertThat(this.userRepository.existsByUsername("new_user_3")).isFalse();
		assertTooManyRequests(register(ip, "alice"));
		assertTooManyRequests(this.mockMvc.perform(fromIp(post(REGISTER), ip).contentType(MediaType.APPLICATION_JSON)
			.content("{}")));
	}

	@Test
	void eachClientIpHasItsOwnAuthenticationLimits() throws Exception {
		String first = nextIp();
		String second = nextIp();
		for (int i = 0; i < 3; i++) {
			login(first, "alice", PASSWORD).andExpect(status().isOk());
		}
		register(first, "first_1").andExpect(status().isCreated());
		register(first, "first_2").andExpect(status().isCreated());
		assertTooManyRequests(login(first, "alice", PASSWORD));
		assertTooManyRequests(register(first, "first_3"));

		login(second, "alice", PASSWORD).andExpect(status().isOk());
		register(second, "second_1").andExpect(status().isCreated());
	}

	@Test
	void loginAndRegistrationHaveSeparateLimits() throws Exception {
		String ip = nextIp();
		for (int i = 0; i < 3; i++) {
			login(ip, "alice", PASSWORD).andExpect(status().isOk());
		}
		assertTooManyRequests(login(ip, "alice", PASSWORD));

		register(ip, "still_allowed").andExpect(status().isCreated());
	}

	@Test
	void proxyHeadersCannotChangeTheClientIp() throws Exception {
		String ip = nextIp();
		for (int i = 0; i < 3; i++) {
			this.mockMvc
				.perform(loginRequest(ip, "alice", PASSWORD).header("X-Forwarded-For", "203.0.113." + i)
					.header("X-Real-IP", "203.0.113." + i))
				.andExpect(status().isOk());
		}

		assertTooManyRequests(this.mockMvc.perform(loginRequest(ip, "alice", PASSWORD)
			.header("X-Forwarded-For", "203.0.113.99")
			.header("X-Real-IP", "203.0.113.99")
			.header("Forwarded", "for=203.0.113.99")));
		for (int i = 0; i < 3; i++) {
			login("203.0.113." + i, "alice", PASSWORD).andExpect(status().isOk());
		}
	}

	@Test
	void concurrentLoginAttemptsCannotExceedTheLimit() throws Exception {
		String ip = nextIp();
		int threads = 12;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				Callable<Integer> call = () -> {
					start.await();
					return login(ip, "alice", "wrong-password").andReturn().getResponse().getStatus();
				};
				results.add(executor.submit(call));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(60, TimeUnit.SECONDS));
			}
			assertThat(statuses).filteredOn(s -> s == 401).hasSize(3);
			assertThat(statuses).filteredOn(s -> s == 429).hasSize(threads - 3);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void listingCreationIsLimitedPerUser() throws Exception {
		createListing(this.aliceToken, "First").andExpect(status().isCreated());
		createListing(this.aliceToken, "Second").andExpect(status().isCreated());

		assertTooManyRequests(createListing(this.aliceToken, "Third"));
		assertThat(listingCount(this.alice)).isEqualTo(2);

		createListing(this.bobToken, "Bob's first").andExpect(status().isCreated());
	}

	@Test
	void publishingIsLimitedPerUser() throws Exception {
		long first = idOf(createListing(this.aliceToken, "First").andExpect(status().isCreated()));
		long second = idOf(createListing(this.aliceToken, "Second").andExpect(status().isCreated()));
		long third = draftOf(this.alice, "Third");
		publish(this.aliceToken, first).andExpect(status().isOk());
		publish(this.aliceToken, second).andExpect(status().isOk());

		assertTooManyRequests(publish(this.aliceToken, third));
		assertThat(this.jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, third))
			.isEqualTo("DRAFT");
	}

	@Test
	void differentOperationsHaveSeparateLimits() throws Exception {
		long first = idOf(createListing(this.aliceToken, "First").andExpect(status().isCreated()));
		long second = idOf(createListing(this.aliceToken, "Second").andExpect(status().isCreated()));
		assertTooManyRequests(createListing(this.aliceToken, "Third"));

		publish(this.aliceToken, first).andExpect(status().isOk());
		publish(this.aliceToken, second).andExpect(status().isOk());
		long davesListing = publishedListing("Dave's listing");
		save(this.aliceToken, davesListing).andExpect(status().isNoContent());
		interest(this.aliceToken, davesListing).andExpect(status().isCreated());
		report(this.aliceToken, "USER", this.charlie.getId()).andExpect(status().isCreated());
	}

	@Test
	void savingIsLimitedPerUserAndRepeatedSavesDoNotCount() throws Exception {
		long first = publishedListing("Dave's listing");
		long second = publishedListing("Dave's second listing");
		long third = publishedOf(this.dave, "Dave's third listing");
		save(this.bobToken, first).andExpect(status().isNoContent());
		for (int i = 0; i < 3; i++) {
			save(this.bobToken, first).andExpect(status().isNoContent());
		}
		save(this.bobToken, second).andExpect(status().isNoContent());

		assertTooManyRequests(save(this.bobToken, third));
		assertThat(savedCount(this.bob)).isEqualTo(2);
		save(this.bobToken, first).andExpect(status().isNoContent());
		save(this.aliceToken, third).andExpect(status().isNoContent());
	}

	@Test
	void expressingInterestIsLimitedPerUserAndRepeatedRequestsDoNotCount() throws Exception {
		long first = publishedListing("Dave's listing");
		long second = publishedListing("Dave's second listing");
		long third = publishedOf(this.dave, "Dave's third listing");
		interest(this.bobToken, first).andExpect(status().isCreated());
		for (int i = 0; i < 3; i++) {
			interest(this.bobToken, first).andExpect(status().isOk());
		}
		interest(this.bobToken, second).andExpect(status().isCreated());

		assertTooManyRequests(interest(this.bobToken, third));
		assertThat(connectionCount(this.bob)).isEqualTo(2);
		interest(this.bobToken, first).andExpect(status().isOk());
		interest(this.aliceToken, third).andExpect(status().isCreated());
	}

	@Test
	void messagingIsLimitedPerUserWithoutAffectingTheOtherParticipant() throws Exception {
		long conversation = conversationBetweenBobAndDave();
		send(this.bobToken, conversation, "Hello 1").andExpect(status().isCreated());
		send(this.bobToken, conversation, "Hello 2").andExpect(status().isCreated());

		assertTooManyRequests(send(this.bobToken, conversation, "Hello 3"));
		assertThat(messageCount(conversation)).isEqualTo(2);

		send(this.daveToken, conversation, "Reply").andExpect(status().isCreated());
	}

	@Test
	void reportingIsLimitedPerUser() throws Exception {
		report(this.bobToken, "USER", this.alice.getId()).andExpect(status().isCreated());
		report(this.bobToken, "USER", this.charlie.getId()).andExpect(status().isCreated());

		assertTooManyRequests(report(this.bobToken, "USER", this.dave.getId()));
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM reports", Long.class)).isEqualTo(2);

		report(this.aliceToken, "USER", this.dave.getId()).andExpect(status().isCreated());
	}

	@Test
	void publishingAListingThatIsNotADraftStays409AndDoesNotUseTheAllowance() throws Exception {
		long published = draftOf(this.alice, "Published");
		publish(this.aliceToken, published).andExpect(status().isOk());
		for (int i = 0; i < 3; i++) {
			publish(this.aliceToken, published).andExpect(status().isConflict());
		}
		publish(this.aliceToken, draftOf(this.alice, "Second")).andExpect(status().isOk());
		long third = draftOf(this.alice, "Third");

		assertTooManyRequests(publish(this.aliceToken, third));
		assertThat(this.jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, third))
			.isEqualTo("DRAFT");
		publish(this.aliceToken, published).andExpect(status().isConflict());
	}

	@Test
	void interestConflictsStay409AndDoNotUseTheAllowance() throws Exception {
		long bobsListing = publishedOf(this.bob, "Bob's");
		long alicesListing = publishedOf(this.alice, "Alice's");
		long rejected = idOf(interest(this.bobToken, alicesListing).andExpect(status().isCreated()));
		perform(post(CONNECTIONS + "/" + rejected + "/reject"), this.aliceToken).andExpect(status().isOk());
		for (int i = 0; i < 3; i++) {
			interest(this.bobToken, bobsListing).andExpect(status().isConflict());
			interest(this.bobToken, alicesListing).andExpect(status().isConflict());
		}
		interest(this.bobToken, publishedOf(this.dave, "Dave's")).andExpect(status().isCreated());

		assertTooManyRequests(interest(this.bobToken, publishedOf(this.charlie, "Charlie's")));
		interest(this.bobToken, bobsListing).andExpect(status().isConflict());
		interest(this.bobToken, alicesListing).andExpect(status().isConflict());
	}

	@Test
	void messagesOutsideAcceptedConnectionsStay409AndDoNotUseTheAllowance() throws Exception {
		long open = conversationBetweenBobAndDave();
		long closed = conversationBetweenBobAndDave();
		this.jdbc.update("UPDATE connections SET status = 'REJECTED' WHERE id = "
				+ "(SELECT connection_id FROM conversations WHERE id = ?)", closed);
		for (int i = 0; i < 3; i++) {
			send(this.bobToken, closed, "Blocked " + i).andExpect(status().isConflict());
		}
		send(this.bobToken, open, "Hello 1").andExpect(status().isCreated());
		send(this.bobToken, open, "Hello 2").andExpect(status().isCreated());

		assertTooManyRequests(send(this.bobToken, open, "Hello 3"));
		send(this.bobToken, closed, "Blocked").andExpect(status().isConflict());
		assertThat(messageCount(closed)).isZero();
	}

	@Test
	void duplicateAndSelfReportsStay409AndDoNotUseTheAllowance() throws Exception {
		report(this.bobToken, "USER", this.alice.getId()).andExpect(status().isCreated());
		for (int i = 0; i < 3; i++) {
			report(this.bobToken, "USER", this.alice.getId()).andExpect(status().isConflict());
			report(this.bobToken, "USER", this.bob.getId()).andExpect(status().isConflict());
		}
		report(this.bobToken, "USER", this.charlie.getId()).andExpect(status().isCreated());

		assertTooManyRequests(report(this.bobToken, "USER", this.dave.getId()));
		report(this.bobToken, "USER", this.alice.getId()).andExpect(status().isConflict());
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM reports", Long.class)).isEqualTo(2);
	}

	@Test
	void publicListingDiscoveryAndDetailAreNotLimited() throws Exception {
		long listing = publishedListing("Dave's listing");
		String slug = this.jdbc.queryForObject("SELECT slug FROM listings WHERE id = ?", String.class, listing);
		for (int i = 0; i < 25; i++) {
			this.mockMvc.perform(get(LISTINGS)).andExpect(status().isOk());
			this.mockMvc.perform(get(LISTINGS).param("search", "dave")).andExpect(status().isOk());
			this.mockMvc.perform(get(LISTINGS + "/" + slug)).andExpect(status().isOk());
		}
	}

	@Test
	void publicProfileLookupIsNotLimited() throws Exception {
		for (int i = 0; i < 25; i++) {
			this.mockMvc.perform(get("/api/v1/users/alice"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("alice"));
		}
	}

	@Test
	void suspendedUsersKeepGettingTheirNormal403() throws Exception {
		long conversation = conversationBetweenBobAndDave();
		long listing = publishedListing("Dave's other listing");
		long bobsDraft = draftOf(this.bob, "Bob's draft");
		this.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", this.bob.getId());

		for (int i = 0; i < 5; i++) {
			assertSuspended(createListing(this.bobToken, "Attempt " + i));
			assertSuspended(publish(this.bobToken, bobsDraft));
			assertSuspended(save(this.bobToken, listing));
			assertSuspended(interest(this.bobToken, listing));
			assertSuspended(send(this.bobToken, conversation, "Attempt " + i));
			assertSuspended(report(this.bobToken, "USER", this.alice.getId()));
		}
	}

	@Test
	void unauthenticatedWritesKeepGetting401() throws Exception {
		for (int i = 0; i < 5; i++) {
			this.mockMvc
				.perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON)
					.content(JSON.writeValueAsString(listingBody("Anonymous"))))
				.andExpect(status().isUnauthorized());
			this.mockMvc
				.perform(post(LISTINGS).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(JSON.writeValueAsString(listingBody("Forged"))))
				.andExpect(status().isUnauthorized());
		}
	}

	@Test
	void ownershipAndVisibilityFailuresAreUnchangedAndDoNotUseTheAllowance() throws Exception {
		long alicesDraft = idOf(createListing(this.aliceToken, "Alice's draft").andExpect(status().isCreated()));
		for (int i = 0; i < 5; i++) {
			publish(this.bobToken, alicesDraft).andExpect(status().isNotFound());
			save(this.bobToken, alicesDraft).andExpect(status().isNotFound());
			send(this.bobToken, Long.MAX_VALUE, "Nobody's conversation").andExpect(status().isNotFound());
		}

		long bobsDraft = idOf(createListing(this.bobToken, "Bob's draft").andExpect(status().isCreated()));
		publish(this.bobToken, bobsDraft).andExpect(status().isOk());
		save(this.bobToken, bobsDraft).andExpect(status().isNoContent());
	}

	@Test
	void adminAuthorizationIsUnchanged() throws Exception {
		for (int i = 0; i < 5; i++) {
			this.mockMvc.perform(get("/api/v1/admin/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + this.bobToken))
				.andExpect(status().isForbidden());
			this.mockMvc.perform(get("/api/v1/admin/reports")).andExpect(status().isUnauthorized());
		}
	}

	private static String nextIp() {
		int n = IP_COUNTER.incrementAndGet();
		return "10.99." + (n / 250) + "." + (n % 250 + 1);
	}

	private static MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder request, String ip) {
		return request.with(r -> {
			r.setRemoteAddr(ip);
			return r;
		});
	}

	private static ResultActions assertTooManyRequests(ResultActions result) throws Exception {
		MvcResult response = result.andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.title").value("Too Many Requests"))
			.andExpect(jsonPath("$.detail").value(TOO_MANY_REQUESTS))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
			.andReturn();
		long retryAfter = Long.parseLong(response.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
		assertThat(retryAfter).isBetween(1L, 600L);
		return result;
	}

	private static void assertSuspended(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value(SUSPENDED));
	}

	private MockHttpServletRequestBuilder loginRequest(String ip, String identifier, String password)
			throws Exception {
		return fromIp(post(LOGIN), ip).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("identifier", identifier, "password", password)));
	}

	private ResultActions login(String ip, String identifier, String password) throws Exception {
		return this.mockMvc.perform(loginRequest(ip, identifier, password));
	}

	private ResultActions register(String ip, String username) throws Exception {
		Map<String, Object> body = Map.of("email", username + "@example.com", "username", username, "password",
				PASSWORD, "displayName", "New User");
		return this.mockMvc.perform(fromIp(post(REGISTER), ip).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body)));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
		return this.mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions createListing(String token, String title) throws Exception {
		return perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(listingBody(title))), token);
	}

	private ResultActions publish(String token, long listingId) throws Exception {
		return perform(post(LISTINGS + "/" + listingId + "/publish"), token);
	}

	private ResultActions save(String token, long listingId) throws Exception {
		return perform(post(LISTINGS + "/" + listingId + "/save"), token);
	}

	private ResultActions interest(String token, long listingId) throws Exception {
		return perform(post(LISTINGS + "/" + listingId + "/interest"), token);
	}

	private ResultActions send(String token, long conversationId, String content) throws Exception {
		return perform(post(CONVERSATIONS + "/" + conversationId + "/messages").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("content", content))), token);
	}

	private ResultActions report(String token, String type, long targetId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("targetType", type);
		body.put("targetId", targetId);
		body.put("reason", "SPAM");
		body.put("details", "details");
		return perform(post(REPORTS).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)),
				token);
	}

	private long publishedListing(String title) throws Exception {
		long id = idOf(createListing(this.daveToken, title).andExpect(status().isCreated()));
		publish(this.daveToken, id).andExpect(status().isOk());
		return id;
	}

	private long draftOf(User owner, String title) {
		String slug = "draft-" + owner.getId() + "-" + IP_COUNTER.incrementAndGet();
		return this.listingRepository.save(new Listing(owner, title, slug, "A short pitch.", "A full description.",
				ListingAssetType.PROJECT, ListingMarketplaceMode.COLLABORATE, ListingCategory.SAAS,
				ListingStage.PROTOTYPE))
			.getId();
	}

	private long publishedOf(User owner, String title) {
		Listing listing = new Listing(owner, title, "published-" + owner.getId() + "-" + IP_COUNTER.incrementAndGet(),
				"A short pitch.", "A full description.", ListingAssetType.PROJECT, ListingMarketplaceMode.COLLABORATE,
				ListingCategory.SAAS, ListingStage.PROTOTYPE);
		listing.publish();
		return this.listingRepository.save(listing).getId();
	}

	private long conversationBetweenBobAndDave() throws Exception {
		long listing = publishedListing("Dave's listing");
		long connection = idOf(interest(this.bobToken, listing).andExpect(status().isCreated()));
		perform(post(CONNECTIONS + "/" + connection + "/accept"), this.daveToken).andExpect(status().isOk());
		return this.jdbc.queryForObject("SELECT id FROM conversations WHERE connection_id = ?", Long.class,
				connection);
	}

	private long listingCount(User owner) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE owner_id = ?", Long.class, owner.getId());
	}

	private long savedCount(User user) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM saved_listings WHERE user_id = ?", Long.class,
				user.getId());
	}

	private long connectionCount(User requester) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM connections WHERE requester_id = ?", Long.class,
				requester.getId());
	}

	private long messageCount(long conversationId) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE conversation_id = ?", Long.class,
				conversationId);
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
