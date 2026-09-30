package com.conflux.admin;

import com.conflux.common.web.ApiPaths;
import com.conflux.listing.ListingModerationService;
import com.conflux.user.UserModerationService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reactive moderation of accounts and listings by administrators ({@code ROLE_ADMIN}, see
 * SecurityConfig). Every operation is idempotent and answers 204.
 */
@RestController
@RequestMapping(ApiPaths.ADMIN)
public class AdminModerationController {

	private final UserModerationService userModerationService;

	private final ListingModerationService listingModerationService;

	public AdminModerationController(UserModerationService userModerationService,
			ListingModerationService listingModerationService) {
		this.userModerationService = userModerationService;
		this.listingModerationService = listingModerationService;
	}

	@PostMapping("/users/{id}/suspend")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void suspendUser(@PathVariable Long id) {
		this.userModerationService.suspend(id);
	}

	@PostMapping("/users/{id}/restore")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restoreUser(@PathVariable Long id) {
		this.userModerationService.restore(id);
	}

	@PostMapping("/listings/{id}/suspend")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void suspendListing(@PathVariable Long id) {
		this.listingModerationService.suspend(id);
	}

	@PostMapping("/listings/{id}/restore")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restoreListing(@PathVariable Long id) {
		this.listingModerationService.restore(id);
	}

}
