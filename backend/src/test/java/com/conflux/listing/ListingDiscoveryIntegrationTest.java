package com.conflux.listing;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.conflux.auth.JwtTokenService;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingDiscoveryIntegrationTest {

	private static final String LISTINGS = "/api/v1/listings";

	private static final String P1 = "AI Invoice Reconciliation";

	private static final String P2 = "Campus Tutor Network";

	private static final String P3 = "Clinic Scheduler";

	private static final String P4 = "Ledger API for Startups";

	private static final String P5 = "Pocket CRM";

	private static final String P6 = "Fintech Savings Coach";

	private static final List<String> HIDDEN = List.of("Draft Reconciliation Tool", "Archived Reconciliation Tool",
			"Suspended Reconciliation Tool");

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

	private String aliceToken;

	private String bobToken;

	private final Map<String, Long> ids = new LinkedHashMap<>();

	@BeforeAll
	void createFixture() throws Exception {
		cleanDatabase();
		User alice = this.userRepository.save(new User("alice@example.com", "unused-hash", "alice", "Alice A"));
		User bob = this.userRepository.save(new User("bob@example.com", "unused-hash", "bob", "Bob B"));
		this.aliceToken = this.tokenService.issueAccessToken(alice).accessToken();
		this.bobToken = this.tokenService.issueAccessToken(bob).accessToken();

		published(this.aliceToken, P1, "Close the books faster.", "Machine learning for finance teams.", "MVP",
				"ACQUIRE", "FINTECH", "PROTOTYPE", "25000.00", "USD", "2026-01-01T10:00", "2026-02-05T10:00");
		published(this.bobToken, P2, "A moonshot for peer learning.", "Students teach students.", "IDEA",
				"COLLABORATE", "EDTECH", "CONCEPT", null, null, "2026-01-02T10:00", "2026-02-01T10:00");
		published(this.aliceToken, P3, "Fewer missed appointments.", "Built around a zebra-striped calendar view.",
				"PROJECT", "ACQUIRE", "HEALTHTECH", "LIVE", "5000.00", "EUR", "2026-01-03T10:00", "2026-02-04T10:00");
		published(this.bobToken, P4, "Invoices and payouts through one API.", "Developer-first accounting.",
				"STARTUP", "COLLABORATE", "FINTECH", "REVENUE", "250000.00", "USD", "2026-01-04T10:00",
				"2026-02-03T10:00");
		published(this.aliceToken, P5, "CRM for freelancers.", "Simple pipeline tracking.", "MVP", "COLLABORATE",
				"SAAS", "MVP", "5000.00", "EUR", "2026-01-05T10:00", "2026-02-03T10:00");
		published(this.bobToken, P6, "Round-ups that invest.", "Behavioural finance app.", "MVP", "COLLABORATE",
				"FINTECH", "LIVE", null, null, "2026-01-05T10:00", "2026-02-02T10:00");

		long draft = create(this.aliceToken, HIDDEN.get(0), "zebra", "1.00");
		long archived = create(this.aliceToken, HIDDEN.get(1), "zebra", "2.00");
		archive(archived);
		long suspended = create(this.aliceToken, HIDDEN.get(2), "zebra", "3.00");
		publish(this.aliceToken, suspended);
		this.jdbc.update("UPDATE listings SET status = 'SUSPENDED' WHERE id = ?", suspended);
		assertThat(statusOf(draft)).isEqualTo("DRAFT");
		assertThat(statusOf(archived)).isEqualTo("ARCHIVED");
		assertThat(statusOf(suspended)).isEqualTo("SUSPENDED");
	}

	@AfterAll
	void cleanDatabase() {
		this.listingRepository.deleteAllInBatch();
		this.userRepository.deleteAllInBatch();
	}

	@Test
	void publicDiscoveryReturnsOnlyPublishedListingsWithoutAuthentication() throws Exception {
		discover("").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.content[*].status").doesNotExist());
		assertThat(titles("")).containsExactlyInAnyOrder(P1, P2, P3, P4, P5, P6).doesNotContainAnyElementsOf(HIDDEN);
	}

	@Test
	void hiddenListingsNeverMatchEvenWhenSearchFiltersAndSortingFavourThem() throws Exception {
		assertThat(titles("search=reconciliation")).containsExactly(P1);
		assertThat(titles("search=zebra")).containsExactly(P3);
		assertThat(titles("assetType=MVP&marketplaceMode=COLLABORATE&category=FINTECH&stage=LIVE"))
			.containsExactly(P6);
		for (ListingSort sort : ListingSort.values()) {
			assertThat(titles("sort=" + sort + "&size=50")).as(sort.name())
				.hasSize(6)
				.doesNotContainAnyElementsOf(HIDDEN);
		}
	}

	@Test
	void searchMatchesTitleShortPitchAndDescription() throws Exception {
		assertThat(titles("search=Reconciliation")).containsExactly(P1);
		assertThat(titles("search=moonshot")).containsExactly(P2);
		assertThat(titles("search=zebra-striped")).containsExactly(P3);
		assertThat(titles("search=invoice")).containsExactly(P4, P1);
		assertThat(titles("search=" + encode("invoice reconciliation"))).containsExactly(P1);
		assertThat(titles("search=" + encode("reconciliation invoice"))).isEmpty();
	}

	@Test
	void searchIsCaseInsensitiveAndTrimmed() throws Exception {
		assertThat(titles("search=RECONCILIATION")).containsExactly(P1);
		assertThat(titles("search=ZeBrA")).containsExactly(P3);
		discover("search=" + encode("   moonshot  ")).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].title").value(P2));
	}

	@Test
	void missingOrBlankSearchMeansNoSearch() throws Exception {
		assertThat(titles("search=")).hasSize(6);
		assertThat(titles("search=" + encode("   "))).hasSize(6);
		assertThat(titles("")).hasSize(6);
	}

	@Test
	void searchWithoutMatchesIs200WithAnEmptyPage() throws Exception {
		discover("search=nonexistentterm").andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray())
			.andExpect(jsonPath("$.content.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.totalPages").value(0))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(12))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(true));
	}

	@Test
	void searchIsLimitedTo100Characters() throws Exception {
		discover("search=" + "a".repeat(100)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		discover("search=" + "a".repeat(101)).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void searchInputIsTreatedAsPlainText() throws Exception {
		assertThat(titles("search=" + encode("%"))).isEmpty();
		assertThat(titles("search=" + encode("_"))).isEmpty();
		assertThat(titles("search=" + encode("!"))).isEmpty();
		assertThat(titles("search=" + encode("' OR '1'='1"))).isEmpty();
		assertThat(titles("search=" + encode("x') OR 1=1 --"))).isEmpty();
		assertThat(titles("search=" + encode("round-ups"))).containsExactly(P6);
		assertThat(titles("search=" + encode("faster."))).containsExactly(P1);
	}

	@Test
	void assetTypeFilter() throws Exception {
		assertThat(titles("assetType=IDEA")).containsExactly(P2);
		assertThat(titles("assetType=PROJECT")).containsExactly(P3);
		assertThat(titles("assetType=MVP")).containsExactly(P6, P5, P1);
		assertThat(titles("assetType=STARTUP")).containsExactly(P4);
	}

	@Test
	void marketplaceModeFilter() throws Exception {
		assertThat(titles("marketplaceMode=ACQUIRE")).containsExactly(P3, P1);
		assertThat(titles("marketplaceMode=COLLABORATE")).containsExactly(P6, P5, P4, P2);
	}

	@Test
	void categoryFilter() throws Exception {
		assertThat(titles("category=FINTECH")).containsExactly(P6, P4, P1);
		assertThat(titles("category=EDTECH")).containsExactly(P2);
		assertThat(titles("category=SAAS")).containsExactly(P5);
		discover("category=AI").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
	}

	@Test
	void stageFilter() throws Exception {
		assertThat(titles("stage=LIVE")).containsExactly(P6, P3);
		assertThat(titles("stage=CONCEPT")).containsExactly(P2);
		assertThat(titles("stage=REVENUE")).containsExactly(P4);
		assertThat(titles("stage=MVP")).containsExactly(P5);
	}

	@Test
	void invalidOrWronglyCasedEnumValuesAre400() throws Exception {
		for (String query : new String[] { "category=BANANA", "category=fintech", "stage=LAUNCHED", "stage=live",
				"assetType=Mvp", "assetType=APP", "marketplaceMode=collaborate", "marketplaceMode=BUY",
				"sort=publishedAt", "sort=newest", "sort=title,asc" }) {
			discover(query).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(content().string(not(containsString("Exception"))));
		}
	}

	@Test
	void filtersCombineWithAnd() throws Exception {
		assertThat(titles("assetType=MVP&marketplaceMode=COLLABORATE&category=FINTECH")).containsExactly(P6);
		assertThat(titles("assetType=MVP&category=FINTECH")).containsExactly(P6, P1);
		assertThat(titles("category=FINTECH&marketplaceMode=ACQUIRE")).containsExactly(P1);
		assertThat(titles("assetType=MVP&category=SAAS&stage=MVP")).containsExactly(P5);
		assertThat(titles("category=FINTECH&stage=LIVE&search=savings")).containsExactly(P6);
		assertThat(titles("assetType=MVP&marketplaceMode=COLLABORATE&category=FINTECH&search=invoice")).isEmpty();
		assertThat(titles("category=HEALTHTECH&marketplaceMode=COLLABORATE")).isEmpty();
	}

	@Test
	void newestIsTheDefaultAndOrdersByPublishedAtDescendingThenIdDescending() throws Exception {
		assertThat(titles("sort=NEWEST")).containsExactly(P6, P5, P4, P3, P2, P1);
		assertThat(titles("")).containsExactly(P6, P5, P4, P3, P2, P1);
	}

	@Test
	void oldestOrdersByPublishedAtAscendingThenIdAscending() throws Exception {
		assertThat(titles("sort=OLDEST")).containsExactly(P1, P2, P3, P4, P5, P6);
	}

	@Test
	void updatedOrdersByUpdatedAtDescendingThenIdDescending() throws Exception {
		assertThat(titles("sort=UPDATED")).containsExactly(P1, P3, P5, P4, P6, P2);
	}

	@Test
	void priceSortsPutUnpricedListingsLastAndBreakTiesByNewest() throws Exception {
		assertThat(titles("sort=PRICE_LOW")).containsExactly(P5, P3, P1, P4, P6, P2);
		assertThat(titles("sort=PRICE_HIGH")).containsExactly(P4, P1, P5, P3, P6, P2);
	}

	@Test
	void sortingCombinesWithSearchAndFilters() throws Exception {
		assertThat(titles("search=invoice&sort=PRICE_LOW")).containsExactly(P1, P4);
		assertThat(titles("category=FINTECH&sort=OLDEST")).containsExactly(P1, P4, P6);
		assertThat(titles("marketplaceMode=COLLABORATE&sort=PRICE_HIGH")).containsExactly(P4, P5, P6, P2);
	}

	@Test
	void defaultPageSizeIs12() throws Exception {
		discover("").andExpect(jsonPath("$.size").value(12))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.content.length()").value(6))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(true));
	}

	@Test
	void customPageAndSizeWithCorrectMetadata() throws Exception {
		discover("page=0&size=4").andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(4))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(4))
			.andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(false));
		assertThat(titles("page=0&size=4")).containsExactly(P6, P5, P4, P3);
		assertThat(titles("page=1&size=4")).containsExactly(P2, P1);
		discover("page=1&size=4").andExpect(jsonPath("$.first").value(false)).andExpect(jsonPath("$.last").value(true));
		assertThat(titles("marketplaceMode=COLLABORATE&page=1&size=2")).containsExactly(P4, P2);
		discover("marketplaceMode=COLLABORATE&page=1&size=2").andExpect(jsonPath("$.totalElements").value(4))
			.andExpect(jsonPath("$.totalPages").value(2));
		discover("page=5&size=4").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
	}

	@Test
	void paginationBoundsAreEnforced() throws Exception {
		for (String query : new String[] { "size=51", "size=0", "page=-1", "page=10001", "page=abc", "size=x" }) {
			discover(query).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		discover("size=50").andExpect(status().isOk());
		discover("page=10000").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
	}

	@Test
	void cardsContainOnlyBrowsingFieldsAndNoPrivateData() throws Exception {
		String response = discover("search=reconciliation").andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(this.ids.get(P1)))
			.andExpect(jsonPath("$.content[0].slug").exists())
			.andExpect(jsonPath("$.content[0].title").value(P1))
			.andExpect(jsonPath("$.content[0].shortPitch").value("Close the books faster."))
			.andExpect(jsonPath("$.content[0].assetType").value("MVP"))
			.andExpect(jsonPath("$.content[0].marketplaceMode").value("ACQUIRE"))
			.andExpect(jsonPath("$.content[0].category").value("FINTECH"))
			.andExpect(jsonPath("$.content[0].stage").value("PROTOTYPE"))
			.andExpect(jsonPath("$.content[0].askingPrice").value(25000))
			.andExpect(jsonPath("$.content[0].currency").value("USD"))
			.andExpect(jsonPath("$.content[0].priceNegotiable").value(false))
			.andExpect(jsonPath("$.content[0].publishedAt").value("2026-01-01T10:00:00Z"))
			.andExpect(jsonPath("$.content[0].owner.username").value("alice"))
			.andExpect(jsonPath("$.content[0].owner.displayName").value("Alice A"))
			.andExpect(jsonPath("$.content[0].description").doesNotExist())
			.andExpect(jsonPath("$.content[0].problem").doesNotExist())
			.andExpect(jsonPath("$.content[0].solution").doesNotExist())
			.andExpect(jsonPath("$.content[0].collaborationDetails").doesNotExist())
			.andExpect(jsonPath("$.content[0].owner.email").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(response).doesNotContain("@example.com")
			.doesNotContain("passwordHash")
			.doesNotContain("unused-hash")
			.doesNotContain("Machine learning for finance teams.");
	}

	@Test
	void publicDetailStillWorksForDiscoveredListings() throws Exception {
		String slug = JsonPath.read(discover("search=zebra").andReturn().getResponse().getContentAsString(),
				"$.content[0].slug");
		this.mockMvc.perform(get(LISTINGS + "/" + slug))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value(P3))
			.andExpect(jsonPath("$.description").value("Built around a zebra-striped calendar view."));
	}

	private ResultActions discover(String query) throws Exception {
		MockHttpServletRequestBuilder request = get(LISTINGS);
		if (!query.isEmpty()) {
			for (String pair : query.split("&")) {
				int separator = pair.indexOf('=');
				request.param(pair.substring(0, separator),
						URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
			}
		}
		return this.mockMvc.perform(request);
	}

	private List<String> titles(String query) throws Exception {
		String response = discover(query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.content[*].title");
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private void published(String token, String title, String shortPitch, String description, String assetType,
			String marketplaceMode, String category, String stage, String price, String currency, String publishedAt,
			String updatedAt) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("shortPitch", shortPitch);
		body.put("description", description);
		body.put("assetType", assetType);
		body.put("marketplaceMode", marketplaceMode);
		body.put("category", category);
		body.put("stage", stage);
		if (price != null) {
			body.put("askingPrice", new BigDecimal(price));
			body.put("currency", currency);
		}
		long id = createFrom(token, body);
		publish(token, id);
		this.jdbc.update("UPDATE listings SET published_at = ?, updated_at = ? WHERE id = ?",
				LocalDateTime.parse(publishedAt), LocalDateTime.parse(updatedAt), id);
		this.ids.put(title, id);
	}

	private long create(String token, String title, String description, String price) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("shortPitch", "Reconciliation for invoices.");
		body.put("description", description);
		body.put("assetType", "MVP");
		body.put("marketplaceMode", "COLLABORATE");
		body.put("category", "FINTECH");
		body.put("stage", "LIVE");
		body.put("askingPrice", new BigDecimal(price));
		body.put("currency", "USD");
		return createFrom(token, body);
	}

	private long createFrom(String token, Map<String, Object> body) throws Exception {
		String response = this.mockMvc
			.perform(authorized(post(LISTINGS), token).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(body)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private void publish(String token, long id) throws Exception {
		this.mockMvc.perform(authorized(post(LISTINGS + "/" + id + "/publish"), token)).andExpect(status().isOk());
	}

	private void archive(long id) throws Exception {
		this.mockMvc.perform(authorized(delete(LISTINGS + "/" + id), this.aliceToken))
			.andExpect(status().isNoContent());
	}

	private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private String statusOf(long id) {
		return this.jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, id);
	}

}
