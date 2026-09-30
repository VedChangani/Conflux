package com.conflux.listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.conflux.auth.JwtTokenService;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserStatus;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP behaviour of the listing API: full context, real security filter chain, Flyway-
 * migrated H2 database. Moderation outcomes (PUBLISHED, REJECTED, SUSPENDED) have no API
 * yet, so tests set them with SQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListingApiIntegrationTest {

	private static final String LISTINGS = "/api/v1/listings";

	private static final String MINE = LISTINGS + "/mine";

	private static final String URL_SAFE_SLUG = "[a-z0-9]+(?:-[a-z0-9]+)*";

	private static final String NOT_FOUND = "Listing not found.";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ListingRepository listingRepository;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private JdbcTemplate jdbc;

	private User alice;

	private User bob;

	private String aliceToken;

	private String bobToken;

	@BeforeEach
	void setUp() {
		cleanDatabase();
		this.alice = this.userRepository.save(new User("alice@example.com", "unused-hash", "alice", "Alice A"));
		this.bob = this.userRepository.save(new User("bob@example.com", "unused-hash", "bob", "Bob B"));
		this.aliceToken = this.tokenService.issueAccessToken(this.alice).accessToken();
		this.bobToken = this.tokenService.issueAccessToken(this.bob).accessToken();
	}

	// Committed rows would break other test classes that delete users (listings reference users).
	@AfterEach
	void cleanDatabase() {
		this.listingRepository.deleteAllInBatch();
		this.userRepository.deleteAllInBatch();
	}

	// ---- Creation ----------------------------------------------------------------------

	@Test
	void authenticatedUserCreatesDraftListingOwnedByThemWithServerGeneratedSlug() throws Exception {
		create(this.aliceToken, body())
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.publishedAt").value(nullValue()))
			.andExpect(jsonPath("$.createdAt").value(notNullValue()))
			.andExpect(jsonPath("$.updatedAt").value(notNullValue()))
			.andExpect(jsonPath("$.slug").value(matchesPattern("ai-invoice-reconciliation-[0-9a-f]{8}")))
			.andExpect(jsonPath("$.slug").value(matchesPattern(URL_SAFE_SLUG)))
			.andExpect(jsonPath("$.title").value("AI Invoice Reconciliation"))
			.andExpect(jsonPath("$.assetType").value("MVP"))
			.andExpect(jsonPath("$.marketplaceMode").value("ACQUIRE"))
			.andExpect(jsonPath("$.category").value("FINTECH"))
			.andExpect(jsonPath("$.stage").value("PROTOTYPE"))
			.andExpect(jsonPath("$.askingPrice").value(25000.5))
			.andExpect(jsonPath("$.currency").value("USD"))
			.andExpect(jsonPath("$.priceNegotiable").value(true))
			.andExpect(jsonPath("$.owner.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.owner.username").value("alice"))
			.andExpect(jsonPath("$.owner.displayName").value("Alice A"))
			.andExpect(jsonPath("$.owner.email").doesNotExist())
			.andExpect(jsonPath("$.owner.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.owner.role").doesNotExist());

		assertThat(this.jdbc.queryForObject("SELECT owner_id FROM listings", Long.class)).isEqualTo(this.alice.getId());
		assertThat(this.jdbc.queryForObject("SELECT status FROM listings", String.class)).isEqualTo("DRAFT");
		assertThat(this.jdbc.queryForObject("SELECT published_at FROM listings", Object.class)).isNull();
	}

	@Test
	void clientCannotChooseOwnerStatusSlugOrTimestamps() throws Exception {
		Map<String, Object> body = body();
		body.put("ownerId", this.bob.getId());
		body.put("owner", Map.of("id", this.bob.getId()));
		body.put("status", "PUBLISHED");
		body.put("slug", "chosen-by-client");
		body.put("publishedAt", "2020-01-01T00:00:00Z");
		body.put("createdAt", "2020-01-01T00:00:00Z");
		body.put("updatedAt", "2020-01-01T00:00:00Z");

		create(this.aliceToken, body)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.owner.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.slug").value(not("chosen-by-client")))
			.andExpect(jsonPath("$.slug").value(matchesPattern("ai-invoice-reconciliation-[0-9a-f]{8}")))
			.andExpect(jsonPath("$.publishedAt").value(nullValue()))
			.andExpect(jsonPath("$.createdAt").value(not("2020-01-01T00:00:00Z")));

		assertThat(this.jdbc.queryForObject("SELECT owner_id FROM listings", Long.class)).isEqualTo(this.alice.getId());
		assertThat(this.jdbc.queryForObject("SELECT status FROM listings", String.class)).isEqualTo("DRAFT");
	}

	@Test
	void sameTitleTwiceGetsDifferentSlugs() throws Exception {
		String first = slugOf(create(this.aliceToken, body()).andExpect(status().isCreated()));
		String second = slugOf(create(this.bobToken, body()).andExpect(status().isCreated()));

		assertThat(first).isNotEqualTo(second);
		assertThat(first).startsWith("ai-invoice-reconciliation-");
		assertThat(second).startsWith("ai-invoice-reconciliation-");
	}

	@Test
	void requestIsNormalizedWithoutInventingValues() throws Exception {
		Map<String, Object> body = body();
		body.put("title", "  Résumé   Builder!  ");
		body.put("shortPitch", "  Pitch  ");
		body.put("problem", "   ");
		body.put("solution", "");
		body.put("collaborationDetails", " \n ");
		body.remove("askingPrice");
		body.remove("currency");
		body.remove("priceNegotiable");

		create(this.aliceToken, body)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Résumé   Builder!"))
			.andExpect(jsonPath("$.shortPitch").value("Pitch"))
			.andExpect(jsonPath("$.slug").value(matchesPattern("resume-builder-[0-9a-f]{8}")))
			.andExpect(jsonPath("$.problem").value(nullValue()))
			.andExpect(jsonPath("$.solution").value(nullValue()))
			.andExpect(jsonPath("$.collaborationDetails").value(nullValue()))
			.andExpect(jsonPath("$.askingPrice").value(nullValue()))
			.andExpect(jsonPath("$.currency").value(nullValue()))
			.andExpect(jsonPath("$.priceNegotiable").value(false));
	}

	@Test
	void textFieldsAtTheirMaximumLengthsAreAccepted() throws Exception {
		Map<String, Object> body = body();
		body.put("title", "t".repeat(120));
		body.put("shortPitch", "p".repeat(240));
		body.put("description", "d".repeat(10_000));
		body.put("problem", "p".repeat(10_000));
		body.put("solution", "s".repeat(10_000));
		body.put("collaborationDetails", "c".repeat(5_000));

		create(this.aliceToken, body).andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value(matchesPattern(URL_SAFE_SLUG)));
	}

	// ---- Validation --------------------------------------------------------------------

	@Test
	void invalidRequestsAreRejectedWith400AndNothingIsCreated() throws Exception {
		assertRejected(b -> b.put("title", "   "), "title");
		assertRejected(b -> b.remove("title"), "title");
		assertRejected(b -> b.put("title", "t".repeat(121)), "title");
		assertRejected(b -> b.put("shortPitch", " "), "shortPitch");
		assertRejected(b -> b.put("shortPitch", "p".repeat(241)), "shortPitch");
		assertRejected(b -> b.put("description", ""), "description");
		assertRejected(b -> b.put("description", "d".repeat(10_001)), "description");
		assertRejected(b -> b.put("problem", "p".repeat(10_001)), "problem");
		assertRejected(b -> b.put("solution", "s".repeat(10_001)), "solution");
		assertRejected(b -> b.put("collaborationDetails", "c".repeat(5_001)), "collaborationDetails");
		assertRejected(b -> b.remove("assetType"), "assetType");
		assertRejected(b -> b.remove("marketplaceMode"), "marketplaceMode");
		assertRejected(b -> b.remove("category"), "category");
		assertRejected(b -> b.remove("stage"), "stage");
		assertThat(this.listingRepository.count()).isZero();
	}

	@Test
	void priceRulesAreEnforced() throws Exception {
		assertRejected(b -> b.put("askingPrice", new BigDecimal("-1")), "askingPrice");
		assertRejected(b -> b.put("askingPrice", new BigDecimal("10.999")), "askingPrice");
		assertRejected(b -> b.remove("currency"), "currency");
		assertRejected(b -> b.remove("askingPrice"), "currency");
		for (String currency : new String[] { "usd", "US", "USDX", "12A", "" }) {
			assertRejected(b -> b.put("currency", currency), "currency");
		}
		assertThat(this.listingRepository.count()).isZero();

		Map<String, Object> free = body();
		free.put("askingPrice", BigDecimal.ZERO);
		create(this.aliceToken, free).andExpect(status().isCreated()).andExpect(jsonPath("$.askingPrice").value(0));
	}

	@Test
	void unknownEnumValueOrMalformedJsonIs400() throws Exception {
		Map<String, Object> body = body();
		body.put("category", "BANANA");
		create(this.aliceToken, body).andExpect(status().isBadRequest());

		this.mockMvc.perform(post(LISTINGS).header(HttpHeaders.AUTHORIZATION, bearer(this.aliceToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ not json"))
			.andExpect(status().isBadRequest());
		assertThat(this.listingRepository.count()).isZero();
	}

	// ---- Public browsing ---------------------------------------------------------------

	@Test
	void onlyPublishedListingsAppearInPublicBrowsingAsLightweightCards() throws Exception {
		long published = createListing(this.aliceToken, "Published one");
		createListing(this.aliceToken, "Draft one");
		publish(published, Instant.now());

		this.mockMvc.perform(get(LISTINGS))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(published))
			.andExpect(jsonPath("$.content[0].title").value("Published one"))
			.andExpect(jsonPath("$.content[0].slug").value(matchesPattern("published-one-[0-9a-f]{8}")))
			.andExpect(jsonPath("$.content[0].shortPitch").exists())
			.andExpect(jsonPath("$.content[0].assetType").value("MVP"))
			.andExpect(jsonPath("$.content[0].marketplaceMode").value("ACQUIRE"))
			.andExpect(jsonPath("$.content[0].category").value("FINTECH"))
			.andExpect(jsonPath("$.content[0].stage").value("PROTOTYPE"))
			.andExpect(jsonPath("$.content[0].askingPrice").value(25000.5))
			.andExpect(jsonPath("$.content[0].currency").value("USD"))
			.andExpect(jsonPath("$.content[0].priceNegotiable").value(true))
			.andExpect(jsonPath("$.content[0].publishedAt").value(notNullValue()))
			.andExpect(jsonPath("$.content[0].owner.username").value("alice"))
			.andExpect(jsonPath("$.content[0].owner.displayName").value("Alice A"))
			.andExpect(jsonPath("$.content[0].owner.email").doesNotExist())
			.andExpect(jsonPath("$.content[0].description").doesNotExist())
			.andExpect(jsonPath("$.content[0].problem").doesNotExist())
			.andExpect(jsonPath("$.content[0].solution").doesNotExist())
			.andExpect(jsonPath("$.content[0].collaborationDetails").doesNotExist())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(12));
	}

	@Test
	void publicDetailReturnsPublishedListingWithSafeOwnerSummary() throws Exception {
		long id = createListing(this.aliceToken, "Detail listing");
		publish(id, Instant.parse("2026-01-15T10:00:00Z"));
		String slug = slugOfListing(id);

		this.mockMvc.perform(get(LISTINGS + "/" + slug))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.slug").value(slug))
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.publishedAt").value("2026-01-15T10:00:00Z"))
			.andExpect(jsonPath("$.description").value("Match invoices to payments automatically."))
			.andExpect(jsonPath("$.problem").value("Manual reconciliation takes days."))
			.andExpect(jsonPath("$.solution").value("An AI matching engine."))
			.andExpect(jsonPath("$.collaborationDetails").value("Looking for a technical cofounder."))
			.andExpect(jsonPath("$.owner.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.owner.username").value("alice"))
			.andExpect(jsonPath("$.owner.displayName").value("Alice A"))
			.andExpect(content().string(not(containsString("alice@example.com"))))
			.andExpect(content().string(not(containsString("passwordHash"))))
			.andExpect(content().string(not(containsString("unused-hash"))))
			.andExpect(content().string(not(containsString("\"role\""))));
	}

	@Test
	void publicDetailDoesNotExposeUnpublishedListings() throws Exception {
		String missing = this.mockMvc.perform(get(LISTINGS + "/does-not-exist-00000000"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value(NOT_FOUND))
			.andReturn()
			.getResponse()
			.getContentAsString();

		for (ListingStatus status : new ListingStatus[] { ListingStatus.DRAFT, ListingStatus.PENDING_REVIEW,
				ListingStatus.REJECTED, ListingStatus.ARCHIVED, ListingStatus.SUSPENDED }) {
			long id = createListing(this.aliceToken, "Hidden " + status);
			setStatus(id, status);
			String slug = slugOfListing(id);

			String response = this.mockMvc.perform(get(LISTINGS + "/" + slug))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(response).as(status.name()).isEqualTo(missing.replace("does-not-exist-00000000", slug));
			assertThat(response).doesNotContain("Hidden");
		}
		this.mockMvc.perform(get(LISTINGS)).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void publicBrowsingIsPaginatedAndOrderedByPublishedAtDescending() throws Exception {
		Instant base = Instant.parse("2026-03-01T00:00:00Z");
		// Created in an order different from publication order.
		int[] dayOffsets = { 2, 0, 4, 1, 3 };
		for (int i = 0; i < dayOffsets.length; i++) {
			long id = createListing(this.aliceToken, "Listing day " + dayOffsets[i]);
			publish(id, base.plus(dayOffsets[i], ChronoUnit.DAYS));
		}

		this.mockMvc.perform(get(LISTINGS).param("page", "0").param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].title").value("Listing day 4"))
			.andExpect(jsonPath("$.content[1].title").value("Listing day 3"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(5))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(false));
		this.mockMvc.perform(get(LISTINGS).param("page", "1").param("size", "2"))
			.andExpect(jsonPath("$.content[0].title").value("Listing day 2"))
			.andExpect(jsonPath("$.content[1].title").value("Listing day 1"));
		this.mockMvc.perform(get(LISTINGS).param("page", "2").param("size", "2"))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].title").value("Listing day 0"))
			.andExpect(jsonPath("$.last").value(true));
	}

	@Test
	void paginationParametersAreBounded() throws Exception {
		for (String[] params : new String[][] { { "page", "-1" }, { "size", "0" }, { "size", "51" },
				{ "page", "10001" }, { "page", "abc" } }) {
			this.mockMvc.perform(get(LISTINGS).param(params[0], params[1]))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
			this.mockMvc.perform(get(MINE).param(params[0], params[1]).header(HttpHeaders.AUTHORIZATION,
					bearer(this.aliceToken)))
				.andExpect(status().isBadRequest());
		}
		this.mockMvc.perform(get(LISTINGS).param("size", "50")).andExpect(status().isOk());
	}

	// ---- Ownership ---------------------------------------------------------------------

	@Test
	void ownerCanReadOwnPrivateListing() throws Exception {
		long id = createListing(this.aliceToken, "Private draft");

		this.mockMvc.perform(get(MINE + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(this.aliceToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.description").value("Match invoices to payments automatically."))
			.andExpect(jsonPath("$.owner.username").value("alice"));
	}

	@Test
	void anotherUserCannotReadEditSubmitOrArchiveAListingAndCannotTellItExists() throws Exception {
		long id = createListing(this.aliceToken, "Alice only");
		String missing = this.mockMvc.perform(
				get(MINE + "/999999999").header(HttpHeaders.AUTHORIZATION, bearer(this.bobToken)))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String read = this.mockMvc.perform(get(MINE + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(this.bobToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value(NOT_FOUND))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(read).isEqualTo(missing.replace("999999999", Long.toString(id)));

		Map<String, Object> hijack = body();
		hijack.put("title", "Hijacked");
		this.mockMvc.perform(withJson(put(LISTINGS + "/" + id), this.bobToken, hijack))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value(NOT_FOUND));
		this.mockMvc.perform(post(LISTINGS + "/" + id + "/submit").header(HttpHeaders.AUTHORIZATION, bearer(this.bobToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value(NOT_FOUND));
		this.mockMvc.perform(delete(LISTINGS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(this.bobToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value(NOT_FOUND));

		assertThat(this.jdbc.queryForObject("SELECT title FROM listings WHERE id = ?", String.class, id))
			.isEqualTo("Alice only");
		assertThat(this.jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, id))
			.isEqualTo("DRAFT");
	}

	@Test
	void mineReturnsOnlyTheCallersListingsInAllStatusesOrderedByUpdatedAt() throws Exception {
		long older = createListing(this.aliceToken, "Alice older");
		long archived = createListing(this.aliceToken, "Alice archived");
		long newer = createListing(this.aliceToken, "Alice newer");
		createListing(this.bobToken, "Bob listing");
		setStatus(archived, ListingStatus.ARCHIVED);
		setUpdatedAt(older, Instant.parse("2026-01-01T00:00:00Z"));
		setUpdatedAt(archived, Instant.parse("2026-01-02T00:00:00Z"));
		setUpdatedAt(newer, Instant.parse("2026-01-03T00:00:00Z"));

		this.mockMvc.perform(get(MINE).header(HttpHeaders.AUTHORIZATION, bearer(this.aliceToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].title").value("Alice newer"))
			.andExpect(jsonPath("$.content[1].title").value("Alice archived"))
			.andExpect(jsonPath("$.content[1].status").value("ARCHIVED"))
			.andExpect(jsonPath("$.content[2].title").value("Alice older"))
			.andExpect(jsonPath("$.content[0].updatedAt").value("2026-01-03T00:00:00Z"))
			.andExpect(jsonPath("$.content[0].description").doesNotExist())
			.andExpect(jsonPath("$.content[0].owner").doesNotExist());

		this.mockMvc.perform(get(MINE).header(HttpHeaders.AUTHORIZATION, bearer(this.bobToken)))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].title").value("Bob listing"));

		// An ownerId query parameter is not a feature: it is ignored.
		this.mockMvc.perform(get(MINE).param("ownerId", this.alice.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(this.bobToken)))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].title").value("Bob listing"));
	}

	// ---- Lifecycle ---------------------------------------------------------------------

	@Test
	void draftAndRejectedListingsCanBeSubmittedForReview() throws Exception {
		long draft = createListing(this.aliceToken, "Draft");
		long rejected = createListing(this.aliceToken, "Rejected");
		setStatus(rejected, ListingStatus.REJECTED);

		for (long id : new long[] { draft, rejected }) {
			submit(this.aliceToken, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
				.andExpect(jsonPath("$.publishedAt").value(nullValue()));
			assertThat(statusOf(id)).isEqualTo("PENDING_REVIEW");
		}
		// Submitting does not publish.
		this.mockMvc.perform(get(LISTINGS)).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void invalidSubmitTransitionsAreConflicts() throws Exception {
		for (ListingStatus status : new ListingStatus[] { ListingStatus.PENDING_REVIEW, ListingStatus.PUBLISHED,
				ListingStatus.ARCHIVED, ListingStatus.SUSPENDED }) {
			long id = createListing(this.aliceToken, "Submit " + status);
			setStatus(id, status);

			submit(this.aliceToken, id).andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail")
					.value("A listing with status " + status + " cannot be submitted for review."));
			assertThat(statusOf(id)).isEqualTo(status.name());
		}
	}

	@Test
	void editingDraftOrRejectedKeepsTheStatusAndReplacesContent() throws Exception {
		long draft = createListing(this.aliceToken, "Draft");
		long rejected = createListing(this.aliceToken, "Rejected");
		setStatus(rejected, ListingStatus.REJECTED);
		String draftSlug = slugOfListing(draft);

		Map<String, Object> edit = body();
		edit.put("title", "Edited title");
		edit.remove("askingPrice");
		edit.remove("currency");
		edit.put("stage", "LIVE");

		update(this.aliceToken, draft, edit).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.title").value("Edited title"))
			.andExpect(jsonPath("$.stage").value("LIVE"))
			.andExpect(jsonPath("$.askingPrice").value(nullValue()))
			.andExpect(jsonPath("$.currency").value(nullValue()))
			.andExpect(jsonPath("$.slug").value(draftSlug));
		update(this.aliceToken, rejected, edit).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));
		assertThat(statusOf(rejected)).isEqualTo("REJECTED");
	}

	@Test
	void editingAPublishedListingSendsItBackToReviewAndHidesIt() throws Exception {
		long id = createListing(this.aliceToken, "Live listing");
		publish(id, Instant.parse("2026-02-01T00:00:00Z"));
		String slug = slugOfListing(id);
		this.mockMvc.perform(get(LISTINGS + "/" + slug)).andExpect(status().isOk());

		Map<String, Object> edit = body();
		edit.put("title", "Live listing, edited");
		update(this.aliceToken, id, edit).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.publishedAt").value(nullValue()))
			.andExpect(jsonPath("$.slug").value(slug));

		assertThat(statusOf(id)).isEqualTo("PENDING_REVIEW");
		assertThat(this.jdbc.queryForObject("SELECT published_at FROM listings WHERE id = ?", Object.class, id)).isNull();
		this.mockMvc.perform(get(LISTINGS + "/" + slug)).andExpect(status().isNotFound());
		this.mockMvc.perform(get(LISTINGS)).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void editingIsRefusedWhilePendingArchivedOrSuspended() throws Exception {
		for (ListingStatus status : new ListingStatus[] { ListingStatus.PENDING_REVIEW, ListingStatus.ARCHIVED,
				ListingStatus.SUSPENDED }) {
			long id = createListing(this.aliceToken, "Locked " + status);
			setStatus(id, status);
			Map<String, Object> edit = body();
			edit.put("title", "Changed");

			update(this.aliceToken, id, edit).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("A listing with status " + status + " cannot be edited."));
			assertThat(this.jdbc.queryForObject("SELECT title FROM listings WHERE id = ?", String.class, id))
				.isEqualTo("Locked " + status);
		}
	}

	@Test
	void archivingAPublishedListingHidesItButKeepsTheRowAndPublicationDate() throws Exception {
		long id = createListing(this.aliceToken, "To archive");
		publish(id, Instant.parse("2026-02-01T00:00:00Z"));
		String slug = slugOfListing(id);

		archive(this.aliceToken, id).andExpect(status().isNoContent());

		this.mockMvc.perform(get(LISTINGS + "/" + slug)).andExpect(status().isNotFound());
		this.mockMvc.perform(get(LISTINGS)).andExpect(jsonPath("$.totalElements").value(0));
		assertThat(this.listingRepository.existsById(id)).isTrue();
		assertThat(statusOf(id)).isEqualTo("ARCHIVED");
		assertThat(this.jdbc.queryForObject("SELECT published_at FROM listings WHERE id = ?", LocalDateTime.class, id))
			.isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
		// Still visible to the owner.
		this.mockMvc.perform(get(MINE + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(this.aliceToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));
	}

	@Test
	void archiveRulesPerStatus() throws Exception {
		for (ListingStatus status : new ListingStatus[] { ListingStatus.DRAFT, ListingStatus.REJECTED,
				ListingStatus.PENDING_REVIEW, ListingStatus.ARCHIVED }) {
			long id = createListing(this.aliceToken, "Archive " + status);
			setStatus(id, status);
			archive(this.aliceToken, id).andExpect(status().isNoContent());
			assertThat(statusOf(id)).isEqualTo("ARCHIVED");
		}
		long suspended = createListing(this.aliceToken, "Suspended");
		setStatus(suspended, ListingStatus.SUSPENDED);
		archive(this.aliceToken, suspended).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("A listing with status SUSPENDED cannot be archived."));
		assertThat(statusOf(suspended)).isEqualTo("SUSPENDED");
		assertThat(this.listingRepository.count()).isEqualTo(5);
	}

	// ---- Security ----------------------------------------------------------------------

	@Test
	void protectedListingEndpointsRequireAuthentication() throws Exception {
		long id = createListing(this.aliceToken, "Protected");
		String json = JSON.writeValueAsString(body());

		for (MockHttpServletRequestBuilder request : List.of(
				post(LISTINGS).contentType(MediaType.APPLICATION_JSON).content(json), get(MINE), get(MINE + "/" + id),
				put(LISTINGS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json),
				post(LISTINGS + "/" + id + "/submit"), delete(LISTINGS + "/" + id))) {
			this.mockMvc.perform(request)
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		assertThat(this.listingRepository.count()).isEqualTo(1);
		assertThat(statusOf(id)).isEqualTo("DRAFT");
	}

	@Test
	void suspendedUserCanReadOwnListingsButCannotChangeAnything() throws Exception {
		long id = createListing(this.aliceToken, "Before suspension");
		this.jdbc.update("UPDATE users SET status = ? WHERE id = ?", UserStatus.SUSPENDED.name(), this.alice.getId());

		create(this.aliceToken, body()).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("This account is suspended."));
		update(this.aliceToken, id, body()).andExpect(status().isForbidden());
		submit(this.aliceToken, id).andExpect(status().isForbidden());
		archive(this.aliceToken, id).andExpect(status().isForbidden());
		this.mockMvc.perform(get(MINE).header(HttpHeaders.AUTHORIZATION, bearer(this.aliceToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		assertThat(statusOf(id)).isEqualTo("DRAFT");
	}

	@Test
	void healthAndAuthEndpointsStillWork() throws Exception {
		this.mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));

		String register = JSON.writeValueAsString(Map.of("email", "carol@example.com", "username", "carol",
				"password", "correct-horse-battery", "displayName", "Carol"));
		this.mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
			.andExpect(status().isCreated());
		String login = JSON.writeValueAsString(Map.of("identifier", "carol", "password", "correct-horse-battery"));
		String token = JsonPath.read(this.mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.accessToken");

		create(token, body()).andExpect(status().isCreated()).andExpect(jsonPath("$.owner.username").value("carol"));
		this.mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("carol"));
	}

	@Test
	void timestampsAreStoredAsUtcWallClockTimeWhateverTheJvmTimeZone() throws Exception {
		String response = create(this.aliceToken, body()).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Instant createdAt = Instant.parse(JsonPath.read(response, "$.createdAt"));

		LocalDateTime storedListing = this.jdbc.queryForObject("SELECT created_at FROM listings", LocalDateTime.class);
		LocalDateTime storedUser = this.jdbc.queryForObject("SELECT created_at FROM users WHERE id = ?",
				LocalDateTime.class, this.alice.getId());
		assertThat(storedListing).isEqualTo(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC));
		assertThat(storedUser)
			.isEqualTo(LocalDateTime.ofInstant(this.userRepository.findById(this.alice.getId()).orElseThrow().getCreatedAt(),
					ZoneOffset.UTC));
	}

	// ---- Helpers ------------------------------------------------------------------------

	private static Map<String, Object> body() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", "AI Invoice Reconciliation");
		body.put("shortPitch", "Reconcile invoices in minutes, not days.");
		body.put("description", "Match invoices to payments automatically.");
		body.put("problem", "Manual reconciliation takes days.");
		body.put("solution", "An AI matching engine.");
		body.put("assetType", "MVP");
		body.put("marketplaceMode", "ACQUIRE");
		body.put("category", "FINTECH");
		body.put("stage", "PROTOTYPE");
		body.put("askingPrice", new BigDecimal("25000.50"));
		body.put("currency", "USD");
		body.put("priceNegotiable", true);
		body.put("collaborationDetails", "Looking for a technical cofounder.");
		return body;
	}

	private void assertRejected(Consumer<Map<String, Object>> change, String field) throws Exception {
		Map<String, Object> body = body();
		change.accept(body);
		create(this.aliceToken, body).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").exists());
	}

	private ResultActions create(String token, Map<String, Object> body) throws Exception {
		return this.mockMvc.perform(withJson(post(LISTINGS), token, body));
	}

	private long createListing(String token, String title) throws Exception {
		Map<String, Object> body = body();
		body.put("title", title);
		String response = create(token, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private ResultActions update(String token, long id, Map<String, Object> body) throws Exception {
		return this.mockMvc.perform(withJson(put(LISTINGS + "/" + id), token, body));
	}

	private ResultActions submit(String token, long id) throws Exception {
		return this.mockMvc.perform(post(LISTINGS + "/" + id + "/submit").header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions archive(String token, long id) throws Exception {
		return this.mockMvc.perform(delete(LISTINGS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, String token,
			Map<String, Object> body) {
		return request.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body));
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String slugOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.slug");
	}

	private String slugOfListing(long id) {
		return this.jdbc.queryForObject("SELECT slug FROM listings WHERE id = ?", String.class, id);
	}

	private String statusOf(long id) {
		return this.jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, id);
	}

	// Stand-in for the future moderation batch. Timestamps are written as UTC, like Hibernate does.
	private void publish(long id, Instant publishedAt) {
		this.jdbc.update("UPDATE listings SET status = 'PUBLISHED', published_at = ? WHERE id = ?",
				LocalDateTime.ofInstant(publishedAt, ZoneOffset.UTC), id);
	}

	private void setStatus(long id, ListingStatus status) {
		this.jdbc.update("UPDATE listings SET status = ? WHERE id = ?", status.name(), id);
	}

	private void setUpdatedAt(long id, Instant updatedAt) {
		this.jdbc.update("UPDATE listings SET updated_at = ? WHERE id = ?",
				LocalDateTime.ofInstant(updatedAt, ZoneOffset.UTC), id);
	}

}
