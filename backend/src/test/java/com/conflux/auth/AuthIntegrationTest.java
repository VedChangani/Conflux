package com.conflux.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserRole;
import com.conflux.user.UserStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authentication tests: full application context, the real security filter
 * chain, and the Flyway-migrated H2 test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthIntegrationTest.TestOnlyController.class)
class AuthIntegrationTest {

	private static final String REGISTER = "/api/v1/auth/register";

	private static final String LOGIN = "/api/v1/auth/login";

	private static final String ME = "/api/v1/auth/me";

	private static final String PASSWORD = "correct-horse-battery";

	private static final String INVALID_CREDENTIALS = "Invalid email/username or password.";

	private static final String TOKEN_REQUIRED = "A valid access token is required.";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JwtAuthenticationConverter jwtAuthenticationConverter;

	@BeforeEach
	void cleanDatabase() {
		this.userRepository.deleteAll();
	}

	// ---- Registration -----------------------------------------------------------------

	@Test
	void validRegistrationCreatesActiveUserAndReturnsSafeAccount() throws Exception {
		register("Ved@Example.com", "Ved_123", PASSWORD, "Ved Changani")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.email").value("ved@example.com"))
			.andExpect(jsonPath("$.username").value("ved_123"))
			.andExpect(jsonPath("$.displayName").value("Ved Changani"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User stored = this.userRepository.findByEmail("ved@example.com").orElseThrow();
		assertThat(stored.getUsername()).isEqualTo("ved_123");
		assertThat(stored.getRole()).isEqualTo(UserRole.USER);
		assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void passwordIsStoredAsBcryptHashNotPlaintext() throws Exception {
		register("ved@example.com", "ved_123", PASSWORD, "Ved").andExpect(status().isCreated());

		String hash = this.userRepository.findByEmail("ved@example.com").orElseThrow().getPasswordHash();
		assertThat(hash).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD).startsWith("$2");
		assertThat(this.passwordEncoder.matches(PASSWORD, hash)).isTrue();
	}

	@Test
	void duplicateEmailIsRejectedWith409() throws Exception {
		register("ved@example.com", "ved_123", PASSWORD, "Ved").andExpect(status().isCreated());

		register("VED@example.COM", "someone_else", PASSWORD, "Other")
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Email is already registered."));
		assertThat(this.userRepository.count()).isEqualTo(1);
	}

	@Test
	void duplicateUsernameIsRejectedWith409() throws Exception {
		register("ved@example.com", "ved_123", PASSWORD, "Ved").andExpect(status().isCreated());

		register("other@example.com", " VED_123 ", PASSWORD, "Other")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Username is already taken."));
		assertThat(this.userRepository.count()).isEqualTo(1);
	}

	@Test
	void invalidUsernameIsRejectedWith400() throws Exception {
		for (String username : new String[] { "josé", "ved.123", "ab", "a".repeat(31) }) {
			register("ved@example.com", username, PASSWORD, "Ved")
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[0].field").value("username"));
		}
		assertThat(this.userRepository.count()).isZero();
	}

	@Test
	void invalidRegistrationInputIsRejectedWith400WithoutEchoingThePassword() throws Exception {
		register("not-an-email", "ved_123", "short", "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'displayName')]").exists())
			.andExpect(content().string(not(containsString("short"))));

		this.mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content("{ not json"))
			.andExpect(status().isBadRequest());
		assertThat(this.userRepository.count()).isZero();
	}

	@Test
	void passwordLongerThanBcryptLimitIsRejectedWith400() throws Exception {
		// 40 characters but 80 UTF-8 bytes: exceeds BCrypt's 72-byte limit.
		assertPasswordRejected("é".repeat(40));
	}

	// ---- Password byte length (BCrypt's 72-byte input limit) ------------------------

	@Test
	void asciiPasswordsOfValidLengthAreAccepted() throws Exception {
		assertPasswordAccepted("abcd1234", "min8");
		assertPasswordAccepted("a".repeat(72), "max72");
	}

	@Test
	void asciiPasswordAbove72BytesIsRejected() throws Exception {
		assertPasswordRejected("a".repeat(73));
	}

	@Test
	void unicodePasswordsWithin72BytesAreAccepted() throws Exception {
		assertPasswordAccepted("é".repeat(36), "accented"); // 36 characters, 72 bytes
		assertPasswordAccepted("🔒".repeat(18), "emoji"); // 18 code points, 72 bytes
		assertPasswordAccepted("éééé", "four_chars"); // only 4 characters, but 8 bytes
	}

	@Test
	void unicodePasswordWithFewCharactersButMoreThan72BytesIsRejected() throws Exception {
		assertPasswordRejected("é".repeat(37)); // 37 characters, 74 bytes
		assertPasswordRejected("🔒".repeat(19)); // 19 code points, 76 bytes
		assertPasswordRejected("a".repeat(71) + "é"); // 72 characters, 73 bytes
	}

