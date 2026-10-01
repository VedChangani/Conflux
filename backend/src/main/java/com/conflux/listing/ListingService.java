package com.conflux.listing;

import java.util.Locale;

import com.conflux.auth.CurrentUser;
import com.conflux.common.web.PageResponse;
import com.conflux.ratelimit.RateLimitOperation;
import com.conflux.ratelimit.RateLimiter;
import com.conflux.user.User;
import com.conflux.user.UserService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ListingService {

	static final int MAX_SLUG_ATTEMPTS = 3;

	private static final Sort MINE_ORDER = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

	private final ListingRepository listingRepository;

	private final UserService userService;

	private final SlugGenerator slugGenerator;

	private final CurrentUser currentUser;

	private final RateLimiter rateLimiter;

	public ListingService(ListingRepository listingRepository, UserService userService, SlugGenerator slugGenerator,
			CurrentUser currentUser, RateLimiter rateLimiter) {
		this.listingRepository = listingRepository;
		this.userService = userService;
		this.slugGenerator = slugGenerator;
		this.currentUser = currentUser;
		this.rateLimiter = rateLimiter;
	}

	@Transactional(readOnly = true)
	public PageResponse<ListingCardResponse> discover(ListingDiscoveryCriteria criteria, int page, int size) {
		ListingSort sort = (criteria.sort() != null) ? criteria.sort() : ListingSort.DEFAULT;
		Pageable pageable = PageRequest.of(page, size, sort.toSort());
		return PageResponse.from(this.listingRepository
			.findPublished(searchPattern(criteria.search()), criteria.assetType(), criteria.marketplaceMode(),
					criteria.category(), criteria.stage(), pageable)
			.map(ListingCardResponse::from));
	}

	static String searchPattern(String search) {
		if (search == null || search.isBlank()) {
			return null;
		}
		String escaped = search.strip()
			.toLowerCase(Locale.ROOT)
			.replace("!", "!!")
			.replace("%", "!%")
			.replace("_", "!_");
		return "%" + escaped + "%";
	}

	@Transactional(readOnly = true)
	public ListingDetailResponse publishedListing(String slug) {
		return this.listingRepository.findPublicBySlug(slug)
			.map(ListingDetailResponse::from)
			.orElseThrow(ListingService::notFound);
	}

	@Transactional
	public ListingDetailResponse create(ListingRequest request) {
		User owner = this.userService.currentActiveUser();
		String title = request.title().strip();
		String slug = uniqueSlug(title);
		this.rateLimiter.acquire(RateLimitOperation.LISTING_CREATE, this.currentUser.id().toString());
		Listing listing = new Listing(owner, title, slug, request.shortPitch().strip(),
				request.description(), request.assetType(), request.marketplaceMode(), request.category(),
				request.stage());
		applyContent(listing, request);
		try {
			this.listingRepository.saveAndFlush(listing);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"The listing could not be created because of a conflicting listing. Please try again.");
		}
		return ListingDetailResponse.from(listing);
	}

	@Transactional(readOnly = true)
	public PageResponse<MyListingSummaryResponse> myListings(int page, int size) {
		Pageable pageable = PageRequest.of(page, size, MINE_ORDER);
		return PageResponse.from(this.listingRepository.findByOwnerId(this.currentUser.id(), pageable).map(MyListingSummaryResponse::from));
	}

	@Transactional(readOnly = true)
	public ListingDetailResponse myListing(Long listingId) {
		return ListingDetailResponse.from(ownedListing(listingId));
	}

	@Transactional
	public ListingDetailResponse update(Long listingId, ListingRequest request) {
		this.userService.currentActiveUser();
		Listing listing = ownedListing(listingId);
		transition(listing::requireEditable);
		listing.setTitle(request.title().strip());
		listing.setShortPitch(request.shortPitch().strip());
		listing.setDescription(request.description());
		listing.setAssetType(request.assetType());
		listing.setMarketplaceMode(request.marketplaceMode());
		listing.setCategory(request.category());
		listing.setStage(request.stage());
		applyContent(listing, request);
		this.rateLimiter.acquire(RateLimitOperation.LISTING_UPDATE, this.currentUser.id().toString());
		this.listingRepository.saveAndFlush(listing);
		return ListingDetailResponse.from(listing);
	}

	@Transactional
	public ListingDetailResponse publish(Long listingId) {
		this.userService.currentActiveUser();
		Listing listing = ownedListing(listingId);
		transition(listing::publish);
		this.rateLimiter.acquire(RateLimitOperation.LISTING_PUBLISH, this.currentUser.id().toString());
		this.listingRepository.saveAndFlush(listing);
		return ListingDetailResponse.from(listing);
	}

	@Transactional
	public void archive(Long listingId) {
		this.userService.currentActiveUser();
		Listing listing = ownedListing(listingId);
		if (listing.getStatus() != ListingStatus.ARCHIVED) {
			transition(listing::archive);
			this.rateLimiter.acquire(RateLimitOperation.LISTING_ARCHIVE, this.currentUser.id().toString());
		}
	}

	private Listing ownedListing(Long listingId) {
		return this.listingRepository.findByIdAndOwnerId(listingId, this.currentUser.id())
			.orElseThrow(ListingService::notFound);
	}

	private static void applyContent(Listing listing, ListingRequest request) {
		listing.setProblem(blankToNull(request.problem()));
		listing.setSolution(blankToNull(request.solution()));
		listing.setCollaborationDetails(blankToNull(request.collaborationDetails()));
		listing.setPriceNegotiable(request.priceNegotiableOrDefault());
		if (request.askingPrice() != null) {
			listing.setAskingPrice(request.askingPrice(), request.currency());
		}
		else {
			listing.clearAskingPrice();
		}
	}

	private String uniqueSlug(String title) {
		for (int attempt = 0; attempt < MAX_SLUG_ATTEMPTS; attempt++) {
			String slug = this.slugGenerator.generate(title);
			if (!this.listingRepository.existsBySlug(slug)) {
				return slug;
			}
		}
		throw new ResponseStatusException(HttpStatus.CONFLICT,
				"A unique URL for this listing could not be generated. Please try again.");
	}

	private static void transition(Runnable transition) {
		try {
			transition.run();
		}
		catch (ListingStateException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
		}
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found.");
	}

}
