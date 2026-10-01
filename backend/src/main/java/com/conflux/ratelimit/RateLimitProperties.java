package com.conflux.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conflux.rate-limit")
public record RateLimitProperties(int maxTrackedKeys, Limit login, Limit register, Limit listingCreate,
		Limit listingPublish, Limit listingSave, Limit listingInterest, Limit messageSend, Limit reportCreate,
		Limit listingUpdate, Limit listingArchive, Limit listingUnsave, Limit connectionDecision,
		Limit connectionWithdraw, Limit profileUpdate, Limit adminModeration) {

	public RateLimitProperties {
		if (maxTrackedKeys < 1) {
			throw new IllegalArgumentException("conflux.rate-limit.max-tracked-keys must be positive");
		}
		requireSet("login", login);
		requireSet("register", register);
		requireSet("listing-create", listingCreate);
		requireSet("listing-publish", listingPublish);
		requireSet("listing-save", listingSave);
		requireSet("listing-interest", listingInterest);
		requireSet("message-send", messageSend);
		requireSet("report-create", reportCreate);
		requireSet("listing-update", listingUpdate);
		requireSet("listing-archive", listingArchive);
		requireSet("listing-unsave", listingUnsave);
		requireSet("connection-decision", connectionDecision);
		requireSet("connection-withdraw", connectionWithdraw);
		requireSet("profile-update", profileUpdate);
		requireSet("admin-moderation", adminModeration);
	}

	Limit limitFor(RateLimitOperation operation) {
		return switch (operation) {
			case LOGIN -> this.login;
			case REGISTER -> this.register;
			case LISTING_CREATE -> this.listingCreate;
			case LISTING_PUBLISH -> this.listingPublish;
			case LISTING_SAVE -> this.listingSave;
			case LISTING_INTEREST -> this.listingInterest;
			case MESSAGE_SEND -> this.messageSend;
			case REPORT_CREATE -> this.reportCreate;
			case LISTING_UPDATE -> this.listingUpdate;
			case LISTING_ARCHIVE -> this.listingArchive;
			case LISTING_UNSAVE -> this.listingUnsave;
			case CONNECTION_DECISION -> this.connectionDecision;
			case CONNECTION_WITHDRAW -> this.connectionWithdraw;
			case PROFILE_UPDATE -> this.profileUpdate;
			case ADMIN_MODERATION -> this.adminModeration;
		};
	}

	private static void requireSet(String name, Limit limit) {
		if (limit == null) {
			throw new IllegalArgumentException("conflux.rate-limit." + name + " must be set");
		}
	}

	public record Limit(int maxRequests, Duration window) {

		public Limit {
			if (maxRequests < 1) {
				throw new IllegalArgumentException("Rate limit max-requests must be positive");
			}
			if (window == null || window.isNegative() || window.isZero()) {
				throw new IllegalArgumentException("Rate limit window must be positive");
			}
		}

	}

}
