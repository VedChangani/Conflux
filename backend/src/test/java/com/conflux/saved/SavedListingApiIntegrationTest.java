package com.conflux.saved;

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
import com.conflux.listing.ListingRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP behaviour of saved listings: full context, real security filter chain, Flyway-
 * migrated H2 database. Alice owns every fixture listing; Bob and Carol save them.
 * Suspension has no API yet, so SUSPENDED is set with SQL after a real publish.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SavedListingApiIntegrationTest {

	private static final String LISTINGS = "/api/v1/listings";

	private static final String SAVED = "/api/v1/saved-listings";

	private static final String NOT_FOUND = "Listing not found.";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ListingRepository listingRepository;

	@Autowired
	private SavedListingRepository savedListingRepository;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private JdbcTemplate jdbc;

	private User alice;

	private User bob;

	private User carol;

	private String aliceToken;

	private String bobToken;

	private String carolToken;

	private long p1;

	private long p2;

	private long p3;

	private long p4;

	private long draft;

	private long archived;

	private long suspended;

	@BeforeEach
	void setUp() throws Exception {
		cleanDatabase();
		this.alice = this.userRepository.save(new User("alice@example.com", "unused-hash", "alice", "Alice A"));
		this.bob = this.userRepository.save(new User("bob@example.com", "unused-hash", "bob", "Bob B"));
		this.carol = this.userRepository.save(new User("carol@example.com", "unused-hash", "carol", "Carol C"));
		this.aliceToken = this.tokenService.issueAccessToken(this.alice).accessToken();
		this.bobToken = this.tokenService.issueAccessToken(this.bob).accessToken();
		this.carolToken = this.tokenService.issueAccessToken(this.carol).accessToken();

		this.p1 = published("Published one");
		this.p2 = published("Published two");
		this.p3 = published("Published three");
		this.p4 = published("Published four");
		this.draft = create("Private draft");
		this.archived = published("Archived listing");
		this.mockMvc.perform(request(delete(LISTINGS + "/" + this.archived), this.aliceToken)).andExpect(status().isNoContent());
		this.suspended = published("Suspended listing");
		this.jdbc.update("UPDATE listings SET status = 'SUSPENDED' WHERE id = ?", this.suspended);
	}

	// Saves reference users and listings, so they must go first (other test classes delete those).
	@AfterEach
	void cleanDatabase() {
		this.savedListingRepository.deleteAllInBatch();
		this.listingRepository.deleteAllInBatch();
		this.userRepository.deleteAllInBatch();
	}

	// ---- Saving ------------------------------------------------------------------------

	@Test
	void authenticatedUserSavesAPublishedListingOnce() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent()).andExpect(content().string(""));

		assertThat(rows(this.bob, this.p1)).isEqualTo(1);
		assertThat(this.savedListingRepository.count()).isEqualTo(1);
		assertThat(savedIds(this.bobToken, "")).containsExactly(this.p1);
	}

	@Test
	void repeatedSaveIsIdempotent() throws Exception {
		for (int i = 0; i < 3; i++) {
			save(this.bobToken, this.p1).andExpect(status().isNoContent());
		}
		assertThat(rows(this.bob, this.p1)).isEqualTo(1);
		assertThat(savedIds(this.bobToken, "")).containsExactly(this.p1);
	}

	@Test
	void savingRequiresAuthentication() throws Exception {
		this.mockMvc.perform(post(LISTINGS + "/" + this.p1 + "/save"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(post(LISTINGS + "/" + this.p1 + "/save").header(HttpHeaders.AUTHORIZATION, "Bearer x.y.z"))
			.andExpect(status().isUnauthorized());
		assertThat(this.savedListingRepository.count()).isZero();
	}

	@Test
	void nonPublicListingsCannotBeSavedAndLookExactlyLikeMissingOnes() throws Exception {
		String missing = save(this.bobToken, 999_999_999L).andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(NOT_FOUND))
			.andReturn()
			.getResponse()
			.getContentAsString();

		for (long hidden : new long[] { this.draft, this.archived, this.suspended }) {
			for (String token : new String[] { this.bobToken, this.aliceToken }) {
				String response = save(token, hidden).andExpect(status().isNotFound())
					.andReturn()
					.getResponse()
					.getContentAsString();
				assertThat(response).isEqualTo(missing.replace("999999999", Long.toString(hidden)));
			}
		}
		assertThat(this.savedListingRepository.count()).isZero();
	}

	@Test
	void ownerMaySaveTheirOwnPublishedListing() throws Exception {
		save(this.aliceToken, this.p2).andExpect(status().isNoContent());

		assertThat(rows(this.alice, this.p2)).isEqualTo(1);
		assertThat(savedIds(this.aliceToken, "")).containsExactly(this.p2);
	}

	@Test
	void theSaverIsAlwaysTheTokenUserNeverRequestData() throws Exception {
		String body = JSON.writeValueAsString(Map.of("userId", this.carol.getId(), "username", "carol", "ownerId",
				this.alice.getId()));
		this.mockMvc
			.perform(request(post(LISTINGS + "/" + this.p1 + "/save").param("userId", this.carol.getId().toString()),
					this.bobToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isNoContent());

		assertThat(rows(this.bob, this.p1)).isEqualTo(1);
		assertThat(rows(this.carol, this.p1)).isZero();
		assertThat(rows(this.alice, this.p1)).isZero();
		assertThat(savedIds(this.carolToken, "")).isEmpty();
		// Query parameters cannot select another user's collection either.
		assertThat(savedIds(this.carolToken, "userId=" + this.bob.getId())).isEmpty();
	}

	@Test
	void suspendedAccountsCannotChangeTheirSavesButCanStillReadThem() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());
		this.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", this.bob.getId());

		save(this.bobToken, this.p2).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("This account is suspended."));
		unsave(this.bobToken, this.p1).andExpect(status().isForbidden());
		assertThat(savedIds(this.bobToken, "")).containsExactly(this.p1);
	}

	// ---- Unsaving ----------------------------------------------------------------------

	@Test
	void savedListingCanBeUnsavedAndRepeatedUnsaveIsIdempotent() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());

		unsave(this.bobToken, this.p1).andExpect(status().isNoContent()).andExpect(content().string(""));
		assertThat(rows(this.bob, this.p1)).isZero();
		assertThat(savedIds(this.bobToken, "")).isEmpty();

		unsave(this.bobToken, this.p1).andExpect(status().isNoContent());
		// Never saved, hidden and non-existent listings: nothing to remove, still 204.
		unsave(this.bobToken, this.p2).andExpect(status().isNoContent());
		unsave(this.bobToken, this.draft).andExpect(status().isNoContent());
		unsave(this.bobToken, 999_999_999L).andExpect(status().isNoContent());
	}

	@Test
	void unsavingRequiresAuthentication() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());

		this.mockMvc.perform(delete(LISTINGS + "/" + this.p1 + "/save"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		assertThat(rows(this.bob, this.p1)).isEqualTo(1);
	}

	@Test
	void anotherUserCannotRemoveSomeoneElsesSave() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());

		// Carol's unsave only ever affects Carol's own (non-existent) save.
		unsave(this.carolToken, this.p1).andExpect(status().isNoContent());
		unsave(this.aliceToken, this.p1).andExpect(status().isNoContent());

		assertThat(rows(this.bob, this.p1)).isEqualTo(1);
		assertThat(savedIds(this.bobToken, "")).containsExactly(this.p1);
	}

	@Test
	void savesOfNoLongerPublicListingsCanStillBeRemoved() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());
		this.mockMvc.perform(request(delete(LISTINGS + "/" + this.p1), this.aliceToken)).andExpect(status().isNoContent());

		unsave(this.bobToken, this.p1).andExpect(status().isNoContent());
		assertThat(rows(this.bob, this.p1)).isZero();
	}

	// ---- Saved-listings endpoint -------------------------------------------------------

	@Test
	void eachUserSeesOnlyTheirOwnSaves() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());
		save(this.bobToken, this.p2).andExpect(status().isNoContent());
		save(this.carolToken, this.p3).andExpect(status().isNoContent());
		save(this.carolToken, this.p1).andExpect(status().isNoContent());

		assertThat(savedIds(this.bobToken, "")).containsExactlyInAnyOrder(this.p1, this.p2);
		assertThat(savedIds(this.carolToken, "")).containsExactlyInAnyOrder(this.p3, this.p1);
		assertThat(savedIds(this.aliceToken, "")).isEmpty();
		this.mockMvc.perform(get(SAVED))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void onlyCurrentlyPublishedListingsAreShownButSavesAreKept() throws Exception {
		for (long id : new long[] { this.p1, this.p2, this.p3, this.p4 }) {
			save(this.bobToken, id).andExpect(status().isNoContent());
		}
		// P2 archived by its owner, P3 suspended by trust-and-safety, and a save of a draft
		// (impossible through the API, inserted directly to prove the query itself filters).
		this.mockMvc.perform(request(delete(LISTINGS + "/" + this.p2), this.aliceToken)).andExpect(status().isNoContent());
		this.jdbc.update("UPDATE listings SET status = 'SUSPENDED' WHERE id = ?", this.p3);
		this.jdbc.update("INSERT INTO saved_listings (user_id, listing_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
				this.bob.getId(), this.draft);

		assertThat(savedIds(this.bobToken, "")).containsExactlyInAnyOrder(this.p1, this.p4);
		saved(this.bobToken, "").andExpect(jsonPath("$.totalElements").value(2));
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM saved_listings WHERE user_id = ?", Long.class,
				this.bob.getId()))
			.isEqualTo(5);

		// If a listing becomes public again, the kept save is visible again.
		this.jdbc.update("UPDATE listings SET status = 'PUBLISHED' WHERE id = ?", this.p3);
		assertThat(savedIds(this.bobToken, "")).containsExactlyInAnyOrder(this.p1, this.p3, this.p4);
	}

	@Test
	void savesAreOrderedByCreatedAtDescendingThenIdDescendingAndPaginated() throws Exception {
		for (long id : new long[] { this.p1, this.p2, this.p3, this.p4 }) {
			save(this.bobToken, id).andExpect(status().isNoContent());
		}
		Instant base = Instant.parse("2026-06-01T08:00:00Z");
		setSavedAt(this.p1, base.plusSeconds(300));
		setSavedAt(this.p2, base.plusSeconds(100));
		setSavedAt(this.p3, base.plusSeconds(300)); // same as P1: the later save (higher id) first
		setSavedAt(this.p4, base.plusSeconds(200));

		assertThat(savedIds(this.bobToken, "")).containsExactly(this.p3, this.p1, this.p4, this.p2);

		saved(this.bobToken, "page=0&size=2").andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(4))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(false));
		assertThat(savedIds(this.bobToken, "page=0&size=2")).containsExactly(this.p3, this.p1);
		assertThat(savedIds(this.bobToken, "page=1&size=2")).containsExactly(this.p4, this.p2);
		saved(this.bobToken, "page=1&size=2").andExpect(jsonPath("$.last").value(true));
		saved(this.bobToken, "page=3&size=2").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
	}

	@Test
	void paginationDefaultsAndBounds() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());

		saved(this.bobToken, "").andExpect(jsonPath("$.size").value(12)).andExpect(jsonPath("$.page").value(0));
		saved(this.bobToken, "size=50").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(50));
		saved(this.bobToken, "page=10000").andExpect(status().isOk());
		for (String query : new String[] { "size=51", "size=0", "page=-1", "page=10001", "page=x", "size=y" }) {
			saved(this.bobToken, query).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Test
	void emptyCollectionIs200WithAnEmptyPage() throws Exception {
		saved(this.carolToken, "").andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray())
			.andExpect(jsonPath("$.content.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.totalPages").value(0))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(true));
	}

	// ---- Response ----------------------------------------------------------------------

	@Test
	void savedItemIsALightweightCardWithSavedAtAndNoPrivateData() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());
		LocalDateTime storedSavedAt = this.jdbc.queryForObject(
				"SELECT created_at FROM saved_listings WHERE user_id = ? AND listing_id = ?", LocalDateTime.class,
				this.bob.getId(), this.p1);

		String response = saved(this.bobToken, "").andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(this.p1))
			.andExpect(jsonPath("$.content[0].slug").exists())
			.andExpect(jsonPath("$.content[0].title").value("Published one"))
			.andExpect(jsonPath("$.content[0].shortPitch").value("A short pitch."))
			.andExpect(jsonPath("$.content[0].assetType").value("STARTUP"))
			.andExpect(jsonPath("$.content[0].marketplaceMode").value("ACQUIRE"))
			.andExpect(jsonPath("$.content[0].category").value("SAAS"))
			.andExpect(jsonPath("$.content[0].stage").value("REVENUE"))
			.andExpect(jsonPath("$.content[0].askingPrice").value(1000))
			.andExpect(jsonPath("$.content[0].currency").value("EUR"))
			.andExpect(jsonPath("$.content[0].priceNegotiable").value(true))
			.andExpect(jsonPath("$.content[0].publishedAt").exists())
			.andExpect(jsonPath("$.content[0].owner.username").value("alice"))
			.andExpect(jsonPath("$.content[0].owner.displayName").value("Alice A"))
			.andExpect(jsonPath("$.content[0].owner.id").doesNotExist())
			.andExpect(jsonPath("$.content[0].owner.email").doesNotExist())
			.andExpect(jsonPath("$.content[0].description").doesNotExist())
			.andExpect(jsonPath("$.content[0].problem").doesNotExist())
			.andExpect(jsonPath("$.content[0].solution").doesNotExist())
			.andExpect(jsonPath("$.content[0].collaborationDetails").doesNotExist())
			.andExpect(jsonPath("$.content[0].status").doesNotExist())
			.andExpect(jsonPath("$.content[0].user").doesNotExist())
			.andExpect(jsonPath("$.content[0].listing").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();

		Instant savedAt = Instant.parse(JsonPath.read(response, "$.content[0].savedAt"));
		assertThat(LocalDateTime.ofInstant(savedAt, ZoneOffset.UTC)).isEqualTo(storedSavedAt);
		assertThat(response).doesNotContain("@example.com")
			.doesNotContain("passwordHash")
			.doesNotContain("unused-hash")
			.doesNotContain("A full private description.")
			.doesNotContain("Looking for a growth partner.");
	}

	// ---- Integrity ---------------------------------------------------------------------

	@Test
	void concurrentSavesOfTheSameListingCreateExactlyOneRow() throws Exception {
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				Callable<Integer> call = () -> {
					start.await();
					return save(this.bobToken, this.p1).andReturn().getResponse().getStatus();
				};
				results.add(executor.submit(call));
			}
			start.countDown();
			for (Future<Integer> result : results) {
				assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(204);
			}
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(rows(this.bob, this.p1)).isEqualTo(1);
	}

	@Test
	void databaseRejectsADuplicatePairEvenBypassingTheApplication() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());

		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> this.jdbc.update(
					"INSERT INTO saved_listings (user_id, listing_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
					this.bob.getId(), this.p1));
		assertThat(rows(this.bob, this.p1)).isEqualTo(1);
	}

	// ---- Regression --------------------------------------------------------------------

	@Test
	void marketplaceAndListingManagementAreUnaffectedBySaves() throws Exception {
		save(this.bobToken, this.p1).andExpect(status().isNoContent());
		save(this.carolToken, this.p1).andExpect(status().isNoContent());

		// Public discovery and detail: unchanged, and no per-user saved state.
		this.mockMvc.perform(get(LISTINGS)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
		String slug = this.jdbc.queryForObject("SELECT slug FROM listings WHERE id = ?", String.class, this.p1);
		this.mockMvc.perform(get(LISTINGS + "/" + slug))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSaved").doesNotExist())
			.andExpect(jsonPath("$.saved").doesNotExist());

		// The owner edits a saved listing: the saved card shows the new content.
		Map<String, Object> edit = listingBody("Published one, edited");
		this.mockMvc
			.perform(request(put(LISTINGS + "/" + this.p1), this.aliceToken).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(edit)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		saved(this.bobToken, "").andExpect(jsonPath("$.content[0].title").value("Published one, edited"));

		// The owner archives it: gone from the saved page and the marketplace; saves are kept.
		this.mockMvc.perform(request(delete(LISTINGS + "/" + this.p1), this.aliceToken)).andExpect(status().isNoContent());
		assertThat(savedIds(this.bobToken, "")).isEmpty();
		this.mockMvc.perform(get(LISTINGS)).andExpect(jsonPath("$.totalElements").value(3));
		assertThat(this.savedListingRepository.count()).isEqualTo(2);

		// The owner's dashboard still works.
		this.mockMvc.perform(get(LISTINGS + "/mine").header(HttpHeaders.AUTHORIZATION, "Bearer " + this.aliceToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(7));
	}

	// ---- Helpers ------------------------------------------------------------------------

	private ResultActions save(String token, long listingId) throws Exception {
		return this.mockMvc.perform(request(post(LISTINGS + "/" + listingId + "/save"), token));
	}

	private ResultActions unsave(String token, long listingId) throws Exception {
		return this.mockMvc.perform(request(delete(LISTINGS + "/" + listingId + "/save"), token));
	}

	private ResultActions saved(String token, String query) throws Exception {
		MockHttpServletRequestBuilder request = request(get(SAVED), token);
		if (!query.isEmpty()) {
			for (String pair : query.split("&")) {
				String[] parts = pair.split("=", 2);
				request.param(parts[0], parts[1]);
			}
		}
		return this.mockMvc.perform(request);
	}

	private List<Long> savedIds(String token, String query) throws Exception {
		String response = saved(token, query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(response, "$.content[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private static MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private int rows(User user, long listingId) {
		Integer count = this.jdbc.queryForObject(
				"SELECT COUNT(*) FROM saved_listings WHERE user_id = ? AND listing_id = ?", Integer.class,
				user.getId(), listingId);
		return (count != null) ? count : 0;
	}

	private void setSavedAt(long listingId, Instant savedAt) {
		this.jdbc.update("UPDATE saved_listings SET created_at = ? WHERE user_id = ? AND listing_id = ?",
				LocalDateTime.ofInstant(savedAt, ZoneOffset.UTC), this.bob.getId(), listingId);
	}

	private long published(String title) throws Exception {
		long id = create(title);
		this.mockMvc.perform(request(post(LISTINGS + "/" + id + "/publish"), this.aliceToken))
			.andExpect(status().isOk());
		return id;
	}

	private long create(String title) throws Exception {
		String response = this.mockMvc
			.perform(request(post(LISTINGS), this.aliceToken).contentType(MediaType.APPLICATION_JSON)
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
		body.put("description", "A full private description.");
		body.put("problem", "A problem.");
		body.put("solution", "A solution.");
		body.put("assetType", "STARTUP");
		body.put("marketplaceMode", "ACQUIRE");
		body.put("category", "SAAS");
		body.put("stage", "REVENUE");
		body.put("askingPrice", 1000);
		body.put("currency", "EUR");
		body.put("priceNegotiable", true);
		body.put("collaborationDetails", "Looking for a growth partner.");
		return body;
	}

}
