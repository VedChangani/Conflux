package com.conflux.listing;

import com.conflux.common.web.ApiPaths;
import com.conflux.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listing endpoints. Public: {@code GET /listings} and {@code GET /listings/{slug}}
 * (published only). Everything else requires a bearer token; the acting user is always
 * the token's subject, never a value from the request.
 */
@RestController
@RequestMapping(ListingController.BASE_PATH)
public class ListingController {

	public static final String BASE_PATH = ApiPaths.API_V1 + "/listings";

	public static final String MINE_PATH = BASE_PATH + "/mine";

	private static final String DEFAULT_PAGE_SIZE = "12";

	private final ListingService listingService;

	public ListingController(ListingService listingService) {
		this.listingService = listingService;
	}

	/**
	 * Public marketplace discovery. All filters are optional and combined with AND; enum
	 * values are the upper-case names (e.g. {@code assetType=MVP}), anything else is 400.
	 */
	@GetMapping
	public PageResponse<ListingCardResponse> discover(
			@RequestParam(required = false) @Size(max = ListingDiscoveryCriteria.SEARCH_MAX_LENGTH) String search,
			@RequestParam(required = false) ListingAssetType assetType,
			@RequestParam(required = false) ListingMarketplaceMode marketplaceMode,
			@RequestParam(required = false) ListingCategory category,
			@RequestParam(required = false) ListingStage stage, @RequestParam(required = false) ListingSort sort,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(50) int size) {
		ListingDiscoveryCriteria criteria = new ListingDiscoveryCriteria(search, assetType, marketplaceMode, category,
				stage, sort);
		return this.listingService.discover(criteria, page, size);
	}

	@GetMapping("/{slug}")
	public ListingDetailResponse publishedListing(@PathVariable String slug) {
		return this.listingService.publishedListing(slug);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ListingDetailResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ListingRequest request) {
		return this.listingService.create(userId(jwt), request);
	}

	@GetMapping("/mine")
	public PageResponse<MyListingSummaryResponse> myListings(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(50) int size) {
		return this.listingService.myListings(userId(jwt), page, size);
	}

	@GetMapping("/mine/{id}")
	public ListingDetailResponse myListing(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return this.listingService.myListing(userId(jwt), id);
	}

	@PutMapping("/{id}")
	public ListingDetailResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody ListingRequest request) {
		return this.listingService.update(userId(jwt), id, request);
	}

	@PostMapping("/{id}/publish")
	public ListingDetailResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return this.listingService.publish(userId(jwt), id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void archive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		this.listingService.archive(userId(jwt), id);
	}

	// Access tokens are issued with the user id as subject (see JwtTokenService).
	private static Long userId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

}
