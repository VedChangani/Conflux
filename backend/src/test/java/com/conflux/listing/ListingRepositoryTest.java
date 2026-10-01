package com.conflux.listing;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.conflux.user.User;
import com.conflux.user.UserRepository;
import com.conflux.user.UserRole;
import com.conflux.user.UserStatus;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.hibernate.Hibernate;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ListingRepositoryTest {

	@Autowired
	private ListingRepository listingRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private Flyway flyway;

	private User owner;

	@BeforeEach
	void createOwner() {
		this.owner = this.userRepository.saveAndFlush(new User("owner@example.com", "hash", "owner", "Owner"));
	}

	@Test
	void v2MigrationCreatesListingsTable() {
		MigrationInfo v2 = migration("2");
		assertThat(v2.getVersion().getVersion()).isEqualTo("2");
		assertThat(v2.getScript()).isEqualTo("V2__create_listings_table.sql");
		assertThat(v2.getState()).isEqualTo(MigrationState.SUCCESS);
		assertThat(this.flyway.info().applied()).extracting(m -> m.getVersion().getVersion()).contains("1", "2");

		assertThat(columns()).containsExactly("id", "owner_id", "title", "slug", "short_pitch", "description",
				"problem", "solution", "asset_type", "marketplace_mode", "category", "stage", "status", "asking_price",
				"currency", "price_negotiable", "collaboration_details", "published_at", "created_at", "updated_at");

		Map<String, String> types = stringMap("SELECT column_name, data_type FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'listings'");
		assertThat(types).containsEntry("title", "CHARACTER VARYING")
			.containsEntry("description", "CHARACTER VARYING")
			.containsEntry("asking_price", "NUMERIC")
			.containsEntry("price_negotiable", "BOOLEAN")
			.containsEntry("published_at", "TIMESTAMP");

		Object[] price = (Object[]) nativeQuery("SELECT numeric_precision, numeric_scale FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'listings' AND column_name = 'asking_price'")
			.getSingleResult();
		assertThat(((Number) price[0]).intValue()).isEqualTo(15);
		assertThat(((Number) price[1]).intValue()).isEqualTo(2);

		Map<String, String> constraints = stringMap("SELECT constraint_name, constraint_type "
				+ "FROM information_schema.table_constraints WHERE table_schema = SCHEMA() AND table_name = 'listings'");
		assertThat(constraints).containsEntry("pk_listings", "PRIMARY KEY")
			.containsEntry("uk_listings_slug", "UNIQUE")
			.containsEntry("fk_listings_owner", "FOREIGN KEY");

		Object deleteRule = nativeQuery("SELECT delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = SCHEMA() AND constraint_name = 'fk_listings_owner'")
			.getSingleResult();
		assertThat(deleteRule.toString()).isEqualToIgnoringCase("RESTRICT");

		assertThat(indexes()).contains("idx_listings_owner_id");
	}

	@Test
	void v3ReplacesStatusIndexWithStatusPublishedAtIndex() {
		MigrationInfo v3 = migration("3");
		assertThat(v3.getScript()).isEqualTo("V3__index_published_listings.sql");
		assertThat(v3.getState()).isEqualTo(MigrationState.SUCCESS);

		assertThat(indexes()).contains("idx_listings_owner_id", "idx_listings_status_published_at")
			.doesNotContain("idx_listings_status");
		List<?> indexColumns = nativeQuery("SELECT column_name FROM information_schema.index_columns "
				+ "WHERE table_schema = SCHEMA() AND index_name = 'idx_listings_status_published_at' "
				+ "ORDER BY ordinal_position")
			.getResultList();
		assertThat(indexColumns).extracting(Object::toString).containsExactly("status", "published_at");
	}

	@Test
	void requiredColumnsAreNotNullableAndOptionalColumnsAre() {
		Map<String, String> nullable = stringMap("SELECT column_name, is_nullable FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'listings'");

		for (String required : new String[] { "id", "owner_id", "title", "slug", "short_pitch", "description",
				"asset_type", "marketplace_mode", "category", "stage", "status", "price_negotiable", "created_at",
				"updated_at" }) {
			assertThat(nullable).as(required).containsEntry(required, "NO");
		}
		for (String optional : new String[] { "problem", "solution", "asking_price", "currency",
				"collaboration_details", "published_at" }) {
			assertThat(nullable).as(optional).containsEntry(optional, "YES");
		}
	}

	@Test
	void validRawInsertSucceeds() {
		assertThat(insertListingWithNull(null)).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "owner_id", "title", "slug", "short_pitch", "description", "asset_type",
			"marketplace_mode", "category", "stage", "status", "price_negotiable", "created_at", "updated_at" })
	void databaseRejectsNullInRequiredColumn(String column) {
		assertThatExceptionOfType(PersistenceException.class).isThrownBy(() -> insertListingWithNull(column));
	}

	@Test
	void persistsAndRetrievesListing() {
		Listing listing = newListing("ai-resume-builder");
		listing.setProblem("Tailoring resumes takes hours.");
		listing.setSolution("An assistant that rewrites resumes per job post.");
		listing.setCollaborationDetails("Looking for a technical cofounder and growth partner.");
		listing.setAskingPrice(new BigDecimal("25000.50"), "USD");
		listing.setPriceNegotiable(true);
		Long id = this.listingRepository.saveAndFlush(listing).getId();
		this.entityManager.clear();

		Listing found = this.listingRepository.findById(id).orElseThrow();

		assertThat(found.getId()).isEqualTo(id);
		assertThat(found.getOwner().getId()).isEqualTo(this.owner.getId());
		assertThat(found.getTitle()).isEqualTo("AI Resume Builder");
		assertThat(found.getSlug()).isEqualTo("ai-resume-builder");
		assertThat(found.getShortPitch()).isEqualTo("Tailored resumes in one click");
		assertThat(found.getDescription()).isEqualTo("A full description.\n\nWith paragraphs.");
		assertThat(found.getProblem()).isEqualTo("Tailoring resumes takes hours.");
		assertThat(found.getSolution()).isEqualTo("An assistant that rewrites resumes per job post.");
		assertThat(found.getAssetType()).isEqualTo(ListingAssetType.MVP);
		assertThat(found.getMarketplaceMode()).isEqualTo(ListingMarketplaceMode.ACQUIRE);
		assertThat(found.getCategory()).isEqualTo(ListingCategory.AI);
		assertThat(found.getStage()).isEqualTo(ListingStage.MVP);
		assertThat(found.getStatus()).isEqualTo(ListingStatus.DRAFT);
		assertThat(found.getAskingPrice()).isEqualByComparingTo("25000.50");
		assertThat(found.getCurrency()).isEqualTo("USD");
		assertThat(found.isPriceNegotiable()).isTrue();
		assertThat(found.getCollaborationDetails()).isEqualTo("Looking for a technical cofounder and growth partner.");
		assertThat(found.getPublishedAt()).isNull();
	}

	@Test
	void longTextFieldsRoundTripWithoutTruncation() {
		String longText = "Paragraph with unicode – résumé 🚀.\n".repeat(1500);
		Listing listing = newListing("long-text");
		listing.setDescription(longText);
		listing.setProblem(longText);
		listing.setSolution(longText);
		listing.setCollaborationDetails(longText);
		Long id = this.listingRepository.saveAndFlush(listing).getId();
		this.entityManager.clear();

		Listing found = this.listingRepository.findById(id).orElseThrow();
		assertThat(found.getDescription()).isEqualTo(longText);
		assertThat(found.getProblem()).isEqualTo(longText);
		assertThat(found.getSolution()).isEqualTo(longText);
		assertThat(found.getCollaborationDetails()).isEqualTo(longText);
	}

	@Test
	void ownerIdReferencesTheExistingUser() {
		Long id = this.listingRepository.saveAndFlush(newListing("owned")).getId();
		this.entityManager.clear();

		Object ownerId = nativeQuery("SELECT owner_id FROM listings WHERE id = ?1").setParameter(1, id)
			.getSingleResult();
		assertThat(((Number) ownerId).longValue()).isEqualTo(this.owner.getId());

		Listing found = this.listingRepository.findById(id).orElseThrow();
		assertThat(found.getOwner().getId()).isEqualTo(this.owner.getId());
		assertThat(found.getOwner().getUsername()).isEqualTo("owner");
	}

	@Test
	void foreignKeyRejectsUnknownOwner() {
		assertThatExceptionOfType(PersistenceException.class)
			.isThrownBy(() -> insertListing(Map.of("owner_id", "999999", "slug", "'orphan'")));
	}

	@Test
	void ownerWithListingsCannotBeDeleted() {
		this.listingRepository.saveAndFlush(newListing("still-owned"));

		assertThatExceptionOfType(PersistenceException.class).isThrownBy(
				() -> nativeQuery("DELETE FROM users WHERE id = ?1").setParameter(1, this.owner.getId()).executeUpdate());
	}

	@Test
	void enumsAreStoredAsStrings() {
		Listing listing = new Listing(this.owner, "Dev tool", "dev-tool", "Pitch", "Description",
				ListingAssetType.STARTUP, ListingMarketplaceMode.COLLABORATE, ListingCategory.DEVELOPER_TOOLS,
				ListingStage.REVENUE);
		ListingTestStates.moveTo(listing, ListingStatus.PUBLISHED);
		Long id = this.listingRepository.saveAndFlush(listing).getId();
		this.entityManager.clear();

		Object[] row = (Object[]) nativeQuery(
				"SELECT asset_type, marketplace_mode, category, stage, status FROM listings WHERE id = ?1")
			.setParameter(1, id)
			.getSingleResult();
		assertThat(row).containsExactly("STARTUP", "COLLABORATE", "DEVELOPER_TOOLS", "REVENUE", "PUBLISHED");

		Listing found = this.listingRepository.findById(id).orElseThrow();
		assertThat(found.getAssetType()).isEqualTo(ListingAssetType.STARTUP);
		assertThat(found.getMarketplaceMode()).isEqualTo(ListingMarketplaceMode.COLLABORATE);
		assertThat(found.getCategory()).isEqualTo(ListingCategory.DEVELOPER_TOOLS);
		assertThat(found.getStage()).isEqualTo(ListingStage.REVENUE);
		assertThat(found.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
	}

	@Test
	void everyAssetTypeAndModeCombinationAndEveryEnumValueCanBeStored() {
		int expected = 0;
		for (ListingAssetType assetType : ListingAssetType.values()) {
			for (ListingMarketplaceMode mode : ListingMarketplaceMode.values()) {
				String slug = (assetType + "-" + mode).toLowerCase(Locale.ROOT);
				this.listingRepository.save(new Listing(this.owner, "T", slug, "P", "D", assetType, mode,
						ListingCategory.OTHER, ListingStage.CONCEPT));
				expected++;
			}
		}
		for (ListingCategory category : ListingCategory.values()) {
			this.listingRepository.save(new Listing(this.owner, "T", "category-" + category.ordinal(), "P", "D",
					ListingAssetType.IDEA, ListingMarketplaceMode.ACQUIRE, category, ListingStage.CONCEPT));
			expected++;
		}
		for (ListingStage stage : ListingStage.values()) {
			this.listingRepository.save(new Listing(this.owner, "T", "stage-" + stage.ordinal(), "P", "D",
					ListingAssetType.IDEA, ListingMarketplaceMode.ACQUIRE, ListingCategory.OTHER, stage));
			expected++;
		}
		for (ListingStatus status : ListingStatus.values()) {
			Listing listing = newListing("status-" + status.ordinal());
			ListingTestStates.moveTo(listing, status);
			this.listingRepository.save(listing);
			expected++;
		}
		this.listingRepository.flush();
		this.entityManager.clear();

		assertThat(this.listingRepository.count()).isEqualTo(expected);
		assertThat(this.listingRepository.findBySlug("startup-collaborate")).map(Listing::getAssetType)
			.contains(ListingAssetType.STARTUP);
	}

	@Test
	void slugIsUnique() {
		this.listingRepository.saveAndFlush(newListing("same-slug"));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.listingRepository.saveAndFlush(newListing("same-slug")));
	}

	@Test
	void askingPriceMayBeNull() {
		Long id = this.listingRepository.saveAndFlush(newListing("no-price")).getId();
		this.entityManager.clear();

		Listing found = this.listingRepository.findById(id).orElseThrow();
		assertThat(found.getAskingPrice()).isNull();
		assertThat(found.getCurrency()).isNull();
		assertThat(found.isPriceNegotiable()).isFalse();
	}

	@Test
	void nonNegativeAskingPriceAndCurrencyArePersisted() {
		Listing priced = newListing("priced");
		priced.setAskingPrice(new BigDecimal("1500.5"), "INR");
		Listing free = newListing("free");
		free.setAskingPrice(BigDecimal.ZERO, "EUR");
		Listing max = newListing("max-price");
		max.setAskingPrice(new BigDecimal("9999999999999.99"), "USD");
		this.listingRepository.saveAllAndFlush(List.of(priced, free, max));
		this.entityManager.clear();

		Listing foundPriced = this.listingRepository.findBySlug("priced").orElseThrow();
		assertThat(foundPriced.getAskingPrice()).isEqualByComparingTo("1500.50");
		assertThat(foundPriced.getAskingPrice().scale()).isEqualTo(2);
		assertThat(foundPriced.getCurrency()).isEqualTo("INR");
		assertThat(this.listingRepository.findBySlug("free").orElseThrow().getAskingPrice()).isEqualByComparingTo("0");
		assertThat(this.listingRepository.findBySlug("max-price").orElseThrow().getAskingPrice())
			.isEqualByComparingTo("9999999999999.99");
	}

	@Test
	void clearingThePriceRemovesPriceAndCurrencyTogether() {
		Listing listing = newListing("cleared");
		listing.setAskingPrice(new BigDecimal("100"), "USD");
		Long id = this.listingRepository.saveAndFlush(listing).getId();

		listing.clearAskingPrice();
		this.listingRepository.saveAndFlush(listing);
		this.entityManager.clear();

		Object[] row = (Object[]) nativeQuery("SELECT asking_price, currency FROM listings WHERE id = ?1")
			.setParameter(1, id)
			.getSingleResult();
		assertThat(row).containsExactly(null, null);
	}

	@Test
	void newListingIsPersistedAsDraftWithoutPublishedAt() {
		Long id = this.listingRepository.saveAndFlush(newListing("fresh")).getId();
		this.entityManager.clear();

		Listing found = this.listingRepository.findById(id).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(ListingStatus.DRAFT);
		assertThat(found.getPublishedAt()).isNull();
	}

	@Test
	void timestampsAreSetOnCreateAndUpdatedOnChange() throws InterruptedException {
		Listing listing = this.listingRepository.saveAndFlush(newListing("timestamps"));
		assertThat(listing.getCreatedAt()).isNotNull();
		assertThat(listing.getUpdatedAt()).isEqualTo(listing.getCreatedAt());
		Long id = listing.getId();
		this.entityManager.clear();

		Listing stored = this.listingRepository.findById(id).orElseThrow();
		assertThat(stored.getCreatedAt()).isEqualTo(listing.getCreatedAt());
		assertThat(stored.getUpdatedAt()).isEqualTo(listing.getUpdatedAt());

		Thread.sleep(5);
		stored.setTitle("Updated title");
		this.listingRepository.saveAndFlush(stored);
		this.entityManager.clear();

		Listing updated = this.listingRepository.findById(id).orElseThrow();
		assertThat(updated.getTitle()).isEqualTo("Updated title");
		assertThat(updated.getCreatedAt()).isEqualTo(listing.getCreatedAt());
		assertThat(updated.getUpdatedAt()).isAfter(listing.getCreatedAt());
	}

	@Test
	void existingUserIsUnaffectedByListings() {
		this.listingRepository.saveAndFlush(newListing("user-check"));
		this.entityManager.clear();

		User reloaded = this.userRepository.findById(this.owner.getId()).orElseThrow();
		assertThat(reloaded.getEmail()).isEqualTo("owner@example.com");
		assertThat(reloaded.getUsername()).isEqualTo("owner");
		assertThat(reloaded.getDisplayName()).isEqualTo("Owner");
		assertThat(reloaded.getPasswordHash()).isEqualTo("hash");
		assertThat(reloaded.getRole()).isEqualTo(UserRole.USER);
		assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(reloaded.getCreatedAt()).isEqualTo(this.owner.getCreatedAt());
		assertThat(this.userRepository.findByEmail("owner@example.com")).isPresent();
		assertThat(this.userRepository.existsByUsername("owner")).isTrue();
	}

	@Test
	void findsAndChecksBySlug() {
		this.listingRepository.saveAndFlush(newListing("findable"));
		this.entityManager.clear();

		assertThat(this.listingRepository.findBySlug("findable")).map(Listing::getTitle).contains("AI Resume Builder");
		assertThat(this.listingRepository.findBySlug("missing")).isEmpty();
		assertThat(this.listingRepository.existsBySlug("findable")).isTrue();
		assertThat(this.listingRepository.existsBySlug("missing")).isFalse();
	}

	@Test
	void findsByStatusWithPaging() {
		for (int i = 0; i < 3; i++) {
			Listing published = newListing("published-" + i);
			ListingTestStates.moveTo(published, ListingStatus.PUBLISHED);
			this.listingRepository.save(published);
		}
		this.listingRepository.save(newListing("draft"));
		this.listingRepository.flush();

		Page<Listing> firstPage = this.listingRepository.findByStatus(ListingStatus.PUBLISHED, PageRequest.of(0, 2));
		Page<Listing> secondPage = this.listingRepository.findByStatus(ListingStatus.PUBLISHED, PageRequest.of(1, 2));

		assertThat(firstPage.getTotalElements()).isEqualTo(3);
		assertThat(firstPage.getTotalPages()).isEqualTo(2);
		assertThat(firstPage.getContent()).hasSize(2);
		assertThat(secondPage.getContent()).hasSize(1);
		assertThat(firstPage.getContent()).extracting(Listing::getStatus).containsOnly(ListingStatus.PUBLISHED);
		assertThat(this.listingRepository.findByStatus(ListingStatus.ARCHIVED, PageRequest.of(0, 2))).isEmpty();
	}

	@Test
	void publicQueriesFetchTheOwnerInTheSameQuery() {
		Listing listing = newListing("fetched-owner");
		ListingTestStates.moveTo(listing, ListingStatus.PUBLISHED);
		this.listingRepository.saveAndFlush(listing);
		this.entityManager.clear();

		Listing fromPage = this.listingRepository.findByStatus(ListingStatus.PUBLISHED, PageRequest.of(0, 10))
			.getContent()
			.get(0);
		assertThat(Hibernate.isInitialized(fromPage.getOwner())).isTrue();
		this.entityManager.clear();

		Listing bySlug = this.listingRepository.findBySlugAndStatus("fetched-owner", ListingStatus.PUBLISHED)
			.orElseThrow();
		assertThat(Hibernate.isInitialized(bySlug.getOwner())).isTrue();
		assertThat(bySlug.getOwner().getUsername()).isEqualTo("owner");
		assertThat(this.listingRepository.findBySlugAndStatus("fetched-owner", ListingStatus.DRAFT)).isEmpty();
	}

	@Test
	void discoveryQueryReturnsOnlyPublishedListingsWithTheirOwnerFetched() {
		Listing published = ListingTestStates.moveTo(newListing("discover-published"), ListingStatus.PUBLISHED);
		this.listingRepository.save(published);
		for (ListingStatus hidden : new ListingStatus[] { ListingStatus.DRAFT, ListingStatus.ARCHIVED,
				ListingStatus.SUSPENDED }) {
			this.listingRepository.save(ListingTestStates.moveTo(newListing("discover-" + hidden.ordinal()), hidden));
		}
		this.listingRepository.flush();
		this.entityManager.clear();

		List<Listing> found = this.listingRepository
			.findPublished("%resume%", ListingAssetType.MVP, ListingMarketplaceMode.ACQUIRE, ListingCategory.AI,
					ListingStage.MVP, PageRequest.of(0, 10, ListingSort.NEWEST.toSort()))
			.getContent();

		assertThat(found).extracting(Listing::getSlug).containsExactly("discover-published");
		assertThat(Hibernate.isInitialized(found.get(0).getOwner())).isTrue();
		assertThat(this.listingRepository.findPublished(null, null, null, null, null, PageRequest.of(0, 10))
			.getContent()).extracting(Listing::getStatus).containsOnly(ListingStatus.PUBLISHED);
	}

	@Test
	void ownerScopedQueriesNeverReturnAnotherUsersListing() {
		User other = this.userRepository.saveAndFlush(new User("other@example.com", "hash", "other", "Other"));
		Long mine = this.listingRepository.saveAndFlush(newListing("mine")).getId();
		Listing theirs = new Listing(other, "Theirs", "theirs", "P", "D", ListingAssetType.IDEA,
				ListingMarketplaceMode.ACQUIRE, ListingCategory.AI, ListingStage.CONCEPT);
		Long theirsId = this.listingRepository.saveAndFlush(theirs).getId();
		this.entityManager.clear();

		Listing found = this.listingRepository.findByIdAndOwnerId(mine, this.owner.getId()).orElseThrow();
		assertThat(Hibernate.isInitialized(found.getOwner())).isTrue();
		assertThat(this.listingRepository.findByIdAndOwnerId(theirsId, this.owner.getId())).isEmpty();
		assertThat(this.listingRepository.findByOwnerId(this.owner.getId(), PageRequest.of(0, 10)).getContent())
			.extracting(Listing::getId)
			.containsExactly(mine);
	}

	private Listing newListing(String slug) {
		return new Listing(this.owner, "AI Resume Builder", slug, "Tailored resumes in one click",
				"A full description.\n\nWith paragraphs.", ListingAssetType.MVP, ListingMarketplaceMode.ACQUIRE,
				ListingCategory.AI, ListingStage.MVP);
	}

	private int insertListingWithNull(String nullColumn) {
		return insertListing(nullColumn != null ? Map.of(nullColumn, "NULL") : Map.of());
	}

	private int insertListing(Map<String, String> overrides) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("owner_id", this.owner.getId().toString());
		values.put("title", "'Raw title'");
		values.put("slug", "'raw-insert'");
		values.put("short_pitch", "'Raw pitch'");
		values.put("description", "'Raw description'");
		values.put("asset_type", "'IDEA'");
		values.put("marketplace_mode", "'ACQUIRE'");
		values.put("category", "'AI'");
		values.put("stage", "'CONCEPT'");
		values.put("status", "'DRAFT'");
		values.put("price_negotiable", "FALSE");
		values.put("created_at", "CURRENT_TIMESTAMP");
		values.put("updated_at", "CURRENT_TIMESTAMP");
		values.putAll(overrides);
		String sql = "INSERT INTO listings (" + String.join(", ", values.keySet()) + ") VALUES ("
				+ String.join(", ", values.values()) + ")";
		return nativeQuery(sql).executeUpdate();
	}

	private MigrationInfo migration(String version) {
		return java.util.Arrays.stream(this.flyway.info().applied())
			.filter(migration -> version.equals(migration.getVersion().getVersion()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("migration " + version + " not applied"));
	}

	private List<Object> indexes() {
		return List.copyOf(nativeQuery("SELECT index_name FROM information_schema.indexes "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'listings'")
			.getResultList());
	}

	private List<String> columns() {
		return nativeQuery("SELECT column_name FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name = 'listings' ORDER BY ordinal_position")
			.getResultList()
			.stream()
			.map(String.class::cast)
			.toList();
	}

	private Map<String, String> stringMap(String sql) {
		List<?> rows = nativeQuery(sql).getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.collect(Collectors.toMap(row -> (String) row[0], row -> ((String) row[1]).toUpperCase(Locale.ROOT)));
	}

	private jakarta.persistence.Query nativeQuery(String sql) {
		return this.entityManager.getEntityManager().createNativeQuery(sql);
	}

}
