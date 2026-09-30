package com.conflux.message;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

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
 * A plain-text message in a {@link Conversation}. Messages are immutable. The receiver and
 * the listing are not stored: they follow from the conversation's connection.
 */
@Entity
@Table(name = "messages")
public class Message {

	public static final int CONTENT_MAX_LENGTH = 5_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false, updatable = false)
	private Conversation conversation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_id", nullable = false, updatable = false)
	private User sender;

	@Column(name = "content", nullable = false, updatable = false)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/**
	 * For JPA only.
	 */
	protected Message() {
	}

	/**
	 * @param content already normalized (trimmed) text; not blank, at most
	 * {@value #CONTENT_MAX_LENGTH} characters
	 */
	public Message(Conversation conversation, User sender, String content) {
		this.conversation = Objects.requireNonNull(conversation, "conversation must not be null");
		this.sender = Objects.requireNonNull(sender, "sender must not be null");
		Objects.requireNonNull(content, "content must not be null");
		if (content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
			throw new IllegalArgumentException("content must be non-blank and at most " + CONTENT_MAX_LENGTH
					+ " characters");
		}
		this.content = content;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return this.id;
	}

	public Conversation getConversation() {
		return this.conversation;
	}

	public User getSender() {
		return this.sender;
	}

	public String getContent() {
		return this.content;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
