package com.conflux.connection;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.conflux.listing.Listing;
import com.conflux.listing.ListingAssetType;
import com.conflux.listing.ListingCategory;
import com.conflux.listing.ListingMarketplaceMode;
import com.conflux.listing.ListingRepository;
import com.conflux.listing.ListingStage;
import com.conflux.user.User;
import com.conflux.user.UserRepository;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * V5 schema and {@link ConnectionRepository} against the Flyway-migrated H2 database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ConnectionRepositoryTest {

	@Autowired
	private ConnectionRepository connectionRepository;

	@Autowired
	private ListingRepository listingRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private Flyway flyway;

	private User alice;

	private User bob;

	private User charlie;

	private Listing aliceListing;

	private Listing bobListing;

	@BeforeEach
	void setUp() {
		this.alice = this.userRepository.save(new User("alice@example.com", "hash", "alice", "Alice"));
		this.bob = this.userRepository.save(new User("bob@example.com", "hash", "bob", "Bob"));
		this.charlie = this.userRepository.save(new User("charlie@example.com", "hash", "charlie", "Charlie"));
		this.aliceListing = published(this.alice, "alice-listing");
		this.bobListing = published(this.bob, "bob-listing");
	}

	@Test
	void v5MigrationCreatesConnectionsTableWithConstraints() {
		assertThat(this.flyway.info().applied()).filteredOn(m -> "5".equals(m.getVersion().getVersion()))
			.singleElement()
			.satisfies(m -> {
				assertThat(m.getScript()).isEqualTo("V5__create_connections_table.sql");
				assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS);
			});
		assertThat(nativeList("SELECT column_name FROM information_schema.columns WHERE table_schema = SCHEMA() "
				+ "AND table_name = 'connections' ORDER BY ordinal_position"))
			.containsExactly("id", "listing_id", "requester_id", "status", "created_at", "updated_at");
		assertThat(nativeMap("SELECT column_name, is_nullable FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'connections'"))
			.containsEntry("listing_id", "NO")
			.containsEntry("requester_id", "NO")
			.containsEntry("status", "NO")
			.containsEntry("created_at", "NO")
			.containsEntry("updated_at", "NO");
		assertThat(nativeMap("SELECT column_name, data_type FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'connections'"))
			.containsEntry("status", "CHARACTER VARYING");
		assertThat(nativeMap("SELECT constraint_name, constraint_type FROM information_schema.table_constraints "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'connections'"))
			.containsEntry("pk_connections", "PRIMARY KEY")
			.containsEntry("uk_connections_requester_listing", "UNIQUE")
			.containsEntry("fk_connections_listing", "FOREIGN KEY")
			.containsEntry("fk_connections_requester", "FOREIGN KEY");
		assertThat(nativeList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema = SCHEMA() "
				+ "AND constraint_name = 'uk_connections_requester_listing' ORDER BY ordinal_position"))
			.containsExactly("requester_id", "listing_id");
		assertThat(nativeMap("SELECT constraint_name, delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = SCHEMA() AND constraint_name LIKE 'fk_connections_%'"))
			.containsEntry("fk_connections_listing", "RESTRICT")
			.containsEntry("fk_connections_requester", "RESTRICT");
		assertThat(nativeList("SELECT index_name FROM information_schema.indexes WHERE table_schema = SCHEMA() "
				+ "AND table_name = 'connections'"))
			.contains("idx_connections_listing_id");
		// No owner column: the owner is always the listing's owner.
		assertThat(nativeList("SELECT column_name FROM information_schema.columns WHERE table_schema = SCHEMA() "
				+ "AND table_name = 'connections'"))
			.doesNotContain("owner_id");
	}

	@Test
	void aRequesterHasAtMostOneConnectionPerListing() {
		this.connectionRepository.saveAndFlush(new Connection(this.aliceListing, this.bob));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.connectionRepository.saveAndFlush(new Connection(this.aliceListing, this.bob)));
	}

	@Test
	void statusIsStoredAsTextAndTimestampsAreSet() {
		Connection connection = new Connection(this.aliceListing, this.bob);
		connection.accept();
		Long id = this.connectionRepository.saveAndFlush(connection).getId();
		this.entityManager.clear();

		Object status = this.entityManager.getEntityManager()
			.createNativeQuery("SELECT status FROM connections WHERE id = " + id)
			.getSingleResult();
		assertThat(status).isEqualTo("ACCEPTED");
		Connection found = this.connectionRepository.findById(id).orElseThrow();
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isEqualTo(found.getCreatedAt());
	}

	@Test
	void historyBlocksPhysicalDeletionInsteadOfCascading() {
		this.connectionRepository.saveAndFlush(new Connection(this.aliceListing, this.bob));

		assertThatExceptionOfType(PersistenceException.class)
			.isThrownBy(() -> execute("DELETE FROM listings WHERE id = " + this.aliceListing.getId()));
	}

	@Test
	void queriesAreScopedToTheParticipantAndFetchEverythingNeeded() {
		Connection bobToAlice = this.connectionRepository.save(new Connection(this.aliceListing, this.bob));
		Connection charlieToAlice = this.connectionRepository.save(new Connection(this.aliceListing, this.charlie));
		Connection charlieToBob = this.connectionRepository.save(new Connection(this.bobListing, this.charlie));
		this.entityManager.flush();
		this.entityManager.clear();

		List<Connection> sentByCharlie = this.connectionRepository
			.findSent(this.charlie.getId(), null, PageRequest.of(0, 10))
			.getContent();
		assertThat(sentByCharlie).extracting(Connection::getId)
			.containsExactlyInAnyOrder(charlieToAlice.getId(), charlieToBob.getId());
		assertThat(Hibernate.isInitialized(sentByCharlie.get(0).getListing())).isTrue();
		assertThat(Hibernate.isInitialized(sentByCharlie.get(0).getListing().getOwner())).isTrue();
		assertThat(Hibernate.isInitialized(sentByCharlie.get(0).getRequester())).isTrue();

		assertThat(this.connectionRepository.findReceived(this.alice.getId(), null, PageRequest.of(0, 10)).getContent())
			.extracting(Connection::getId)
			.containsExactlyInAnyOrder(bobToAlice.getId(), charlieToAlice.getId());
		assertThat(this.connectionRepository.findReceived(this.bob.getId(), null, PageRequest.of(0, 10)).getContent())
			.extracting(Connection::getId)
			.containsExactly(charlieToBob.getId());
		assertThat(this.connectionRepository.findReceived(this.alice.getId(), ConnectionStatus.ACCEPTED,
				PageRequest.of(0, 10)))
			.isEmpty();

		// Requester and owner can see it, anyone else cannot.
		assertThat(this.connectionRepository.findForParticipant(bobToAlice.getId(), this.bob.getId())).isPresent();
		assertThat(this.connectionRepository.findForParticipant(bobToAlice.getId(), this.alice.getId())).isPresent();
		assertThat(this.connectionRepository.findForParticipant(bobToAlice.getId(), this.charlie.getId())).isEmpty();
		assertThat(this.connectionRepository.findByRequesterIdAndListingId(this.bob.getId(), this.aliceListing.getId()))
			.map(Connection::getId)
			.contains(bobToAlice.getId());
		assertThat(this.connectionRepository.findByRequesterIdAndListingId(this.alice.getId(), this.aliceListing.getId()))
			.isEmpty();
	}

	private Listing published(User owner, String slug) {
		Listing listing = new Listing(owner, "Title " + slug, slug, "Pitch", "Description", ListingAssetType.IDEA,
				ListingMarketplaceMode.COLLABORATE, ListingCategory.AI, ListingStage.CONCEPT);
		listing.publish();
		return this.listingRepository.save(listing);
	}

	private List<Object> nativeList(String sql) {
		return List.copyOf(this.entityManager.getEntityManager().createNativeQuery(sql).getResultList());
	}

	private Map<String, String> nativeMap(String sql) {
		List<?> rows = this.entityManager.getEntityManager().createNativeQuery(sql).getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.collect(Collectors.toMap(row -> (String) row[0], row -> ((String) row[1]).toUpperCase()));
	}

	private void execute(String sql) {
		this.entityManager.getEntityManager().createNativeQuery(sql).executeUpdate();
	}

}
