package com.conflux.listing;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

	private static final Pattern URL_SAFE_SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

	private final SlugGenerator generator = new SlugGenerator();

	@Test
	void readablePartIsDerivedFromTheTitle() {
		assertThat(SlugGenerator.slugBase("AI Invoice Reconciliation")).isEqualTo("ai-invoice-reconciliation");
		assertThat(SlugGenerator.slugBase("  --Hello,   World!!  ")).isEqualTo("hello-world");
		assertThat(SlugGenerator.slugBase("Résumé Builder für Café")).isEqualTo("resume-builder-fur-cafe");
		assertThat(SlugGenerator.slugBase("Web3 / DeFi_Tools & more")).isEqualTo("web3-defi-tools-more");
		assertThat(SlugGenerator.slugBase("🚀🚀 Rocket")).isEqualTo("rocket");
	}

	@Test
	void titleWithoutUsableCharactersFallsBackToListing() {
		assertThat(SlugGenerator.slugBase("智能发票")).isEqualTo("listing");
		assertThat(SlugGenerator.slugBase("!!!")).isEqualTo("listing");
	}

	@Test
	void generatedSlugHasRandomHexSuffixAndIsUrlSafe() {
		String slug = this.generator.generate("AI Invoice Reconciliation");

		assertThat(slug).matches("ai-invoice-reconciliation-[0-9a-f]{8}");
		assertThat(URL_SAFE_SLUG.matcher(slug).matches()).isTrue();
	}

	@Test
	void longTitlesAreCutToAtMost160CharactersWithoutTrailingHyphen() {
		String slug = this.generator.generate("word ".repeat(100));

		assertThat(slug.length()).isLessThanOrEqualTo(Listing.SLUG_MAX_LENGTH);
		assertThat(URL_SAFE_SLUG.matcher(slug).matches()).isTrue();
		assertThat(SlugGenerator.slugBase("word ".repeat(100))).doesNotEndWith("-")
			.hasSizeLessThanOrEqualTo(SlugGenerator.MAX_BASE_LENGTH);
	}

	@Test
	void sameTitleGivesDifferentSlugs() {
		Set<String> slugs = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			slugs.add(this.generator.generate("Same title"));
		}
		assertThat(slugs).hasSize(1000);
	}

}
