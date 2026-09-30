package com.conflux.connection;

import java.util.List;
import java.util.function.Consumer;

import com.conflux.listing.Listing;
import com.conflux.listing.ListingAssetType;
import com.conflux.listing.ListingCategory;
import com.conflux.listing.ListingMarketplaceMode;
import com.conflux.listing.ListingStage;
import com.conflux.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Connection lifecycle rules, without a database.
 */
class ConnectionTest {

	private final User owner = new User("owner@example.com", "hash", "owner", "Owner");

	private final User requester = new User("requester@example.com", "hash", "requester", "Requester");

	private final Listing listing = new Listing(this.owner, "Title", "title-0000abcd", "Pitch", "Description",
			ListingAssetType.IDEA, ListingMarketplaceMode.COLLABORATE, ListingCategory.AI, ListingStage.CONCEPT);

	@Test
	void statusesAreExactlyTheMvpLifecycle() {
		assertThat(ConnectionStatus.values()).containsExactly(ConnectionStatus.PENDING, ConnectionStatus.ACCEPTED,
				ConnectionStatus.REJECTED, ConnectionStatus.WITHDRAWN);
	}

	@Test
	void newConnectionIsPendingAndItsOwnerIsTheListingOwner() {
		Connection connection = new Connection(this.listing, this.requester);

		assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.PENDING);
		assertThat(connection.getRequester()).isSameAs(this.requester);
		assertThat(connection.getOwner()).isSameAs(this.owner);
		assertThatNullPointerException().isThrownBy(() -> new Connection(null, this.requester));
		assertThatNullPointerException().isThrownBy(() -> new Connection(this.listing, null));
	}

	@Test
	void pendingCanBeAcceptedRejectedOrWithdrawn() {
		assertThat(transitioned(Connection::accept).getStatus()).isEqualTo(ConnectionStatus.ACCEPTED);
		assertThat(transitioned(Connection::reject).getStatus()).isEqualTo(ConnectionStatus.REJECTED);
		assertThat(transitioned(Connection::withdraw).getStatus()).isEqualTo(ConnectionStatus.WITHDRAWN);
	}

	@Test
	void terminalStatesCannotChangeAgain() {
		List<Consumer<Connection>> transitions = List.of(Connection::accept, Connection::reject, Connection::withdraw);
		List<String> verbs = List.of("accepted", "rejected", "withdrawn");
		for (Consumer<Connection> toTerminal : transitions) {
			for (int i = 0; i < transitions.size(); i++) {
				Connection connection = transitioned(toTerminal);
				ConnectionStatus terminal = connection.getStatus();
				Consumer<Connection> next = transitions.get(i);

				assertThatExceptionOfType(ConnectionStateException.class).isThrownBy(() -> next.accept(connection))
					.withMessage("A connection with status " + terminal + " cannot be " + verbs.get(i) + ".");
				assertThat(connection.getStatus()).isEqualTo(terminal);
			}
		}
	}

	@Test
	void thereIsNoPublicStatusSetter() {
		assertThat(Connection.class.getMethods()).extracting(java.lang.reflect.Method::getName)
			.doesNotContain("setStatus");
	}

	private Connection transitioned(Consumer<Connection> transition) {
		Connection connection = new Connection(this.listing, this.requester);
		transition.accept(connection);
		return connection;
	}

}
