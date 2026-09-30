-- Marketplace listings. Each listing belongs to exactly one user (owner_id).
-- Enum-backed columns hold enum names as strings (no native ENUM columns).
-- Timestamps are stored in UTC. published_at stays NULL until a listing is published.
-- The price/currency relationship is enforced by the application (Listing entity).
CREATE TABLE listings (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    owner_id              BIGINT         NOT NULL,
    title                 VARCHAR(120)   NOT NULL,
    slug                  VARCHAR(160)   NOT NULL,
    short_pitch           VARCHAR(240)   NOT NULL,
    description           TEXT           NOT NULL,
    problem               TEXT,
    solution              TEXT,
    asset_type            VARCHAR(20)    NOT NULL,
    marketplace_mode      VARCHAR(20)    NOT NULL,
    category              VARCHAR(30)    NOT NULL,
    stage                 VARCHAR(20)    NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    asking_price          DECIMAL(15, 2),
    currency              VARCHAR(3),
    price_negotiable      BOOLEAN        NOT NULL DEFAULT FALSE,
    collaboration_details TEXT,
    published_at          DATETIME(6),
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    CONSTRAINT pk_listings PRIMARY KEY (id),
    CONSTRAINT uk_listings_slug UNIQUE (slug)
);

-- Listings by owner ("my listings"); also backs the foreign key below.
CREATE INDEX idx_listings_owner_id ON listings (owner_id);

-- Listings by lifecycle status (e.g. browsing PUBLISHED listings).
CREATE INDEX idx_listings_status ON listings (status);

-- Deleting a user that still owns listings is refused rather than cascaded.
ALTER TABLE listings
    ADD CONSTRAINT fk_listings_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT;
