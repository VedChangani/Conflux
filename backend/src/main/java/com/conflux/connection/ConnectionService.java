package com.conflux.connection;

import java.util.Optional;
import java.util.function.Consumer;

import com.conflux.auth.CurrentUser;
import com.conflux.common.web.PageResponse;
import com.conflux.listing.Listing;
import com.conflux.listing.ListingRepository;
import com.conflux.message.ConversationService;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Interest in listings and the owner's answer. The acting user always comes from the
 * verified JWT ({@link CurrentUser}). Rules:
 * <ul>
 * <li>anyone except the owner may express interest in a PUBLISHED listing, once;</li>
 * <li>only the listing owner may accept or reject, only the requester may withdraw;
 * accepting also creates the connection's conversation;</li>
 * <li>only the two participants can see a connection; for anyone else it does not exist
 * (404);</li>
 * <li>suspended accounts cannot change anything (403).</li>
 * </ul>
 */
@Service
public class ConnectionService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final ConnectionRepository connectionRepository;

	private final ListingRepository listingRepository;

	private final UserRepository userRepository;

	private final CurrentUser currentUser;

	private final ConversationService conversationService;

	private final EntityManager entityManager;

	public ConnectionService(ConnectionRepository connectionRepository, ListingRepository listingRepository,
			UserRepository userRepository, CurrentUser currentUser, ConversationService conversationService,
			EntityManager entityManager) {
		this.connectionRepository = connectionRepository;
		this.listingRepository = listingRepository;
		this.userRepository = userRepository;
		this.currentUser = currentUser;
		this.conversationService = conversationService;
		this.entityManager = entityManager;
	}

	/**
	 * Expresses the current user's interest in a PUBLISHED listing.
	 * <p>
	 * Deliberately not one transaction: if a concurrent request creates the same connection
	 * first, the unique constraint fails only the insert's own transaction and the existing
	 * connection is returned, exactly as for a repeated request.
	 * @return the new PENDING connection ({@code created}), or the existing PENDING or
	 * ACCEPTED one
	 * @throws ResponseStatusException 404 unless the listing is PUBLISHED; 409 for the
	 * listing's owner or if an earlier interest was rejected or withdrawn
	 */
	public InterestResult expressInterest(Long listingId) {
		User requester = activeUser();
		Listing listing = this.listingRepository.findPublicById(listingId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
		if (listing.getOwner().getId().equals(requester.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot express interest in your own listing.");
		}
		Optional<Connection> existing = existingConnection(requester, listing);
		if (existing.isPresent()) {
			return reuse(existing.get());
		}
		Connection connection = new Connection(listing, requester);
		try {
			this.connectionRepository.saveAndFlush(connection);
		}
		catch (DataIntegrityViolationException ex) {
			// Lost a race against an identical request: answer as for a repeated request.
			return reuse(existingConnection(requester, listing).orElseThrow(() -> ex));
		}
		return new InterestResult(ConnectionResponse.from(connection), true);
	}

	@Transactional(readOnly = true)
	public PageResponse<ConnectionResponse> sent(ConnectionStatus status, int page, int size) {
		return PageResponse.from(this.connectionRepository
			.findSent(this.currentUser.id(), status, PageRequest.of(page, size, NEWEST_FIRST))
			.map(ConnectionResponse::from));
	}

	@Transactional(readOnly = true)
	public PageResponse<ConnectionResponse> received(ConnectionStatus status, int page, int size) {
		return PageResponse.from(this.connectionRepository
			.findReceived(this.currentUser.id(), status, PageRequest.of(page, size, NEWEST_FIRST))
			.map(ConnectionResponse::from));
	}

	/**
	 * @throws ResponseStatusException 404 unless the current user is a participant
	 */
	@Transactional(readOnly = true)
	public ConnectionResponse detail(Long connectionId) {
		return ConnectionResponse.from(participantConnection(connectionId, this.currentUser.id()));
	}

	/**
	 * Listing owner only: PENDING to ACCEPTED, and the connection's conversation is created
	 * in the same transaction, so there is never an accepted connection without one (if
	 * creating the conversation fails, the acceptance is rolled back).
	 * @throws ResponseStatusException 409 if a concurrent request accepted it first
	 */
	@Transactional
	public ConnectionResponse accept(Long connectionId) {
		Connection connection = answerAsOwner(connectionId, Connection::accept);
		try {
			this.conversationService.createFor(connection);
		}
		catch (DataIntegrityViolationException ex) {
			// Another request accepted this connection and created its conversation first.
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been accepted.");
		}
		return ConnectionResponse.from(connection);
	}

	/**
	 * Listing owner only: PENDING to REJECTED. The connection is kept as history.
	 */
	@Transactional
	public ConnectionResponse reject(Long connectionId) {
		return ConnectionResponse.from(answerAsOwner(connectionId, Connection::reject));
	}

	/**
	 * Requester only: PENDING to WITHDRAWN. The connection is kept as history.
	 */
	@Transactional
	public void withdraw(Long connectionId) {
		User user = activeUser();
		Connection connection = lockedParticipantConnection(connectionId, user.getId());
		if (!connection.getRequester().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can withdraw this request.");
		}
		transition(connection, Connection::withdraw);
	}

	// ---- Helpers --------------------------------------------------------------------

	private Connection answerAsOwner(Long connectionId, Consumer<Connection> answer) {
		User user = activeUser();
		Connection connection = lockedParticipantConnection(connectionId, user.getId());
		if (!connection.getOwner().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Only the listing owner can accept or reject this request.");
		}
		transition(connection, answer);
		// Flush so the response carries the updated timestamp.
		this.connectionRepository.saveAndFlush(connection);
		return connection;
	}

	private void transition(Connection connection, Consumer<Connection> transition) {
		try {
			transition.accept(connection);
		}
		catch (ConnectionStateException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
		}
	}

	private Optional<Connection> existingConnection(User requester, Listing listing) {
		return this.connectionRepository.findByRequesterIdAndListingId(requester.getId(), listing.getId());
	}

	// A repeated interest returns the live connection; a closed one is not reopened.
	private static InterestResult reuse(Connection existing) {
		return switch (existing.getStatus()) {
			case PENDING, ACCEPTED -> new InterestResult(ConnectionResponse.from(existing), false);
			case REJECTED -> throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Your interest in this listing was declined and cannot be sent again.");
			case WITHDRAWN -> throw new ResponseStatusException(HttpStatus.CONFLICT,
					"You withdrew your interest in this listing; it cannot be sent again.");
		};
	}

	/**
	 * Like {@link #participantConnection}, then locks the connection row until the end of the
	 * transaction and re-reads it, so concurrent accept/reject/withdraw calls on the same
	 * connection run one after another and each sees the status the previous one committed
	 * (e.g. an accept racing a reject cannot both succeed).
	 */
	private Connection lockedParticipantConnection(Long connectionId, Long userId) {
		Connection connection = participantConnection(connectionId, userId);
		this.entityManager.refresh(connection, LockModeType.PESSIMISTIC_WRITE);
		return connection;
	}

	private Connection participantConnection(Long connectionId, Long userId) {
		return this.connectionRepository.findForParticipant(connectionId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found."));
	}

	// Same rule as elsewhere: suspended accounts cannot change anything.
	private User activeUser() {
		User user = this.userRepository.findById(this.currentUser.id())
			.orElseThrow(() -> new InvalidBearerTokenException("Token subject does not match an account"));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is suspended.");
		}
		return user;
	}

}
