package com.conflux.user;

import com.conflux.common.web.ApiPaths;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated user's own profile. Requires a bearer token; always acts on the
 * token's user.
 */
@RestController
@RequestMapping(ProfileController.BASE_PATH)
public class ProfileController {

	public static final String BASE_PATH = ApiPaths.API_V1 + "/profile";

	private final UserService userService;

	public ProfileController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public UserProfileResponse currentProfile() {
		return this.userService.currentProfile();
	}

	@PutMapping
	public UserProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		return this.userService.updateProfile(request);
	}

}
