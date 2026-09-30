package com.conflux.connection;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.conflux.listing.Listing;
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

/**
 * A user's interest in a specific listing, answered by the listing's owner. There is no
 * owner column: the owner of a connection is always {@code listing.owner}, which never
 * changes. At most one connection exists per (requester, listing); the database enforces it.
 * <p>
 * The status only changes through {@link #accept()}, {@link #reject()} and
 * {@link #withdraw()}, all of which require PENDING. Who may call which is decided by
 * {@link ConnectionService}.
 */
@Entity
@Table(name = "connections")
public class Connection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "listing_id", nullable = false, updatable = false)
	private Listing listing;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_id", nullable = false, updatable = false)
	private User requester;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ConnectionStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * For JPA only.
	 */
	protected Connection() {
	}

	/**
	 * A new PENDING expression of interest by {@code requester} in {@code listing}.
	 */
	public Connection(Listing listing, User requester) {
		this.listing = Objects.requireNonNull(listing, "listing must not be null");
		this.requester = Objects.requireNonNull(requester, "requester must not be null");
		this.status = ConnectionStatus.PENDING;
	}

	/**
	 * PENDING to ACCEPTED.
	 * @throws ConnectionStateException for any other status
	 */
	public void accept() {
		requirePending("accepted");
		this.status = ConnectionStatus.ACCEPTED;
	}

	/**
	 * PENDING to REJECTED.
	 * @throws ConnectionStateException for any other status
	 */
	public void reject() {
		requirePending("rejected");
		this.status = ConnectionStatus.REJECTED;
	}

	/**
	 * PENDING to WITHDRAWN.
	 * @throws ConnectionStateException for any other status
	 */
	public void withdraw() {
		requirePending("withdrawn");
		this.status = ConnectionStatus.WITHDRAWN;
	}

	private void requirePending(String action) {
		if (this.status != ConnectionStatus.PENDING) {
			throw new ConnectionStateException(
					"A connection with status " + this.status + " cannot be " + action + ".");
		}
	}

	// Same approach as the other entities: UTC, truncated to the DATETIME(6) precision.
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

	public Long getId() {
		return this.id;
	}

	public Listing getListing() {
		return this.listing;
	}

	public User getRequester() {
		return this.requester;
	}

	/**
	 * The listing's owner, the only one who may accept or reject.
	 */
	public User getOwner() {
		return this.listing.getOwner();
	}

	public ConnectionStatus getStatus() {
		return this.status;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
