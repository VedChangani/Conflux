-- Expressions of interest in a listing and the owner's answer. The owner is not stored:
-- it is always the listing's (immutable) owner. status holds a ConnectionStatus name
-- (no native ENUM). Timestamps are stored in UTC.
CREATE TABLE connections (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    listing_id   BIGINT      NOT NULL,
    requester_id BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_connections PRIMARY KEY (id),
    -- One connection per requester and listing; also serves requester-scoped lookups and
    -- backs the requester foreign key.
    CONSTRAINT uk_connections_requester_listing UNIQUE (requester_id, listing_id)
);

-- Received connections are found through the owner's listings (connections -> listings -> owner);
-- also backs the listing foreign key.
CREATE INDEX idx_connections_listing_id ON connections (listing_id);

-- Listings and users are never physically deleted by the application (listings are archived);
-- deleting either while connection history references it is refused, never cascaded.
ALTER TABLE connections
    ADD CONSTRAINT fk_connections_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT;

ALTER TABLE connections
    ADD CONSTRAINT fk_connections_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE RESTRICT;
