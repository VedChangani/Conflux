package com.conflux.listing;

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
 * Listing use cases. Every owner operation takes the authenticated user's id (from the
 * JWT) and only ever loads listings through owner-scoped queries, so another user's
 * listing behaves exactly like a missing one (404). Responses are mapped inside the
 * transaction.
 */
@Service
public class ListingService {

	static final int MAX_SLUG_ATTEMPTS = 3;

	private static final Sort PUBLIC_ORDER = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

	private static final Sort MINE_ORDER = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

	private final ListingRepository listingRepository;

	private final UserRepository userRepository;

	private final SlugGenerator slugGenerator;

	public ListingService(ListingRepository listingRepository, UserRepository userRepository,
			SlugGenerator slugGenerator) {
		this.listingRepository = listingRepository;
		this.userRepository = userRepository;
		this.slugGenerator = slugGenerator;
	}

	// ---- Public ---------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<ListingCardResponse> publishedListings(int page, int size) {
		Pageable pageable = PageRequest.of(page, size, PUBLIC_ORDER);
		return PageResponse
			.from(this.listingRepository.findByStatus(ListingStatus.PUBLISHED, pageable).map(ListingCardResponse::from));
	}

	/**
	 * @throws ResponseStatusException 404 unless the listing exists and is PUBLISHED
	 */
	@Transactional(readOnly = true)
	public ListingDetailResponse publishedListing(String slug) {
		return this.listingRepository.findBySlugAndStatus(slug, ListingStatus.PUBLISHED)
			.map(ListingDetailResponse::from)
			.orElseThrow(ListingService::notFound);
	}

	// ---- Owner ----------------------------------------------------------------------

	/**
	 * Creates a DRAFT listing owned by {@code userId} with a server-generated slug.
	 * @throws ResponseStatusException 409 if no unique slug could be stored
	 */
	@Transactional
	public ListingDetailResponse create(Long userId, ListingRequest request) {
		User owner = this.userRepository.findById(userId)
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
	public PageResponse<MyListingSummaryResponse> myListings(Long userId, int page, int size) {
		Pageable pageable = PageRequest.of(page, size, MINE_ORDER);
		return PageResponse.from(this.listingRepository.findByOwnerId(userId, pageable).map(MyListingSummaryResponse::from));
	}

	@Transactional(readOnly = true)
	public ListingDetailResponse myListing(Long userId, Long listingId) {
		return ListingDetailResponse.from(ownedListing(userId, listingId));
	}

	/**
	 * Replaces the editable content. Editing a PUBLISHED listing sends it back to review.
	 * @throws ResponseStatusException 404 if not owned, 409 if the status forbids editing
	 */
	@Transactional
	public ListingDetailResponse update(Long userId, Long listingId, ListingRequest request) {
		Listing listing = ownedListing(userId, listingId);
		requireActive(listing.getOwner());
		transition(listing::beginOwnerEdit);
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
	 * @throws ResponseStatusException 404 if not owned, 409 unless DRAFT or REJECTED
	 */
	@Transactional
	public ListingDetailResponse submitForReview(Long userId, Long listingId) {
		Listing listing = ownedListing(userId, listingId);
		requireActive(listing.getOwner());
		transition(listing::submitForReview);
		this.listingRepository.saveAndFlush(listing);
		return ListingDetailResponse.from(listing);
	}

	/**
	 * Soft delete: the row is kept with status ARCHIVED.
	 * @throws ResponseStatusException 404 if not owned, 409 if SUSPENDED
	 */
	@Transactional
	public void archive(Long userId, Long listingId) {
		Listing listing = ownedListing(userId, listingId);
		requireActive(listing.getOwner());
		transition(listing::archive);
	}

	// ---- Helpers --------------------------------------------------------------------

	private Listing ownedListing(Long userId, Long listingId) {
		return this.listingRepository.findByIdAndOwnerId(listingId, userId).orElseThrow(ListingService::notFound);
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
