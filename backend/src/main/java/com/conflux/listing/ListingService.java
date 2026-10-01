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

/**
 * Listing use cases. Owner operations act for the authenticated user ({@link CurrentUser},
 * from the verified JWT) and only ever load listings through owner-scoped queries, so
 * another user's listing behaves exactly like a missing one (404). Responses are mapped inside the
 * transaction.
 * <p>
 * Every write checks, in this order: the account (403 if suspended), ownership (404), the
 * listing's status (409), and only then the owner's rate limit (429). A write that changes
 * nothing (archiving an archived listing) is answered normally without using the allowance.
 * Changes applied in memory before a 429 are never flushed: the transaction rolls back.
 */
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

	// ---- Public ---------------------------------------------------------------------

	/**
	 * Public marketplace discovery: PUBLISHED listings only, filtered by the given criteria
	 * (AND) and ordered by the chosen {@link ListingSort}.
	 */
	@Transactional(readOnly = true)
	public PageResponse<ListingCardResponse> discover(ListingDiscoveryCriteria criteria, int page, int size) {
		ListingSort sort = (criteria.sort() != null) ? criteria.sort() : ListingSort.DEFAULT;
		Pageable pageable = PageRequest.of(page, size, sort.toSort());
		return PageResponse.from(this.listingRepository
			.findPublished(searchPattern(criteria.search()), criteria.assetType(), criteria.marketplaceMode(),
					criteria.category(), criteria.stage(), pageable)
			.map(ListingCardResponse::from));
	}

	/**
	 * Trimmed, lower-cased "contains" LIKE pattern, or {@code null} for a missing or blank
	 * search. LIKE wildcards typed by the user are escaped so they match literally.
	 */
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

	/**
	 * @throws ResponseStatusException 404 unless the listing is publicly visible (PUBLISHED
	 * and its owner ACTIVE)
	 */
	@Transactional(readOnly = true)
	public ListingDetailResponse publishedListing(String slug) {
		return this.listingRepository.findPublicBySlug(slug)
			.map(ListingDetailResponse::from)
			.orElseThrow(ListingService::notFound);
	}

	// ---- Owner ----------------------------------------------------------------------

	/**
	 * Creates a DRAFT listing owned by the current user with a server-generated slug.
	 * @throws ResponseStatusException 409 if no unique slug could be stored
	 * @throws com.conflux.ratelimit.RateLimitExceededException (429) if the user created too
	 * many listings recently
	 */
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
			// The existence check passed but a concurrent insert took the same slug.
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

	/**
	 * Replaces the editable content. The status and {@code publishedAt} are unchanged, so a
	 * PUBLISHED listing stays public.
	 * @throws ResponseStatusException 403 for a suspended account, 404 if not owned, 409 if
	 * ARCHIVED or SUSPENDED
	 * @throws com.conflux.ratelimit.RateLimitExceededException (429) if the owner edited too
	 * often recently
	 */
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
		// Flush so the response carries the updated timestamp.
		this.listingRepository.saveAndFlush(listing);
		return ListingDetailResponse.from(listing);
	}

	/**
	 * DRAFT to PUBLISHED: the listing is public immediately, without any review.
	 * @throws ResponseStatusException 403 for a suspended account, 404 if not owned, 409
	 * unless DRAFT
	 * @throws com.conflux.ratelimit.RateLimitExceededException (429) if the owner published
	 * too often recently
	 */
	@Transactional
	public ListingDetailResponse publish(Long listingId) {
		this.userService.currentActiveUser();
		Listing listing = ownedListing(listingId);
		transition(listing::publish);
		this.rateLimiter.acquire(RateLimitOperation.LISTING_PUBLISH, this.currentUser.id().toString());
		this.listingRepository.saveAndFlush(listing);
		return ListingDetailResponse.from(listing);
	}

	/**
	 * Soft delete: the row is kept with status ARCHIVED. Archiving an already archived
	 * listing does nothing (and does not count toward the rate limit).
	 * @throws ResponseStatusException 403 for a suspended account, 404 if not owned, 409 if
	 * SUSPENDED
	 * @throws com.conflux.ratelimit.RateLimitExceededException (429) if the owner archived too
	 * often recently
	 */
	@Transactional
	public void archive(Long listingId) {
		this.userService.currentActiveUser();
		Listing listing = ownedListing(listingId);
		if (listing.getStatus() != ListingStatus.ARCHIVED) {
			transition(listing::archive);
			this.rateLimiter.acquire(RateLimitOperation.LISTING_ARCHIVE, this.currentUser.id().toString());
		}
	}

	// ---- Helpers --------------------------------------------------------------------

	// Owner-scoped lookup for the current user: another user's listing is indistinguishable from a missing one.
	private Listing ownedListing(Long listingId) {
		return this.listingRepository.findByIdAndOwnerId(listingId, this.currentUser.id())
			.orElseThrow(ListingService::notFound);
	}

	// Optional fields: blank input is stored as null. Price and currency are set together.
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
