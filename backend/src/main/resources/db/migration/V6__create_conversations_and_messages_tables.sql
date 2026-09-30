-- One conversation per accepted connection. Participants and listing are not stored: they
-- are the connection's requester and its listing's owner. Timestamps are stored in UTC.
CREATE TABLE conversations (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    connection_id BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (id),
    -- At most one conversation per connection; also backs the connection foreign key.
    CONSTRAINT uk_conversations_connection UNIQUE (connection_id)
);

-- Plain-text messages. The receiver is not stored: it is the other participant.
CREATE TABLE messages (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    sender_id       BIGINT      NOT NULL,
    content         TEXT        NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id)
);

-- A conversation's messages newest first (paging and the latest-message lookup); also backs
-- the conversation foreign key.
CREATE INDEX idx_messages_conversation_created_id ON messages (conversation_id, created_at, id);

-- Nothing is physically deleted by the application; such deletes are refused, never cascaded.
ALTER TABLE conversations
    ADD CONSTRAINT fk_conversations_connection FOREIGN KEY (connection_id) REFERENCES connections (id) ON DELETE RESTRICT;

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE RESTRICT;

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE RESTRICT;
