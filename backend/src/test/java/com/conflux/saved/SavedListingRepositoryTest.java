package com.conflux.saved;

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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SavedListingRepositoryTest {

	@Autowired
	private SavedListingRepository savedListingRepository;

	@Autowired
	private ListingRepository listingRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private Flyway flyway;

	private User owner;

	private User saver;

	@BeforeEach
	void setUp() {
		this.owner = this.userRepository.save(new User("owner@example.com", "hash", "owner", "Owner"));
		this.saver = this.userRepository.save(new User("saver@example.com", "hash", "saver", "Saver"));
	}

	@Test
	void v4MigrationCreatesSavedListingsTableWithConstraints() {
		assertThat(this.flyway.info().applied()).filteredOn(m -> "4".equals(m.getVersion().getVersion()))
			.singleElement()
			.satisfies(m -> {
				assertThat(m.getScript()).isEqualTo("V4__create_saved_listings_table.sql");
				assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS);
			});

		assertThat(nativeList("SELECT column_name FROM information_schema.columns WHERE table_schema = SCHEMA() "
				+ "AND table_name = 'saved_listings' ORDER BY ordinal_position"))
			.containsExactly("id", "user_id", "listing_id", "created_at");
		assertThat(nativeMap("SELECT column_name, is_nullable FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'saved_listings'"))
			.containsEntry("user_id", "NO")
			.containsEntry("listing_id", "NO")
			.containsEntry("created_at", "NO");
		assertThat(nativeMap("SELECT constraint_name, constraint_type FROM information_schema.table_constraints "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'saved_listings'"))
			.containsEntry("pk_saved_listings", "PRIMARY KEY")
			.containsEntry("uk_saved_listings_user_listing", "UNIQUE")
			.containsEntry("fk_saved_listings_user", "FOREIGN KEY")
			.containsEntry("fk_saved_listings_listing", "FOREIGN KEY");
		assertThat(nativeMap("SELECT constraint_name, delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = SCHEMA() AND constraint_name LIKE 'fk_saved_listings_%'"))
			.containsEntry("fk_saved_listings_user", "RESTRICT")
			.containsEntry("fk_saved_listings_listing", "RESTRICT");
		assertThat(nativeList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema = SCHEMA() "
				+ "AND constraint_name = 'uk_saved_listings_user_listing' ORDER BY ordinal_position"))
			.containsExactly("user_id", "listing_id");
		assertThat(nativeList("SELECT index_name FROM information_schema.indexes WHERE table_schema = SCHEMA() "
				+ "AND table_name = 'saved_listings'"))
			.contains("idx_saved_listings_listing_id");
	}

	@Test
	void theSameUserCannotSaveTheSameListingTwice() {
		Listing listing = publishedListing("unique-pair");
		this.savedListingRepository.saveAndFlush(new SavedListing(this.saver, listing));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.savedListingRepository.saveAndFlush(new SavedListing(this.saver, listing)));
	}

	@Test
	void differentUsersMaySaveTheSameListingAndTheOwnerMaySaveTheirOwn() {
		Listing listing = publishedListing("shared");
		this.savedListingRepository.saveAndFlush(new SavedListing(this.saver, listing));
		this.savedListingRepository.saveAndFlush(new SavedListing(this.owner, listing));

		assertThat(this.savedListingRepository.count()).isEqualTo(2);
	}

	@Test
	void savesReferencingMissingUsersOrListingsAreRejected() {
		Listing listing = publishedListing("fk-check");
		assertThatExceptionOfType(PersistenceException.class).isThrownBy(() -> execute(
				"INSERT INTO saved_listings (user_id, listing_id, created_at) VALUES (999999, " + listing.getId()
						+ ", CURRENT_TIMESTAMP)"));
	}

	@Test
	void savedRowsBlockPhysicalDeletionOfTheirListingInsteadOfCascading() {
		Listing listing = publishedListing("no-cascade");
		this.savedListingRepository.saveAndFlush(new SavedListing(this.saver, listing));

		assertThatExceptionOfType(PersistenceException.class)
			.isThrownBy(() -> execute("DELETE FROM listings WHERE id = " + listing.getId()));
	}

	@Test
	void lookupAndDeleteAreScopedToTheUserAndListing() {
		Listing listing = publishedListing("lookup");
		SavedListing saved = this.savedListingRepository.saveAndFlush(new SavedListing(this.saver, listing));
		assertThat(saved.getCreatedAt()).isNotNull();

		assertThat(this.savedListingRepository.existsByUserIdAndListingId(this.saver.getId(), listing.getId())).isTrue();
		assertThat(this.savedListingRepository.existsByUserIdAndListingId(this.owner.getId(), listing.getId()))
			.isFalse();
		assertThat(this.savedListingRepository.findByUserIdAndListingId(this.saver.getId(), listing.getId()))
			.map(SavedListing::getId)
			.contains(saved.getId());
		assertThat(this.savedListingRepository.findByUserIdAndListingId(this.owner.getId(), listing.getId())).isEmpty();

		this.savedListingRepository.deleteByUserIdAndListingId(this.owner.getId(), listing.getId());
		assertThat(this.savedListingRepository.count()).isEqualTo(1);
		this.savedListingRepository.deleteByUserIdAndListingId(this.saver.getId(), listing.getId());
		assertThat(this.savedListingRepository.count()).isZero();
	}

	@Test
	void publishedSavesQueryFetchesListingAndOwnerAndSkipsUnpublishedListings() {
		Listing published = publishedListing("still-public");
		Listing draft = this.listingRepository.save(listing("still-draft"));
		Listing archived = listing("now-archived");
		archived.publish();
		archived.archive();
		this.listingRepository.save(archived);
		for (Listing listing : List.of(published, draft, archived)) {
			this.savedListingRepository.save(new SavedListing(this.saver, listing));
		}
		this.savedListingRepository.save(new SavedListing(this.owner, published));
		this.entityManager.flush();
		this.entityManager.clear();

		List<SavedListing> saves = this.savedListingRepository
			.findPublishedByUserId(this.saver.getId(), PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"))))
			.getContent();

		assertThat(saves).hasSize(1);
		assertThat(Hibernate.isInitialized(saves.get(0).getListing())).isTrue();
		assertThat(Hibernate.isInitialized(saves.get(0).getListing().getOwner())).isTrue();
		assertThat(saves.get(0).getListing().getSlug()).isEqualTo("still-public");
		assertThat(this.savedListingRepository.count()).isEqualTo(4);
	}

	private Listing publishedListing(String slug) {
		Listing listing = listing(slug);
		listing.publish();
		return this.listingRepository.save(listing);
	}

	private Listing listing(String slug) {
		return new Listing(this.owner, "Title", slug, "Pitch", "Description", ListingAssetType.IDEA,
				ListingMarketplaceMode.ACQUIRE, ListingCategory.AI, ListingStage.CONCEPT);
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
