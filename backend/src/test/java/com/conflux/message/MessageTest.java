package com.conflux.message;

import com.conflux.connection.Connection;
import com.conflux.listing.Listing;
import com.conflux.listing.ListingAssetType;
import com.conflux.listing.ListingCategory;
import com.conflux.listing.ListingMarketplaceMode;
import com.conflux.listing.ListingStage;
import com.conflux.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Message content rules and request normalization, without a database.
 */
class MessageTest {

	private final User owner = new User("owner@example.com", "hash", "owner", "Owner");

	private final User requester = new User("requester@example.com", "hash", "requester", "Requester");

	private final Conversation conversation = new Conversation(new Connection(new Listing(this.owner, "Title",
			"title-0000abcd", "Pitch", "Description", ListingAssetType.IDEA, ListingMarketplaceMode.COLLABORATE,
			ListingCategory.AI, ListingStage.CONCEPT), this.requester));

	@Test
	void contentIsStoredExactlyAsGiven() {
		String content = "Hello,\n\n  I am   interested.\tLet's talk.";
		Message message = new Message(this.conversation, this.requester, content);

		assertThat(message.getContent()).isEqualTo(content);
		assertThat(message.getSender()).isSameAs(this.requester);
	}

	@Test
	void contentMustBeNonBlankAndAtMost5000Characters() {
		new Message(this.conversation, this.requester, "x".repeat(5_000));

		assertThatIllegalArgumentException().isThrownBy(() -> new Message(this.conversation, this.requester, " \n "));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Message(this.conversation, this.requester, "x".repeat(5_001)));
		assertThatNullPointerException().isThrownBy(() -> new Message(this.conversation, this.requester, null));
		assertThatNullPointerException().isThrownBy(() -> new Message(null, this.requester, "hi"));
		assertThatNullPointerException().isThrownBy(() -> new Message(this.conversation, null, "hi"));
	}

	@Test
	void requestTrimsOnlyTheEndsAndNeverPrintsTheContent() {
		SendMessageRequest request = new SendMessageRequest("  \n Hello,  there\n\nfriend \t ");

		assertThat(request.content()).isEqualTo("Hello,  there\n\nfriend");
		assertThat(new SendMessageRequest("   ").content()).isEmpty();
		assertThat(new SendMessageRequest(null).content()).isNull();
		assertThat(request.toString()).doesNotContain("Hello");
	}

}
