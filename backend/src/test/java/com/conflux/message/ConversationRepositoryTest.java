package com.conflux.message;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.conflux.connection.Connection;
import com.conflux.connection.ConnectionRepository;
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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ConversationRepositoryTest {

	@Autowired
	private ConversationRepository conversationRepository;

	@Autowired
	private MessageRepository messageRepository;

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

	@BeforeEach
	void setUp() {
		this.alice = this.userRepository.save(new User("alice@example.com", "hash", "alice", "Alice"));
		this.bob = this.userRepository.save(new User("bob@example.com", "hash", "bob", "Bob"));
		this.charlie = this.userRepository.save(new User("charlie@example.com", "hash", "charlie", "Charlie"));
		this.aliceListing = published(this.alice, "alice-listing");
	}

	@Test
	void v6MigrationCreatesConversationsAndMessagesWithConstraints() {
		assertThat(this.flyway.info().applied()).filteredOn(m -> "6".equals(m.getVersion().getVersion()))
			.singleElement()
			.satisfies(m -> {
				assertThat(m.getScript()).isEqualTo("V6__create_conversations_and_messages_tables.sql");
				assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS);
			});
		assertThat(columns("conversations")).containsExactly("id", "connection_id", "created_at", "updated_at");
		assertThat(columns("messages")).containsExactly("id", "conversation_id", "sender_id", "content", "created_at");
		assertThat(nativeMap("SELECT column_name, is_nullable FROM information_schema.columns "
				+ "WHERE table_schema = SCHEMA() AND table_name IN ('conversations', 'messages') AND column_name <> 'id'"))
			.allSatisfy((column, nullable) -> assertThat(nullable).as(column).isEqualTo("NO"));
		assertThat(nativeMap("SELECT constraint_name, constraint_type FROM information_schema.table_constraints "
				+ "WHERE table_schema = SCHEMA() AND table_name IN ('conversations', 'messages')"))
			.containsEntry("pk_conversations", "PRIMARY KEY")
			.containsEntry("uk_conversations_connection", "UNIQUE")
			.containsEntry("fk_conversations_connection", "FOREIGN KEY")
			.containsEntry("pk_messages", "PRIMARY KEY")
			.containsEntry("fk_messages_conversation", "FOREIGN KEY")
			.containsEntry("fk_messages_sender", "FOREIGN KEY");
		assertThat(nativeMap("SELECT constraint_name, delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = SCHEMA() AND constraint_name IN ('fk_conversations_connection', "
				+ "'fk_messages_conversation', 'fk_messages_sender')"))
			.hasSize(3)
			.allSatisfy((name, rule) -> assertThat(rule).as(name).isEqualTo("RESTRICT"));
		assertThat(nativeList("SELECT column_name FROM information_schema.index_columns WHERE table_schema = SCHEMA() "
				+ "AND index_name = 'idx_messages_conversation_created_id' ORDER BY ordinal_position"))
			.containsExactly("conversation_id", "created_at", "id");
		assertThat(columns("conversations")).doesNotContain("requester_id", "owner_id", "listing_id");
		assertThat(columns("messages")).doesNotContain("receiver_id", "listing_id");
	}

	@Test
	void aConnectionHasAtMostOneConversation() {
		Connection connection = acceptedConnection(this.bob);
		this.conversationRepository.saveAndFlush(new Conversation(connection));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.conversationRepository.saveAndFlush(new Conversation(connection)));
	}

	@Test
	void historyBlocksPhysicalDeletionInsteadOfCascading() {
		Connection connection = acceptedConnection(this.bob);
		Conversation conversation = this.conversationRepository.saveAndFlush(new Conversation(connection));
		this.messageRepository.saveAndFlush(new Message(conversation, this.bob, "Hello"));

		assertThatExceptionOfType(PersistenceException.class)
			.isThrownBy(() -> execute("DELETE FROM conversations WHERE id = " + conversation.getId()));
		assertThatExceptionOfType(PersistenceException.class)
			.isThrownBy(() -> execute("DELETE FROM connections WHERE id = " + connection.getId()));
	}

	@Test
	void participantsAreTheRequesterAndTheListingOwnerOnly() {
		Conversation conversation = this.conversationRepository.saveAndFlush(new Conversation(acceptedConnection(this.bob)));
		this.entityManager.clear();

		assertThat(this.conversationRepository.findForParticipant(conversation.getId(), this.bob.getId())).isPresent();
		assertThat(this.conversationRepository.findForParticipant(conversation.getId(), this.alice.getId())).isPresent();
		assertThat(this.conversationRepository.findForParticipant(conversation.getId(), this.charlie.getId())).isEmpty();

		Conversation found = this.conversationRepository.findForParticipant(conversation.getId(), this.bob.getId())
			.orElseThrow();
		assertThat(Hibernate.isInitialized(found.getConnection())).isTrue();
		assertThat(Hibernate.isInitialized(found.getConnection().getListing().getOwner())).isTrue();
		assertThat(Hibernate.isInitialized(found.getConnection().getRequester())).isTrue();
		assertThat(found.isParticipant(this.bob.getId())).isTrue();
		assertThat(found.isParticipant(this.alice.getId())).isTrue();
		assertThat(found.isParticipant(this.charlie.getId())).isFalse();
		assertThat(found.otherParticipant(this.bob.getId()).getId()).isEqualTo(this.alice.getId());
		assertThat(found.otherParticipant(this.alice.getId()).getId()).isEqualTo(this.bob.getId());
		assertThatIllegalArgumentException().isThrownBy(() -> found.otherParticipant(this.charlie.getId()));
	}

	@Test
	void activityQueryOrdersByLatestMessageOrCreationAndFetchesEverythingInOneQuery() {
		Conversation withOldMessage = this.conversationRepository.save(new Conversation(acceptedConnection(this.bob)));
		Conversation withoutMessages = this.conversationRepository.save(new Conversation(acceptedConnection(this.charlie)));
		Message old = this.messageRepository.save(new Message(withOldMessage, this.bob, "Old message"));
		this.entityManager.flush();
		setTime("conversations", withOldMessage.getId(), "2026-01-01T00:00:00Z");
		setTime("messages", old.getId(), "2026-01-02T00:00:00Z");
		setTime("conversations", withoutMessages.getId(), "2026-01-03T00:00:00Z");
		this.entityManager.clear();

		List<Object[]> rows = this.conversationRepository.findForParticipantByActivity(this.alice.getId(),
				PageRequest.of(0, 10))
			.getContent();

		assertThat(rows).extracting(row -> ((Conversation) row[0]).getId())
			.containsExactly(withoutMessages.getId(), withOldMessage.getId());
		assertThat(rows).extracting(row -> row[1])
			.containsExactly(Instant.parse("2026-01-03T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
		Conversation first = (Conversation) rows.get(0)[0];
		assertThat(Hibernate.isInitialized(first.getConnection())).isTrue();
		assertThat(Hibernate.isInitialized(first.getConnection().getListing())).isTrue();
		assertThat(Hibernate.isInitialized(first.getConnection().getListing().getOwner())).isTrue();
		assertThat(Hibernate.isInitialized(first.getConnection().getRequester())).isTrue();
		assertThat(this.conversationRepository.findForParticipantByActivity(this.bob.getId(), PageRequest.of(0, 10))
			.getTotalElements()).isEqualTo(1);
	}

	@Test
	void messagesArePagedNewestFirstAndTheLatestIsFoundInOneQuery() {
		Conversation a = this.conversationRepository.save(new Conversation(acceptedConnection(this.bob)));
		Conversation b = this.conversationRepository.save(new Conversation(acceptedConnection(this.charlie)));
		Message a1 = this.messageRepository.save(new Message(a, this.bob, "a1"));
		Message a2 = this.messageRepository.save(new Message(a, this.alice, "a2"));
		Message b1 = this.messageRepository.save(new Message(b, this.charlie, "b1"));
		this.entityManager.flush();
		setTime("messages", a1.getId(), "2026-02-01T00:00:00Z");
		setTime("messages", a2.getId(), "2026-02-02T00:00:00Z");
		setTime("messages", b1.getId(), "2026-02-01T00:00:00Z");
		this.entityManager.clear();

		assertThat(this.messageRepository
			.findByConversationId(a.getId(),
					PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
			.getContent()).extracting(Message::getContent).containsExactly("a2", "a1");
		assertThat(this.messageRepository.findLatestInConversations(List.of(a.getId(), b.getId())))
			.extracting(Message::getContent)
			.containsExactlyInAnyOrder("a2", "b1");
		assertThat(this.messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(a.getId()))
			.map(Message::getContent)
			.contains("a2");
	}

	private Connection acceptedConnection(User requester) {
		Connection connection = new Connection(this.aliceListing, requester);
		connection.accept();
		return this.connectionRepository.saveAndFlush(connection);
	}

	private Listing published(User owner, String slug) {
		Listing listing = new Listing(owner, "Title " + slug, slug, "Pitch", "Description", ListingAssetType.IDEA,
				ListingMarketplaceMode.COLLABORATE, ListingCategory.AI, ListingStage.CONCEPT);
		listing.publish();
		return this.listingRepository.save(listing);
	}

	private void setTime(String table, Long id, String instant) {
		this.entityManager.getEntityManager()
			.createNativeQuery("UPDATE " + table + " SET created_at = ?1 WHERE id = ?2")
			.setParameter(1, Instant.parse(instant))
			.setParameter(2, id)
			.executeUpdate();
	}

	private List<Object> columns(String table) {
		return nativeList("SELECT column_name FROM information_schema.columns WHERE table_schema = SCHEMA() "
				+ "AND table_name = '" + table + "' ORDER BY ordinal_position");
	}

	private List<Object> nativeList(String sql) {
		return List.copyOf(this.entityManager.getEntityManager().createNativeQuery(sql).getResultList());
	}

	private Map<String, String> nativeMap(String sql) {
		List<?> rows = this.entityManager.getEntityManager().createNativeQuery(sql).getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.collect(Collectors.toMap(row -> (String) row[0], row -> ((String) row[1]).toUpperCase(), (x, y) -> x));
	}

	private void execute(String sql) {
		this.entityManager.getEntityManager().createNativeQuery(sql).executeUpdate();
	}

}
