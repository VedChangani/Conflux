package com.conflux.message;

import com.conflux.auth.JwtTokenService;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accepting a connection and creating its conversation are one transaction: if creating the
 * conversation fails, the connection stays PENDING.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationCreationAtomicityIntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@MockitoSpyBean
	private ConversationService conversationService;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanDatabase() {
		for (String table : new String[] { "messages", "conversations", "connections", "listings", "users" }) {
			this.jdbc.update("DELETE FROM " + table);
		}
	}

	@Test
	void failedConversationCreationRollsBackTheAcceptance() throws Exception {
		User alice = this.userRepository.save(new User("alice@example.com", "unused-hash", "alice", "Alice"));
		User bob = this.userRepository.save(new User("bob@example.com", "unused-hash", "bob", "Bob"));
		String aliceToken = this.tokenService.issueAccessToken(alice).accessToken();
		String bobToken = this.tokenService.issueAccessToken(bob).accessToken();

		String listing = this.mockMvc
			.perform(authorized(post("/api/v1/listings"), aliceToken).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(java.util.Map.of("title", "Atomic", "shortPitch", "Pitch", "description",
						"Description", "assetType", "IDEA", "marketplaceMode", "COLLABORATE", "category", "AI", "stage",
						"CONCEPT"))))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long listingId = ((Number) JsonPath.read(listing, "$.id")).longValue();
		this.mockMvc.perform(authorized(post("/api/v1/listings/" + listingId + "/publish"), aliceToken))
			.andExpect(status().isOk());
		String connection = this.mockMvc
			.perform(authorized(post("/api/v1/listings/" + listingId + "/interest"), bobToken))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long connectionId = ((Number) JsonPath.read(connection, "$.id")).longValue();

		doThrow(new IllegalStateException("simulated failure")).when(this.conversationService).createFor(any());

		this.mockMvc.perform(authorized(post("/api/v1/connections/" + connectionId + "/accept"), aliceToken))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred."));

		assertThat(this.jdbc.queryForObject("SELECT status FROM connections WHERE id = ?", String.class, connectionId))
			.isEqualTo("PENDING");
		assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Long.class)).isZero();
	}

	private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

}