	@Test
	void passwordBelow8BytesIsRejected() throws Exception {
		assertPasswordRejected("abc1234");
		assertPasswordRejected("ééé"); // 6 bytes
		assertPasswordRejected("");
	}

	@Test
	void acceptedUnicodePasswordIsStoredAsBcryptHashNotPlaintext() throws Exception {
		String password = "pässwörd-🔒-密码";
		register("ved@example.com", "ved_123", password, "Ved").andExpect(status().isCreated());

		String hash = this.userRepository.findByEmail("ved@example.com").orElseThrow().getPasswordHash();
		assertThat(hash).startsWith("$2").doesNotContain(password);
		assertThat(this.passwordEncoder.matches(password, hash)).isTrue();
		login("ved_123", password).andExpect(status().isOk());
	}

	@Test
	void passwordIsUsedExactlyAsSuppliedWithoutTrimming() throws Exception {
		String password = "  spaced out  ";
		register("ved@example.com", "ved_123", password, "Ved").andExpect(status().isCreated());

		login("ved_123", password).andExpect(status().isOk());
		login("ved_123", password.strip()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS));
	}

	@Test
	void loginRejectsPasswordLongerThan72BytesEvenIfTheFirst72BytesMatch() throws Exception {
		String password = "a".repeat(72);
		register("ved@example.com", "ved_123", password, "Ved").andExpect(status().isCreated());

		login("ved_123", password + "anything").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS));
		login("ved_123", password).andExpect(status().isOk());
	}

	@Test
	void clientCannotChooseRoleOrStatus() throws Exception {
		Map<String, Object> body = registrationBody("ved@example.com", "ved_123", PASSWORD, "Ved");
		body.put("role", "ADMIN");
		body.put("status", "SUSPENDED");

		this.mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		User stored = this.userRepository.findByEmail("ved@example.com").orElseThrow();
		assertThat(stored.getRole()).isEqualTo(UserRole.USER);
		assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	// ---- Login --------------------------------------------------------------------------

	@Test
	void loginWithEmailReturnsBearerToken() throws Exception {
		registerVed();

		login("  VED@Example.com ", PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900));
	}

	@Test
	void loginWithUsernameReturnsBearerToken() throws Exception {
		registerVed();

		login(" Ved_123 ", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
	}

	@Test
	void wrongPasswordAndUnknownIdentifierGetTheSameGeneric401() throws Exception {
		registerVed();

		String wrongPassword = login("ved@example.com", "wrong-password")
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String unknownEmail = login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String unknownUsername = login("nobody", PASSWORD).andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String impossibleUsername = login("josé", PASSWORD).andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(unknownEmail).isEqualTo(wrongPassword);
		assertThat(unknownUsername).isEqualTo(wrongPassword);
		assertThat(impossibleUsername).isEqualTo(wrongPassword);
	}

	@Test
	void suspendedUserCannotLogIn() throws Exception {
		registerVed();
		User user = this.userRepository.findByEmail("ved@example.com").orElseThrow();
		user.setStatus(UserStatus.SUSPENDED);
		this.userRepository.save(user);

		login("ved@example.com", PASSWORD)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("This account is suspended."))
			.andExpect(jsonPath("$.accessToken").doesNotExist());

		// Without the correct password, suspension is not revealed.
		login("ved@example.com", "wrong-password")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS));
	}

	@Test
	void accessTokenHasExpectedClaimsAndNoSensitiveData() throws Exception {
		long id = registerVed();
		String hash = this.userRepository.findById(id).orElseThrow().getPasswordHash();

		String response = login("ved_123", PASSWORD).andReturn().getResponse().getContentAsString();
		assertThat(response).doesNotContain(hash).doesNotContain(PASSWORD).doesNotContain("passwordHash");

		Jwt jwt = this.jwtDecoder.decode(JsonPath.read(response, "$.accessToken"));
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
		assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "iat", "exp", "role");
		assertThat(jwt.getSubject()).isEqualTo(Long.toString(id));
		assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("conflux-test");
		assertThat(jwt.getIssuedAt()).isBeforeOrEqualTo(Instant.now());
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
	}

	// ---- Protected endpoint ------------------------------------------------------------

	@Test
	void meWithoutTokenIs401() throws Exception {
		this.mockMvc.perform(get(ME))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(TOKEN_REQUIRED));
	}

	@Test
	void meWithValidTokenReturnsCurrentUser() throws Exception {
		long id = registerVed();
		String token = accessToken("ved@example.com", PASSWORD);

		this.mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.email").value("ved@example.com"))
			.andExpect(jsonPath("$.username").value("ved_123"))
			.andExpect(jsonPath("$.displayName").value("Ved Changani"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void invalidTokensAre401() throws Exception {
		long id = registerVed();
		String valid = accessToken("ved@example.com", PASSWORD);
		String[] parts = valid.split("\\.");
		String tamperedSignature = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
		byte[] otherSecret = new byte[32];
		new SecureRandom().nextBytes(otherSecret);
		JwtEncoder otherKeyEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(otherSecret, "HmacSHA256"))
			.algorithm(MacAlgorithm.HS256)
			.build();
		String otherKey = encode(otherKeyEncoder, claims(id, "conflux-test", Instant.now(), Instant.now().plusSeconds(600)));
		String wrongIssuer = encode(this.jwtEncoder,
				claims(id, "someone-else", Instant.now(), Instant.now().plusSeconds(600)));

		for (String token : new String[] { "not-a-jwt", tamperedSignature, otherKey, wrongIssuer }) {
			this.mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value(TOKEN_REQUIRED));
		}
	}

	@Test
	void expiredTokenIs401() throws Exception {
		long id = registerVed();
		// Well beyond the 60 second clock skew tolerated by the decoder.
		Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
		String expired = encode(this.jwtEncoder,
				claims(id, "conflux-test", issuedAt, issuedAt.plus(Duration.ofHours(1))));

		this.mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value(TOKEN_REQUIRED));
	}

	@Test
	void tokenForAccountThatNoLongerExistsIs401() throws Exception {
		long id = registerVed();
		String token = accessToken("ved@example.com", PASSWORD);
		this.userRepository.deleteById(id);

		this.mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	// ---- Authorities and 403 -----------------------------------------------------------

	@Test
	void roleClaimMapsToSpringSecurityAuthority() {
		// Spring Security 7 also adds its own FACTOR_BEARER authority for bearer-token authentication.
		assertThat(authoritiesFor("USER")).containsExactlyInAnyOrder("ROLE_USER", "FACTOR_BEARER");
		assertThat(authoritiesFor("ADMIN")).containsExactlyInAnyOrder("ROLE_ADMIN", "FACTOR_BEARER");
	}

	@Test
	void accessDeniedForAuthenticatedUserIs403NotA500() throws Exception {
		registerVed();
		String token = accessToken("ved_123", PASSWORD);

		this.mockMvc.perform(get(TestOnlyController.PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("You do not have permission to access this resource."));
	}

	// ---- Existing behaviour in the full context ----------------------------------------

	@Test
	void healthIsStillPublic() throws Exception {
		this.mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void corsPreflightAllowsAuthorizationHeaderFromReactDevServer() throws Exception {
		this.mockMvc.perform(options(ME).header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	// ---- Helpers ------------------------------------------------------------------------

	private long registerVed() throws Exception {
		String response = register("ved@example.com", "ved_123", PASSWORD, "Ved Changani")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private void assertPasswordAccepted(String password, String username) throws Exception {
		register(username + "@example.com", username, password, "Ved").andExpect(status().isCreated());
		login(username, password).andExpect(status().isOk());
	}

	private void assertPasswordRejected(String password) throws Exception {
		long usersBefore = this.userRepository.count();
		register("rejected@example.com", "rejected", password, "Ved")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Invalid request content."))
			.andExpect(jsonPath("$.errors[0].field").value("password"))
			.andExpect(jsonPath("$.errors[0].message").value("must be between 8 and 72 bytes (UTF-8)"))
			.andExpect(content().string(not(containsString(password.isEmpty() ? "\"password\":\"\"" : password))));
		assertThat(this.userRepository.count()).isEqualTo(usersBefore);
	}

	private ResultActions register(String email, String username, String password, String displayName)
			throws Exception {
		String body = JSON.writeValueAsString(registrationBody(email, username, password, displayName));
		return this.mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static Map<String, Object> registrationBody(String email, String username, String password,
			String displayName) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("email", email);
		body.put("username", username);
		body.put("password", password);
		body.put("displayName", displayName);
		return body;
	}

	private ResultActions login(String identifier, String password) throws Exception {
		String body = JSON.writeValueAsString(Map.of("identifier", identifier, "password", password));
		return this.mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String accessToken(String identifier, String password) throws Exception {
		String response = login(identifier, password).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	private static JwtClaimsSet claims(long userId, String issuer, Instant issuedAt, Instant expiresAt) {
		return JwtClaimsSet.builder()
			.issuer(issuer)
			.subject(Long.toString(userId))
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.claim("role", "USER")
			.build();
	}

	private static String encode(JwtEncoder encoder, JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private List<String> authoritiesFor(String role) {
		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject("1")
			.claim("role", role)
			.build();
		return this.jwtAuthenticationConverter.convert(jwt)
			.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.toList();
	}

	/**
	 * Test-only endpoint behind authentication that denies access, standing in for future
	 * role-restricted endpoints.
	 */
	@RestController
	static class TestOnlyController {

		static final String PATH = "/api/v1/test-only/forbidden";

		@GetMapping(PATH)
		String forbidden() {
			throw new AccessDeniedException("test-only");
		}

	}

}
