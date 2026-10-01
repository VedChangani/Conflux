package com.conflux.connection;

import java.util.Optional;
import java.util.function.Consumer;

import com.conflux.auth.CurrentUser;
import com.conflux.common.web.PageResponse;
import com.conflux.listing.Listing;
import com.conflux.listing.ListingRepository;
import com.conflux.message.ConversationService;
import com.conflux.ratelimit.RateLimitOperation;
import com.conflux.ratelimit.RateLimiter;
import com.conflux.user.User;
import com.conflux.user.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConnectionService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final ConnectionRepository connectionRepository;

	private final ListingRepository listingRepository;

	private final UserService userService;

	private final CurrentUser currentUser;

	private final ConversationService conversationService;

	private final EntityManager entityManager;

	private final RateLimiter rateLimiter;

	public ConnectionService(ConnectionRepository connectionRepository, ListingRepository listingRepository,
			UserService userService, CurrentUser currentUser, ConversationService conversationService,
			EntityManager entityManager, RateLimiter rateLimiter) {
		this.connectionRepository = connectionRepository;
		this.listingRepository = listingRepository;
		this.userService = userService;
		this.currentUser = currentUser;
		this.conversationService = conversationService;
		this.entityManager = entityManager;
		this.rateLimiter = rateLimiter;
	}

	public InterestResult expressInterest(Long listingId) {
		User requester = this.userService.currentActiveUser();
		Listing listing = this.listingRepository.findPublicById(listingId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
		if (listing.getOwner().getId().equals(requester.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot express interest in your own listing.");
		}
		Optional<Connection> existing = existingConnection(requester, listing);
		if (existing.isPresent()) {
			return reuse(existing.get());
		}
		this.rateLimiter.acquire(RateLimitOperation.LISTING_INTEREST, this.currentUser.id().toString());
		Connection connection = new Connection(listing, requester);
		try {
			this.connectionRepository.saveAndFlush(connection);
		}
		catch (DataIntegrityViolationException ex) {
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

	@Transactional(readOnly = true)
	public ConnectionResponse detail(Long connectionId) {
		return ConnectionResponse.from(participantConnection(connectionId, this.currentUser.id()));
	}

	@Transactional
	public ConnectionResponse accept(Long connectionId) {
		Connection connection = answerAsOwner(connectionId, Connection::accept);
		try {
			this.conversationService.createFor(connection);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been accepted.");
		}
		return ConnectionResponse.from(connection);
	}

	@Transactional
	public ConnectionResponse reject(Long connectionId) {
		return ConnectionResponse.from(answerAsOwner(connectionId, Connection::reject));
	}

	@Transactional
	public void withdraw(Long connectionId) {
		User user = this.userService.currentActiveUser();
		Connection connection = lockedParticipantConnection(connectionId, user.getId());
		if (!connection.getRequester().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can withdraw this request.");
		}
		transition(connection, Connection::withdraw);
		this.rateLimiter.acquire(RateLimitOperation.CONNECTION_WITHDRAW, this.currentUser.id().toString());
	}

	private Connection answerAsOwner(Long connectionId, Consumer<Connection> answer) {
		User user = this.userService.currentActiveUser();
		Connection connection = lockedParticipantConnection(connectionId, user.getId());
		if (!connection.getOwner().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Only the listing owner can accept or reject this request.");
		}
		transition(connection, answer);
		this.rateLimiter.acquire(RateLimitOperation.CONNECTION_DECISION, this.currentUser.id().toString());
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

	private static InterestResult reuse(Connection existing) {
		return switch (existing.getStatus()) {
			case PENDING, ACCEPTED -> new InterestResult(ConnectionResponse.from(existing), false);
			case REJECTED -> throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Your interest in this listing was declined and cannot be sent again.");
			case WITHDRAWN -> throw new ResponseStatusException(HttpStatus.CONFLICT,
					"You withdrew your interest in this listing; it cannot be sent again.");
		};
	}

	private Connection lockedParticipantConnection(Long connectionId, Long userId) {
		Connection connection = participantConnection(connectionId, userId);
		this.entityManager.refresh(connection, LockModeType.PESSIMISTIC_WRITE);
		return connection;
	}

	private Connection participantConnection(Long connectionId, Long userId) {
		return this.connectionRepository.findForParticipant(connectionId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found."));
	}

}
