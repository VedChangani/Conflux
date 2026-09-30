package com.conflux.listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.conflux.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Domain rules of {@link Listing}, without a database.
 */
class ListingTest {

	private final User owner = new User("owner@example.com", "hash", "owner", "Owner");

	@Test
	void newListingIsDraftWithoutPriceOrPublicationDate() {
		Listing listing = listing("ai-resume-builder");

		assertThat(listing.getStatus()).isEqualTo(ListingStatus.DRAFT);
		assertThat(listing.getPublishedAt()).isNull();
		assertThat(listing.getAskingPrice()).isNull();
		assertThat(listing.getCurrency()).isNull();
		assertThat(listing.isPriceNegotiable()).isFalse();
		assertThat(listing.getOwner()).isSameAs(this.owner);
	}

	@Test
	void requiredFieldsRejectNull() {
		assertThatNullPointerException().isThrownBy(() -> new Listing(null, "Title", "slug", "Pitch", "Description",
				ListingAssetType.IDEA, ListingMarketplaceMode.ACQUIRE, ListingCategory.AI, ListingStage.CONCEPT));
		assertThatNullPointerException().isThrownBy(() -> new Listing(this.owner, null, "slug", "Pitch",
				"Description", ListingAssetType.IDEA, ListingMarketplaceMode.ACQUIRE, ListingCategory.AI,
				ListingStage.CONCEPT));
		assertThatNullPointerException().isThrownBy(() -> new Listing(this.owner, "Title", null, "Pitch",
				"Description", ListingAssetType.IDEA, ListingMarketplaceMode.ACQUIRE, ListingCategory.AI,
				ListingStage.CONCEPT));
		assertThatNullPointerException().isThrownBy(() -> new Listing(this.owner, "Title", "slug", null,
				"Description", ListingAssetType.IDEA, ListingMarketplaceMode.ACQUIRE, ListingCategory.AI,
				ListingStage.CONCEPT));
		assertThatNullPointerException().isThrownBy(() -> new Listing(this.owner, "Title", "slug", "Pitch", null,
				ListingAssetType.IDEA, ListingMarketplaceMode.ACQUIRE, ListingCategory.AI, ListingStage.CONCEPT));
		assertThatNullPointerException().isThrownBy(() -> new Listing(this.owner, "Title", "slug", "Pitch",
				"Description", null, ListingMarketplaceMode.ACQUIRE, ListingCategory.AI, ListingStage.CONCEPT));
		assertThatNullPointerException().isThrownBy(() -> new Listing(this.owner, "Title", "slug", "Pitch",
				"Description", ListingAssetType.IDEA, null, ListingCategory.AI, ListingStage.CONCEPT));
		assertThatNullPointerException().isThrownBy(() -> new Listing(this.owner, "Title", "slug", "Pitch",
				"Description", ListingAssetType.IDEA, ListingMarketplaceMode.ACQUIRE, null, ListingStage.CONCEPT));
		assertThatNullPointerException().isThrownBy(() -> new Listing(this.owner, "Title", "slug", "Pitch",
				"Description", ListingAssetType.IDEA, ListingMarketplaceMode.ACQUIRE, ListingCategory.AI, null));

		Listing listing = listing("slug");
		assertThatNullPointerException().isThrownBy(() -> listing.setTitle(null));
		assertThatNullPointerException().isThrownBy(() -> listing.setShortPitch(null));
		assertThatNullPointerException().isThrownBy(() -> listing.setDescription(null));
		assertThatNullPointerException().isThrownBy(() -> listing.setAssetType(null));
		assertThatNullPointerException().isThrownBy(() -> listing.setMarketplaceMode(null));
		assertThatNullPointerException().isThrownBy(() -> listing.setCategory(null));
		assertThatNullPointerException().isThrownBy(() -> listing.setStage(null));
	}

	@Test
	void optionalTextFieldsMayBeNull() {
		Listing listing = listing("slug");
		listing.setProblem(null);
		listing.setSolution(null);
		listing.setCollaborationDetails(null);

		assertThat(listing.getProblem()).isNull();
		assertThat(listing.getSolution()).isNull();
		assertThat(listing.getCollaborationDetails()).isNull();
	}

