package com.conflux.listing;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class SlugGenerator {

	static final int SUFFIX_LENGTH = 8;

	static final int MAX_BASE_LENGTH = Listing.SLUG_MAX_LENGTH - SUFFIX_LENGTH - 1;

	private static final String FALLBACK_BASE = "listing";

	private final SecureRandom random = new SecureRandom();

	public String generate(String title) {
		return slugBase(title) + "-" + randomSuffix();
	}

	static String slugBase(String title) {
		String withoutAccents = Normalizer.normalize(title, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
		String base = withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (base.length() > MAX_BASE_LENGTH) {
			base = base.substring(0, MAX_BASE_LENGTH).replaceAll("-+$", "");
		}
		return base.isEmpty() ? FALLBACK_BASE : base;
	}

	private String randomSuffix() {
		byte[] bytes = new byte[SUFFIX_LENGTH / 2];
		this.random.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

}
