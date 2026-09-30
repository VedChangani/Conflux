package com.conflux.auth;

import com.conflux.common.web.ApiPaths;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthController.BASE_PATH)
public class AuthController {

	public static final String BASE_PATH = ApiPaths.API_V1 + "/auth";

	public static final String REGISTER_PATH = BASE_PATH + "/register";

	public static final String LOGIN_PATH = BASE_PATH + "/login";

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AccountResponse register(@Valid @RequestBody RegisterRequest request) {
		return this.authService.register(request);
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return this.authService.login(request);
	}

	@GetMapping("/me")
	public AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
		return this.authService.currentAccount(jwt.getSubject());
	}

}
