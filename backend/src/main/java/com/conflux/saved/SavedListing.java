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

	protected SavedListing() {
	}

	public SavedListing(User user, Listing listing) {
		this.user = Objects.requireNonNull(user, "user must not be null");
		this.listing = Objects.requireNonNull(listing, "listing must not be null");
	}

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
