package com.conflux.user;

import java.util.LinkedHashMap;
import java.util.Map;

import com.conflux.auth.JwtTokenService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP behaviour of public and own profiles: full context, real security filter chain,
 * Flyway-migrated H2 database. Alice has a complete profile, Bob an empty one, Carol a
 * complete profile but a suspended account. Alice's published listing is saved by Bob and
 * Bob's interest in it is accepted (with its conversation), for the regression checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileApiIntegrationTest {

	private static final String USERS = "/api/v1/users";

	private static final String PROFILE = "/api/v1/profile";

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

	private User carol;

	private String aliceToken;

	private String bobToken;

	private String carolToken;

	private long aliceListing;

	private long connection;

	private long conversation;

	@BeforeEach
	void setUp() throws Exception {
		cleanDatabase();
		this.alice = this.userRepository.save(new User("alice@example.com", "unused-hash", "alice", "Alice"));
		this.bob = this.userRepository.save(new User("bob@example.com", "unused-hash", "bob", "Bob B"));
		User suspended = new User("carol@example.com", "unused-hash", "carol", "Carol C");
		suspended.setBio("Carol's bio");
		suspended.setWebsiteUrl("https://carol.example.com");
		this.carol = this.userRepository.save(suspended);
		this.aliceToken = this.tokenService.issueAccessToken(this.alice).accessToken();
		this.bobToken = this.tokenService.issueAccessToken(this.bob).accessToken();
		this.carolToken = this.tokenService.issueAccessToken(this.carol).accessToken();
		this.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", this.carol.getId());

		updateProfile(this.aliceToken, fullProfile()).andExpect(status().isOk());

		this.aliceListing = publishedListing(this.aliceToken);
		perform(post("/api/v1/listings/" + this.aliceListing + "/save"), this.bobToken).andExpect(status().isNoContent());
		this.connection = idOf(perform(post("/api/v1/listings/" + this.aliceListing + "/interest"), this.bobToken)
			.andExpect(status().isCreated()));
		perform(post("/api/v1/connections/" + this.connection + "/accept"), this.aliceToken).andExpect(status().isOk());
		this.conversation = this.jdbc.queryForObject("SELECT id FROM conversations WHERE connection_id = ?", Long.class,
				this.connection);
	}

	@AfterEach
	void cleanDatabase() {
		for (String table : new String[] { "messages", "conversations", "connections", "saved_listings", "listings",
				"users" }) {
			this.jdbc.update("DELETE FROM " + table);
		}
	}

	// ---- Public profile ------------------------------------------------------------------

	@Test
	void publicProfileOfAnActiveUserNeedsNoAuthentication() throws Exception {
		String response = this.mockMvc.perform(get(USERS + "/alice"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.username").value("alice"))
			.andExpect(jsonPath("$.displayName").value("Alice   Liddell"))
			.andExpect(jsonPath("$.bio").value("Builds marketplaces.\nLoves  tea."))
			.andExpect(jsonPath("$.location").value("Pune, India"))
			.andExpect(jsonPath("$.websiteUrl").value("https://alice.example.com/"))
			.andExpect(jsonPath("$.githubUrl").value("https://github.com/alice"))
			.andExpect(jsonPath("$.linkedinUrl").value("http://www.linkedin.com/in/alice"))
			.andExpect(jsonPath("$.createdAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertNoPrivateData(response);
		assertThat(JSON.readTree(response).propertyNames()).containsExactlyInAnyOrder("id", "username", "displayName",
				"bio", "location", "websiteUrl", "githubUrl", "linkedinUrl", "createdAt");

		// Usernames follow the usual normalization.
		this.mockMvc.perform(get(USERS + "/ALICE")).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
	}

	@Test
	void emptyProfileHasNullOptionalFields() throws Exception {
		this.mockMvc.perform(get(USERS + "/bob"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Bob B"))
			.andExpect(jsonPath("$.bio").value(nullValue()))
			.andExpect(jsonPath("$.location").value(nullValue()))
			.andExpect(jsonPath("$.websiteUrl").value(nullValue()))
			.andExpect(jsonPath("$.githubUrl").value(nullValue()))
			.andExpect(jsonPath("$.linkedinUrl").value(nullValue()));
	}

	@Test
	void missingAndSuspendedUsersGetTheSame404() throws Exception {
		String missing = this.mockMvc.perform(get(USERS + "/nobody"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("User not found."))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String suspended = this.mockMvc.perform(get(USERS + "/carol"))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(suspended).isEqualTo(missing.replace("nobody", "carol")).doesNotContain("SUSPENDED")
			.doesNotContain("Carol's bio");
		// Strings that cannot be usernames are simply not found.
		for (String impossible : new String[] { "ab", "josé", "a.b", "UPPER_but-too-long-" + "x".repeat(20) }) {
			this.mockMvc.perform(get(USERS + "/{username}", impossible))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("User not found."));
		}
	}

	// ---- Own profile -----------------------------------------------------------------------

	@Test
	void authenticatedUserGetsTheirOwnProfileFromTheToken() throws Exception {
		String response = perform(get(PROFILE).param("username", "alice").param("userId", this.alice.getId().toString()),
				this.bobToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(this.bob.getId()))
			.andExpect(jsonPath("$.username").value("bob"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertNoPrivateData(response);
		perform(get(PROFILE), this.aliceToken).andExpect(jsonPath("$.bio").value("Builds marketplaces.\nLoves  tea."));
	}

	@Test
	void ownProfileRequiresAuthentication() throws Exception {
		this.mockMvc.perform(get(PROFILE))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, "Bearer x.y.z"))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(put(PROFILE).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(fullProfile())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void suspendedUserCanReadButNotUpdateTheirOwnProfile() throws Exception {
		perform(get(PROFILE), this.carolToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("carol"))
			.andExpect(jsonPath("$.bio").value("Carol's bio"))
			.andExpect(jsonPath("$.status").doesNotExist());

		Map<String, Object> change = fullProfile();
		change.put("displayName", "Carol Changed");
		updateProfile(this.carolToken, change).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("This account is suspended."));
		assertThat(column("display_name", this.carol)).isEqualTo("Carol C");
	}

	// ---- Update ----------------------------------------------------------------------------

	@Test
	void updateNormalizesAndReplacesTheEditableFields() throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("displayName", "  Bob   the Builder  ");
		body.put("bio", "  Hands-on engineer.  ");
		body.put("location", "   ");
		body.put("websiteUrl", " http://bob.example.com ");
		body.put("githubUrl", "");
		body.put("linkedinUrl", "https://www.linkedin.com/in/bob");

		updateProfile(this.bobToken, body).andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Bob   the Builder"))
			.andExpect(jsonPath("$.bio").value("Hands-on engineer."))
			.andExpect(jsonPath("$.location").value(nullValue()))
			.andExpect(jsonPath("$.websiteUrl").value("http://bob.example.com"))
			.andExpect(jsonPath("$.githubUrl").value(nullValue()))
			.andExpect(jsonPath("$.linkedinUrl").value("https://www.linkedin.com/in/bob"));

		assertThat(this.jdbc.queryForMap("SELECT display_name, bio, location, website_url, github_url, linkedin_url "
				+ "FROM users WHERE id = ?", this.bob.getId()))
			.containsEntry("display_name", "Bob   the Builder")
			.containsEntry("bio", "Hands-on engineer.")
			.containsEntry("location", null)
			.containsEntry("website_url", "http://bob.example.com")
			.containsEntry("github_url", null)
			.containsEntry("linkedin_url", "https://www.linkedin.com/in/bob");
		this.mockMvc.perform(get(USERS + "/bob")).andExpect(jsonPath("$.displayName").value("Bob   the Builder"));

		// A PUT replaces everything: omitted optional fields are cleared.
		updateProfile(this.bobToken, Map.of("displayName", "Bob")).andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value(nullValue()))
			.andExpect(jsonPath("$.linkedinUrl").value(nullValue()));
	}

	@Test
	void lengthLimitsAreEnforcedAfterTrimming() throws Exception {
		String url255 = "https://example.com/" + "a".repeat(235);
		Map<String, Object> atLimits = new LinkedHashMap<>();
		atLimits.put("displayName", " " + "d".repeat(100) + " ");
		atLimits.put("bio", "b".repeat(500));
		atLimits.put("location", "l".repeat(120));
		atLimits.put("websiteUrl", url255);
		atLimits.put("githubUrl", url255);
		atLimits.put("linkedinUrl", url255);
		updateProfile(this.bobToken, atLimits).andExpect(status().isOk());

		assertRejected("displayName", "d".repeat(101));
		assertRejected("bio", "b".repeat(501));
		assertRejected("location", "l".repeat(121));
		for (String url : new String[] { "websiteUrl", "githubUrl", "linkedinUrl" }) {
			assertRejected(url, url255 + "a");
		}
	}

	@Test
	void displayNameIsRequired() throws Exception {
		for (Object value : new Object[] { null, "", "   " }) {
			Map<String, Object> body = fullProfile();
			body.put("displayName", value);
			updateProfile(this.aliceToken, body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("displayName"));
		}
		Map<String, Object> missing = fullProfile();
		missing.remove("displayName");
		updateProfile(this.aliceToken, missing).andExpect(status().isBadRequest());
		assertThat(column("display_name", this.alice)).isEqualTo("Alice   Liddell");
	}

	@Test
	void onlyHttpAndHttpsUrlsAreAccepted() throws Exception {
		for (String invalid : new String[] { "example.com", "www.example.com", "ftp://example.com",
				"javascript:alert(1)", "https://", "https://exa mple.com", "mailto:alice@example.com" }) {
			for (String field : new String[] { "websiteUrl", "githubUrl", "linkedinUrl" }) {
				assertRejected(field, invalid);
			}
		}
		Map<String, Object> valid = fullProfile();
		valid.put("websiteUrl", "http://example.com");
		valid.put("githubUrl", "https://example.com");
		updateProfile(this.aliceToken, valid).andExpect(status().isOk())
			.andExpect(jsonPath("$.websiteUrl").value("http://example.com"))
			.andExpect(jsonPath("$.githubUrl").value("https://example.com"));
	}

	@Test
	void protectedAccountFieldsCannotBeChangedThroughTheProfile() throws Exception {
		Map<String, Object> body = fullProfile();
		body.put("id", 999_999);
		body.put("username", "mallory");
		body.put("email", "mallory@example.com");
		body.put("role", "ADMIN");
		body.put("status", "SUSPENDED");
		body.put("passwordHash", "$2a$10$attacker");
		body.put("password", "new-password-123");
		body.put("createdAt", "2020-01-01T00:00:00Z");

		updateProfile(this.aliceToken, body).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(this.alice.getId()))
			.andExpect(jsonPath("$.username").value("alice"));

		assertThat(this.jdbc.queryForMap("SELECT id, username, email, role, status, password_hash FROM users WHERE id = ?",
				this.alice.getId()))
			.containsEntry("username", "alice")
			.containsEntry("email", "alice@example.com")
			.containsEntry("role", "USER")
			.containsEntry("status", "ACTIVE")
			.containsEntry("password_hash", "unused-hash");
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = 'mallory' OR id = 999999",
				Long.class))
			.isZero();
		// Another user's profile is unaffected: the token decides whose profile is updated.
		assertThat(column("display_name", this.bob)).isEqualTo("Bob B");
	}

	// ---- Regression ------------------------------------------------------------------------

	@Test
	void profileChangesShowUpInEverySafeOwnerSummaryWithoutExposingPrivateData() throws Exception {
		Map<String, Object> renamed = fullProfile();
		renamed.put("displayName", "Alice Renamed");
		updateProfile(this.aliceToken, renamed).andExpect(status().isOk());

		String slug = this.jdbc.queryForObject("SELECT slug FROM listings WHERE id = ?", String.class, this.aliceListing);
		for (ResultActions result : new ResultActions[] {
				this.mockMvc.perform(get("/api/v1/listings/" + slug)),
				this.mockMvc.perform(get("/api/v1/listings")),
				perform(get("/api/v1/saved-listings"), this.bobToken),
				perform(get("/api/v1/connections/" + this.connection), this.bobToken),
				perform(get("/api/v1/conversations/" + this.conversation), this.bobToken) }) {
			String response = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(response).contains("Alice Renamed").doesNotContain("alice@example.com")
				.doesNotContain("unused-hash");
		}
		this.mockMvc.perform(get("/api/v1/listings/" + slug)).andExpect(jsonPath("$.owner.displayName").value("Alice Renamed"));
		perform(get("/api/v1/conversations/" + this.conversation), this.bobToken)
			.andExpect(jsonPath("$.otherParticipant.displayName").value("Alice Renamed"));
		perform(get("/api/v1/connections/" + this.connection), this.bobToken)
			.andExpect(jsonPath("$.owner.displayName").value("Alice Renamed"));
		perform(get("/api/v1/listings/mine"), this.aliceToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void registrationLoginAndAccountEndpointStillWork() throws Exception {
		String register = JSON.writeValueAsString(Map.of("email", "dora@example.com", "username", "dora", "password",
				"correct-horse-battery", "displayName", "Dora"));
		this.mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
			.andExpect(status().isCreated());
		String login = JSON.writeValueAsString(Map.of("identifier", "dora", "password", "correct-horse-battery"));
		String token = JsonPath.read(this.mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.accessToken");

		perform(get("/api/v1/auth/me"), token).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("dora"));
		perform(get(PROFILE), token).andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Dora"))
			.andExpect(jsonPath("$.bio").value(nullValue()));
		this.mockMvc.perform(get(USERS + "/dora")).andExpect(status().isOk());
	}

	// ---- Helpers ------------------------------------------------------------------------

	private static Map<String, Object> fullProfile() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("displayName", "  Alice   Liddell ");
		body.put("bio", " Builds marketplaces.\nLoves  tea. ");
		body.put("location", "Pune, India");
		body.put("websiteUrl", "https://alice.example.com/");
		body.put("githubUrl", "https://github.com/alice");
		body.put("linkedinUrl", "http://www.linkedin.com/in/alice");
		return body;
	}

	private void assertRejected(String field, String value) throws Exception {
		Map<String, Object> body = fullProfile();
		body.put(field, value);
		updateProfile(this.aliceToken, body).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").exists());
		assertThat(column("display_name", this.alice)).isEqualTo("Alice   Liddell");
	}

	private void assertNoPrivateData(String response) {
		assertThat(response).doesNotContain("@example.com")
			.doesNotContain("email")
			.doesNotContain("passwordHash")
			.doesNotContain("unused-hash")
			.doesNotContain("\"role\"")
			.doesNotContain("\"status\"")
			.doesNotContain("ACTIVE")
			.doesNotContain("eyJ");
	}

	private ResultActions updateProfile(String token, Map<String, Object> body) throws Exception {
		return perform(put(PROFILE).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)), token);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
		return this.mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String column(String column, User user) {
		return this.jdbc.queryForObject("SELECT " + column + " FROM users WHERE id = ?", String.class, user.getId());
	}

	private long publishedListing(String token) throws Exception {
		Map<String, Object> body = Map.of("title", "Alice opportunity", "shortPitch", "A short pitch.", "description",
				"A description.", "assetType", "PROJECT", "marketplaceMode", "COLLABORATE", "category", "SAAS", "stage",
				"PROTOTYPE");
		long id = idOf(perform(post("/api/v1/listings").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body)), token).andExpect(status().isCreated()));
		perform(post("/api/v1/listings/" + id + "/publish"), token).andExpect(status().isOk());
		return id;
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
