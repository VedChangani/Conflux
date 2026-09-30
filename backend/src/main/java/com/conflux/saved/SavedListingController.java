package com.conflux.saved;

import com.conflux.common.web.ApiPaths;
import com.conflux.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saved listings of the authenticated user. All endpoints require a bearer token and
 * always act on the token's user; there is no way to address another user's saves.
 */
@RestController
public class SavedListingController {

	public static final String SAVE_PATH = ApiPaths.API_V1 + "/listings/{id}/save";

	public static final String SAVED_LISTINGS_PATH = ApiPaths.API_V1 + "/saved-listings";

	private final SavedListingService savedListingService;

	public SavedListingController(SavedListingService savedListingService) {
		this.savedListingService = savedListingService;
	}

	@PostMapping(SAVE_PATH)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void save(@PathVariable Long id) {
		this.savedListingService.save(id);
	}

	@DeleteMapping(SAVE_PATH)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unsave(@PathVariable Long id) {
		this.savedListingService.unsave(id);
	}

	@GetMapping(SAVED_LISTINGS_PATH)
	public PageResponse<SavedListingResponse> savedListings(
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
		return this.savedListingService.savedListings(page, size);
	}

}
