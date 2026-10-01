package com.conflux.message;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.conflux.connection.Connection;
import com.conflux.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversations")
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "connection_id", nullable = false, updatable = false, unique = true)
	private Connection connection;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Conversation() {
	}

	public Conversation(Connection connection) {
		this.connection = Objects.requireNonNull(connection, "connection must not be null");
	}

	public boolean isParticipant(Long userId) {
		return this.connection.getRequester().getId().equals(userId)
				|| this.connection.getOwner().getId().equals(userId);
	}

	public User otherParticipant(Long userId) {
		if (this.connection.getRequester().getId().equals(userId)) {
			return this.connection.getOwner();
		}
		if (this.connection.getOwner().getId().equals(userId)) {
			return this.connection.getRequester();
		}
		throw new IllegalArgumentException("not a participant");
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return this.id;
	}

	public Connection getConnection() {
		return this.connection;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
