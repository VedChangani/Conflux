package com.conflux.listing;

import java.util.Locale;

import com.conflux.auth.CurrentUser;
import com.conflux.common.web.PageResponse;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserStatus;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Listing use cases. Owner operations act for the authenticated user ({@link CurrentUser},
 * from the verified JWT) and only ever load listings through owner-scoped queries, so
 * another user's listing behaves exactly like a missing one (404). Responses are mapped inside the
 * transaction.
 */
@Service
public class ListingService {

	static final int MAX_SLUG_ATTEMPTS = 3;

	private static final Sort MINE_ORDER = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

	private final ListingRepository listingRepository;

	private final UserRepository userRepository;

	private final SlugGenerator slugGenerator;

	private final CurrentUser currentUser;

	public ListingService(ListingRepository listingRepository, UserRepository userRepository,
			SlugGenerator slugGenerator, CurrentUser currentUser) {
		this.listingRepository = listingRepository;
		this.userRepository = userRepository;
		this.slugGenerator = slugGenerator;
		this.currentUser = currentUser;
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
	 */
	@Transactional
	public ListingDetailResponse create(ListingRequest request) {
		User owner = this.userRepository.findById(this.currentUser.id())
			.orElseThrow(() -> new InvalidBearerTokenException("Token subject does not match an account"));
		requireActive(owner);
		String title = request.title().strip();
		Listing listing = new Listing(owner, title, uniqueSlug(title), request.shortPitch().strip(),
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
	 * @throws ResponseStatusException 404 if not owned, 409 if ARCHIVED or SUSPENDED
	 */
	@Transactional
	public ListingDetailResponse update(Long listingId, ListingRequest request) {
		Listing listing = ownedListing(listingId);
		requireActive(listing.getOwner());
		transition(listing::requireEditable);
		listing.setTitle(request.title().strip());
		listing.setShortPitch(request.shortPitch().strip());
		listing.setDescription(request.description());
		listing.setAssetType(request.assetType());
		listing.setMarketplaceMode(request.marketplaceMode());
		listing.setCategory(request.category());
		listing.setStage(request.stage());
		applyContent(listing, request);
		// Flush so the response carries the updated timestamp.
		this.listingRepository.saveAndFlush(listing);
		return ListingDetailResponse.from(listing);
	}

	/**
	 * DRAFT to PUBLISHED: the listing is public immediately, without any review.
	 * @throws ResponseStatusException 404 if not owned, 409 unless DRAFT
	 */
	@Transactional
	public ListingDetailResponse publish(Long listingId) {
		Listing listing = ownedListing(listingId);
		requireActive(listing.getOwner());
		transition(listing::publish);
		this.listingRepository.saveAndFlush(listing);
		return ListingDetailResponse.from(listing);
	}

	/**
	 * Soft delete: the row is kept with status ARCHIVED.
	 * @throws ResponseStatusException 404 if not owned, 409 if SUSPENDED
	 */
	@Transactional
	public void archive(Long listingId) {
		Listing listing = ownedListing(listingId);
		requireActive(listing.getOwner());
		transition(listing::archive);
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

	// Suspended accounts may still read their listings but not change them.
	private static void requireActive(User user) {
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is suspended.");
		}
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
