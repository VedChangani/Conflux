package com.conflux.listing;

import com.conflux.common.web.ApiPaths;
import com.conflux.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
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
 * the token's subject (resolved by the service through {@code CurrentUser}), never a value
 * from the request.
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
	public ListingDetailResponse create(@Valid @RequestBody ListingRequest request) {
		return this.listingService.create(request);
	}

	@GetMapping("/mine")
	public PageResponse<MyListingSummaryResponse> myListings(@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(50) int size) {
		return this.listingService.myListings(page, size);
	}

	@GetMapping("/mine/{id}")
	public ListingDetailResponse myListing(@PathVariable Long id) {
		return this.listingService.myListing(id);
	}

	@PutMapping("/{id}")
	public ListingDetailResponse update(@PathVariable Long id, @Valid @RequestBody ListingRequest request) {
		return this.listingService.update(id, request);
	}

	@PostMapping("/{id}/publish")
	public ListingDetailResponse publish(@PathVariable Long id) {
		return this.listingService.publish(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void archive(@PathVariable Long id) {
		this.listingService.archive(id);
	}

}
