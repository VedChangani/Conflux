package com.conflux.listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Pattern;

import com.conflux.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "listings")
public class Listing {

	public static final int TITLE_MAX_LENGTH = 120;

	public static final int SLUG_MAX_LENGTH = 160;

	public static final int SHORT_PITCH_MAX_LENGTH = 240;

	private static final int PRICE_PRECISION = 15;

	private static final int PRICE_SCALE = 2;

	private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

	private static final Pattern CURRENCY_PATTERN = Pattern.compile("[A-Z]{3}");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private User owner;

	@Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(name = "slug", nullable = false, length = SLUG_MAX_LENGTH, updatable = false)
	private String slug;

	@Column(name = "short_pitch", nullable = false, length = SHORT_PITCH_MAX_LENGTH)
	private String shortPitch;

	@Column(name = "description", nullable = false)
	private String description;

	@Column(name = "problem")
	private String problem;

	@Column(name = "solution")
	private String solution;

	@Enumerated(EnumType.STRING)
	@Column(name = "asset_type", nullable = false, length = 20)
	private ListingAssetType assetType;

	@Enumerated(EnumType.STRING)
	@Column(name = "marketplace_mode", nullable = false, length = 20)
	private ListingMarketplaceMode marketplaceMode;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 30)
	private ListingCategory category;

	@Enumerated(EnumType.STRING)
	@Column(name = "stage", nullable = false, length = 20)
	private ListingStage stage;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ListingStatus status;

	@Column(name = "asking_price", precision = PRICE_PRECISION, scale = PRICE_SCALE)
	private BigDecimal askingPrice;

	@Column(name = "currency", length = 3)
	private String currency;

	@Column(name = "price_negotiable", nullable = false)
	private boolean priceNegotiable;

	@Column(name = "collaboration_details")
	private String collaborationDetails;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Listing() {
	}

	public Listing(User owner, String title, String slug, String shortPitch, String description,
			ListingAssetType assetType, ListingMarketplaceMode marketplaceMode, ListingCategory category,
			ListingStage stage) {
		this.owner = Objects.requireNonNull(owner, "owner must not be null");
		this.slug = requireSlug(slug);
		setTitle(title);
		setShortPitch(shortPitch);
		setDescription(description);
		setAssetType(assetType);
		setMarketplaceMode(marketplaceMode);
		setCategory(category);
		setStage(stage);
		this.status = ListingStatus.DRAFT;
		this.priceNegotiable = false;
	}

	@PrePersist
	void onCreate() {
		Instant now = now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = now();
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public void setAskingPrice(BigDecimal askingPrice, String currency) {
		Objects.requireNonNull(askingPrice, "askingPrice must not be null; use clearAskingPrice()");
		Objects.requireNonNull(currency, "currency is required when an asking price is set");
		if (askingPrice.signum() < 0) {
			throw new IllegalArgumentException("askingPrice must not be negative");
		}
		if (askingPrice.scale() > PRICE_SCALE) {
			throw new IllegalArgumentException("askingPrice must have at most " + PRICE_SCALE + " decimal places");
		}
		if (askingPrice.precision() - askingPrice.scale() > PRICE_PRECISION - PRICE_SCALE) {
			throw new IllegalArgumentException(
					"askingPrice must have at most " + (PRICE_PRECISION - PRICE_SCALE) + " integer digits");
		}
		if (!CURRENCY_PATTERN.matcher(currency).matches()) {
			throw new IllegalArgumentException("currency must be a three-letter upper-case code such as USD");
		}
		this.askingPrice = askingPrice;
		this.currency = currency;
	}

	public void clearAskingPrice() {
		this.askingPrice = null;
		this.currency = null;
	}

	public void publish() {
		if (this.status != ListingStatus.DRAFT) {
			throw notAllowed("published");
		}
		this.status = ListingStatus.PUBLISHED;
		this.publishedAt = now();
	}

	public void requireEditable() {
		switch (this.status) {
			case DRAFT, PUBLISHED -> {
			}
			case ARCHIVED, SUSPENDED -> throw notAllowed("edited");
		}
	}

	public void archive() {
		switch (this.status) {
			case DRAFT, PUBLISHED -> this.status = ListingStatus.ARCHIVED;
			case ARCHIVED -> {
			}
			case SUSPENDED -> throw notAllowed("archived");
		}
	}

	void suspend() {
		if (this.status != ListingStatus.PUBLISHED) {
			throw notAllowed("suspended");
		}
		this.status = ListingStatus.SUSPENDED;
	}

	void restore() {
		if (this.status != ListingStatus.SUSPENDED) {
			throw notAllowed("restored");
		}
		this.status = ListingStatus.PUBLISHED;
	}

	private ListingStateException notAllowed(String action) {
		return new ListingStateException("A listing with status " + this.status + " cannot be " + action + ".");
	}

	private static String requireSlug(String slug) {
		Objects.requireNonNull(slug, "slug must not be null");
		requireMaxLength(slug, SLUG_MAX_LENGTH, "slug");
		if (!SLUG_PATTERN.matcher(slug).matches()) {
			throw new IllegalArgumentException(
					"slug must consist of lower-case letters and digits separated by single hyphens");
		}
		return slug;
	}

	private static String requireMaxLength(String value, int maxLength, String name) {
		if (value.codePointCount(0, value.length()) > maxLength) {
			throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
		}
		return value;
	}

	public Long getId() {
		return this.id;
	}

	public User getOwner() {
		return this.owner;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		Objects.requireNonNull(title, "title must not be null");
		this.title = requireMaxLength(title, TITLE_MAX_LENGTH, "title");
	}

	public String getSlug() {
		return this.slug;
	}

	public String getShortPitch() {
		return this.shortPitch;
	}

	public void setShortPitch(String shortPitch) {
		Objects.requireNonNull(shortPitch, "shortPitch must not be null");
		this.shortPitch = requireMaxLength(shortPitch, SHORT_PITCH_MAX_LENGTH, "shortPitch");
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = Objects.requireNonNull(description, "description must not be null");
	}

	public String getProblem() {
		return this.problem;
	}

	public void setProblem(String problem) {
		this.problem = problem;
	}

	public String getSolution() {
		return this.solution;
	}

	public void setSolution(String solution) {
		this.solution = solution;
	}

	public ListingAssetType getAssetType() {
		return this.assetType;
	}

	public void setAssetType(ListingAssetType assetType) {
		this.assetType = Objects.requireNonNull(assetType, "assetType must not be null");
	}

	public ListingMarketplaceMode getMarketplaceMode() {
		return this.marketplaceMode;
	}

	public void setMarketplaceMode(ListingMarketplaceMode marketplaceMode) {
		this.marketplaceMode = Objects.requireNonNull(marketplaceMode, "marketplaceMode must not be null");
	}

	public ListingCategory getCategory() {
		return this.category;
	}

	public void setCategory(ListingCategory category) {
		this.category = Objects.requireNonNull(category, "category must not be null");
	}

	public ListingStage getStage() {
		return this.stage;
	}

	public void setStage(ListingStage stage) {
		this.stage = Objects.requireNonNull(stage, "stage must not be null");
	}

	public ListingStatus getStatus() {
		return this.status;
	}

	public BigDecimal getAskingPrice() {
		return this.askingPrice;
	}

	public String getCurrency() {
		return this.currency;
	}

	public boolean isPriceNegotiable() {
		return this.priceNegotiable;
	}

	public void setPriceNegotiable(boolean priceNegotiable) {
		this.priceNegotiable = priceNegotiable;
	}

	public String getCollaborationDetails() {
		return this.collaborationDetails;
	}

	public void setCollaborationDetails(String collaborationDetails) {
		this.collaborationDetails = collaborationDetails;
	}

	public Instant getPublishedAt() {
		return this.publishedAt;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
