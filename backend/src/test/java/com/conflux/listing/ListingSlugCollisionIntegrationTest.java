package com.conflux.listing;

import java.util.Map;

import com.conflux.auth.JwtTokenService;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forces slug collisions over HTTP by making the slug generator deterministic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListingSlugCollisionIntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@MockitoBean
	private SlugGenerator slugGenerator;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ListingRepository listingRepository;

	@Autowired
	private JwtTokenService tokenService;

	private String token;

	@BeforeEach
	void setUp() {
		cleanDatabase();
		User user = this.userRepository.save(new User("alice@example.com", "unused-hash", "alice", "Alice"));
		this.token = this.tokenService.issueAccessToken(user).accessToken();
		given(this.slugGenerator.generate(anyString())).willReturn("always-the-same-0000abcd");
	}

	@AfterEach
	void cleanDatabase() {
		this.listingRepository.deleteAllInBatch();
		this.userRepository.deleteAllInBatch();
	}

	@Test
	void collidingSlugIsA409ProblemDetailNotA500() throws Exception {
		create().andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value("always-the-same-0000abcd"));

		create().andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("A unique URL for this listing could not be generated. Please try again."))
			.andExpect(content().string(not(containsString("uk_listings_slug"))))
			.andExpect(content().string(not(containsString("SQL"))));
		assertThat(this.listingRepository.count()).isEqualTo(1);
	}

	private ResultActions create() throws Exception {
		Map<String, Object> body = Map.of("title", "Same", "shortPitch", "Pitch", "description", "Description",
				"assetType", "IDEA", "marketplaceMode", "COLLABORATE", "category", "OTHER", "stage", "CONCEPT");
		return this.mockMvc.perform(post("/api/v1/listings").header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body)));
	}

}