	@Test
	void userContentIsStoredExactlyAsGiven() {
		Listing listing = new Listing(this.owner, "  AI  Résumé Builder ", "ai-resume-builder", " Pitch\t",
				"\nLine one\n\nLine two  ", ListingAssetType.MVP, ListingMarketplaceMode.COLLABORATE,
				ListingCategory.AI, ListingStage.PROTOTYPE);
		listing.setProblem("  problem ");
		listing.setSolution(" solution  ");
		listing.setCollaborationDetails(" Looking for a technical cofounder and growth partner. ");

		assertThat(listing.getTitle()).isEqualTo("  AI  Résumé Builder ");
		assertThat(listing.getShortPitch()).isEqualTo(" Pitch\t");
		assertThat(listing.getDescription()).isEqualTo("\nLine one\n\nLine two  ");
		assertThat(listing.getProblem()).isEqualTo("  problem ");
		assertThat(listing.getSolution()).isEqualTo(" solution  ");
		assertThat(listing.getCollaborationDetails())
			.isEqualTo(" Looking for a technical cofounder and growth partner. ");
	}

	@Test
	void titleAndShortPitchRespectMaximumLengths() {
		Listing listing = listing("slug");
		listing.setTitle("t".repeat(120));
		listing.setShortPitch("p".repeat(240));
		// Counted in characters (code points), like MySQL VARCHAR, not UTF-16 units.
		listing.setTitle("🚀".repeat(120));

		assertThatIllegalArgumentException().isThrownBy(() -> listing.setTitle("t".repeat(121)));
		assertThatIllegalArgumentException().isThrownBy(() -> listing.setShortPitch("p".repeat(241)));
	}

	@Test
	void slugMustBeUrlFriendlyAndIsNotRewritten() {
		assertThat(listing("ai-resume-builder-2").getSlug()).isEqualTo("ai-resume-builder-2");
		assertThat(listing("a".repeat(160)).getSlug()).hasSize(160);

		for (String invalid : new String[] { "", "AI-Builder", "ai builder", "ai_builder", "-ai", "ai-", "ai--builder",
				"résumé", "ai/builder", "a".repeat(161) }) {
			assertThatIllegalArgumentException().as(invalid).isThrownBy(() -> listing(invalid));
		}
	}

	@Test
	void priceWithCurrencyIsValid() {
		Listing listing = listing("slug");
		listing.setAskingPrice(new BigDecimal("25000.50"), "USD");

		assertThat(listing.getAskingPrice()).isEqualByComparingTo("25000.50");
		assertThat(listing.getCurrency()).isEqualTo("USD");
	}

	@Test
	void zeroPriceIsAllowed() {
		Listing listing = listing("slug");
		listing.setAskingPrice(BigDecimal.ZERO, "EUR");

		assertThat(listing.getAskingPrice()).isEqualByComparingTo("0");
	}

	@Test
	void negativePriceIsRejected() {
		Listing listing = listing("slug");

		assertThatIllegalArgumentException().isThrownBy(() -> listing.setAskingPrice(new BigDecimal("-0.01"), "USD"));
		assertThat(listing.getAskingPrice()).isNull();
		assertThat(listing.getCurrency()).isNull();
	}

	@Test
	void priceWithoutCurrencyIsRejected() {
		Listing listing = listing("slug");

		assertThatNullPointerException().isThrownBy(() -> listing.setAskingPrice(new BigDecimal("100"), null));
		assertThat(listing.getAskingPrice()).isNull();
	}

	@Test
	void noPriceAndNoCurrencyIsValid() {
		Listing listing = listing("slug");
		listing.setAskingPrice(new BigDecimal("100"), "USD");
		listing.clearAskingPrice();

		assertThat(listing.getAskingPrice()).isNull();
		assertThat(listing.getCurrency()).isNull();
	}

	@Test
	void currencyWithoutPriceCannotBeSet() {
		Listing listing = listing("slug");

		assertThatNullPointerException().isThrownBy(() -> listing.setAskingPrice(null, "USD"));
		assertThat(listing.getCurrency()).isNull();
	}

	@Test
	void currencyMustBeThreeUpperCaseLetters() {
		Listing listing = listing("slug");
		for (String currency : new String[] { "INR", "USD", "EUR" }) {
			listing.setAskingPrice(BigDecimal.TEN, currency);
			assertThat(listing.getCurrency()).isEqualTo(currency);
		}
		for (String invalid : new String[] { "usd", "US", "USDX", "U$D", "12A", "" }) {
			assertThatIllegalArgumentException().as(invalid)
				.isThrownBy(() -> listing.setAskingPrice(BigDecimal.TEN, invalid));
		}
	}

