package com.conflux.saved;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.conflux.listing.Listing;
import com.conflux.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A listing saved (bookmarked) by a user. An explicit entity rather than a many-to-many
 * association so the relationship can carry its own data ({@code createdAt} now, more
 * later). The database guarantees at most one row per (user, listing).
 */
@Entity
@Table(name = "saved_listings")
public class SavedListing {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "listing_id", nullable = false, updatable = false)
	private Listing listing;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/**
	 * For JPA only.
	 */
	protected SavedListing() {
	}

	public SavedListing(User user, Listing listing) {
		this.user = Objects.requireNonNull(user, "user must not be null");
		this.listing = Objects.requireNonNull(listing, "listing must not be null");
	}

	// Same approach as User and Listing: UTC, truncated to the DATETIME(6) precision.
	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return this.id;
	}

	public User getUser() {
		return this.user;
	}

	public Listing getListing() {
		return this.listing;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
