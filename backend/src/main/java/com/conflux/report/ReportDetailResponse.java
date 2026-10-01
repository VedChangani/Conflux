package com.conflux.report;

import java.time.Instant;

import com.conflux.listing.Listing;
import com.conflux.listing.ListingStatus;
import com.conflux.message.Message;
import com.conflux.user.User;
import com.conflux.user.UserStatus;

public record ReportDetailResponse(Long id, ReportTargetType targetType, Long targetId, ReportReason reason,
		String details, ReportStatus status, Instant createdAt, Instant reviewedAt, String resolutionNote,
		Person reporter, Person reviewer, Target target) {

	public record Person(Long id, String username, String displayName) {

		static Person from(User user) {
			return (user != null) ? new Person(user.getId(), user.getUsername(), user.getDisplayName()) : null;
		}

	}

	public sealed interface Target permits UserTarget, ListingTarget, MessageTarget {

	}

	public record UserTarget(Long id, String username, String displayName, String bio, String location,
			String websiteUrl, String githubUrl, String linkedinUrl, UserStatus status) implements Target {

		static UserTarget from(User user) {
			return new UserTarget(user.getId(), user.getUsername(), user.getDisplayName(), user.getBio(),
					user.getLocation(), user.getWebsiteUrl(), user.getGithubUrl(), user.getLinkedinUrl(),
					user.getStatus());
		}

	}

	public record ListingTarget(Long id, String slug, String title, String shortPitch, String description,
			ListingStatus status, Person owner) implements Target {

		static ListingTarget from(Listing listing) {
			return new ListingTarget(listing.getId(), listing.getSlug(), listing.getTitle(), listing.getShortPitch(),
					listing.getDescription(), listing.getStatus(), Person.from(listing.getOwner()));
		}

	}

	public record MessageTarget(Long id, String content, Instant createdAt, Person sender, Long conversationId,
			ListingRef listing) implements Target {

		static MessageTarget from(Message message) {
			Listing listing = message.getConversation().getConnection().getListing();
			return new MessageTarget(message.getId(), message.getContent(), message.getCreatedAt(),
					Person.from(message.getSender()), message.getConversation().getId(),
					new ListingRef(listing.getId(), listing.getSlug(), listing.getTitle()));
		}

	}

	public record ListingRef(Long id, String slug, String title) {

	}

	static ReportDetailResponse from(Report report, Target target) {
		return new ReportDetailResponse(report.getId(), report.getTargetType(), report.getTargetId(),
				report.getReason(), report.getDetails(), report.getStatus(), report.getCreatedAt(),
				report.getReviewedAt(), report.getResolutionNote(), Person.from(report.getReporter()),
				Person.from(report.getReviewedBy()), target);
	}

}