	@Test
	void priceMustFitDecimal15Scale2WithoutRounding() {
		Listing listing = listing("slug");
		listing.setAskingPrice(new BigDecimal("9999999999999.99"), "USD");

		assertThatIllegalArgumentException().isThrownBy(() -> listing.setAskingPrice(new BigDecimal("10.999"), "USD"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> listing.setAskingPrice(new BigDecimal("10000000000000"), "USD"));
		assertThat(listing.getAskingPrice()).isEqualByComparingTo("9999999999999.99");
	}

	// ---- Owner lifecycle ------------------------------------------------------------

	@Test
	void statusesAreExactlyTheSelfPublishingLifecycle() {
		assertThat(ListingStatus.values()).containsExactly(ListingStatus.DRAFT, ListingStatus.PUBLISHED,
				ListingStatus.ARCHIVED, ListingStatus.SUSPENDED);
	}

	@Test
	void publishMakesDraftPublishedAndSetsPublishedAtToNow() {
		Listing listing = listing("slug");
		Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

		listing.publish();

		assertThat(listing.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
		assertThat(listing.getPublishedAt()).isBetween(before, Instant.now());
	}

	@Test
	void publishIsRefusedUnlessDraftAndChangesNothing() {
		for (ListingStatus status : new ListingStatus[] { ListingStatus.PUBLISHED, ListingStatus.ARCHIVED,
				ListingStatus.SUSPENDED }) {
			Listing listing = withStatus(status);
			Instant publishedAt = listing.getPublishedAt();

			assertThatExceptionOfType(ListingStateException.class).as(status.name())
				.isThrownBy(listing::publish)
				.withMessage("A listing with status " + status + " cannot be published.");
			assertThat(listing.getStatus()).isEqualTo(status);
			assertThat(listing.getPublishedAt()).isEqualTo(publishedAt);
		}
	}

	@Test
	void draftAndPublishedAreEditableWithoutAnyStatusChange() {
		Listing draft = listing("draft");
		draft.requireEditable();
		assertThat(draft.getStatus()).isEqualTo(ListingStatus.DRAFT);
		assertThat(draft.getPublishedAt()).isNull();

		Listing published = withStatus(ListingStatus.PUBLISHED);
		Instant publishedAt = published.getPublishedAt();
		published.requireEditable();
		published.setTitle("Edited title");
		assertThat(published.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
		assertThat(published.getPublishedAt()).isNotNull().isEqualTo(publishedAt);
	}

	@Test
	void editIsRefusedWhenArchivedOrSuspended() {
		for (ListingStatus status : new ListingStatus[] { ListingStatus.ARCHIVED, ListingStatus.SUSPENDED }) {
			Listing listing = withStatus(status);
			assertThatExceptionOfType(ListingStateException.class).as(status.name())
				.isThrownBy(listing::requireEditable)
				.withMessage("A listing with status " + status + " cannot be edited.");
			assertThat(listing.getStatus()).isEqualTo(status);
		}
	}

	@Test
	void archiveIsAllowedExceptWhenSuspendedKeepsPublishedAtAndIsIdempotent() {
		for (ListingStatus status : new ListingStatus[] { ListingStatus.DRAFT, ListingStatus.PUBLISHED,
				ListingStatus.ARCHIVED }) {
			Listing listing = withStatus(status);
			Instant publishedAt = listing.getPublishedAt();
			listing.archive();
			assertThat(listing.getStatus()).as(status.name()).isEqualTo(ListingStatus.ARCHIVED);
			assertThat(listing.getPublishedAt()).isEqualTo(publishedAt);
		}
		Listing suspended = withStatus(ListingStatus.SUSPENDED);
		assertThatExceptionOfType(ListingStateException.class).isThrownBy(suspended::archive);
		assertThat(suspended.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
	}

	@Test
	void suspensionIsOnlyPossibleForPublishedListingsAndKeepsPublishedAt() {
		Listing published = withStatus(ListingStatus.PUBLISHED);
		Instant publishedAt = published.getPublishedAt();
		published.suspend();
		assertThat(published.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
		assertThat(published.getPublishedAt()).isEqualTo(publishedAt);

		for (ListingStatus status : new ListingStatus[] { ListingStatus.DRAFT, ListingStatus.ARCHIVED,
				ListingStatus.SUSPENDED }) {
			Listing listing = withStatus(status);
			assertThatExceptionOfType(ListingStateException.class).as(status.name()).isThrownBy(listing::suspend);
			assertThat(listing.getStatus()).isEqualTo(status);
		}
	}

	@Test
	void noPublicMethodCanSetAnArbitraryStatus() {
		assertThat(Listing.class.getMethods()).extracting(java.lang.reflect.Method::getName)
			.doesNotContain("setStatus", "suspend");
	}

	private Listing withStatus(ListingStatus status) {
		return ListingTestStates.moveTo(listing("slug"), status);
	}

	private Listing listing(String slug) {
		return new Listing(this.owner, "AI Resume Builder", slug, "Tailored resumes in one click",
				"A full description.", ListingAssetType.MVP, ListingMarketplaceMode.ACQUIRE, ListingCategory.AI,
				ListingStage.MVP);
	}

}
