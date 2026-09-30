package com.conflux.user;

import com.conflux.common.web.ApiPaths;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public user profiles. No authentication required.
 */
@RestController
@RequestMapping(UserController.BASE_PATH)
public class UserController {

	public static final String BASE_PATH = ApiPaths.API_V1 + "/users";

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/{username}")
	public UserProfileResponse publicProfile(@PathVariable String username) {
		return this.userService.publicProfile(username);
	}

}
