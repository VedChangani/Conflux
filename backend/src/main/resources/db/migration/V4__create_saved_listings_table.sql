-- Listings a user has saved (bookmarked). One row per (user, listing) pair.
-- created_at is stored in UTC.
CREATE TABLE saved_listings (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    listing_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_saved_listings PRIMARY KEY (id),
    -- Also the index for "this user's saves" and (user, listing) lookups, and it backs the user foreign key.
    CONSTRAINT uk_saved_listings_user_listing UNIQUE (user_id, listing_id)
);

-- Backs the listing foreign key (the unique index above starts with user_id, so it cannot).
CREATE INDEX idx_saved_listings_listing_id ON saved_listings (listing_id);

-- Users and listings are never physically deleted by the application (listings are archived);
-- a delete of either that still has saves is refused rather than silently removing them.
ALTER TABLE saved_listings
    ADD CONSTRAINT fk_saved_listings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE saved_listings
    ADD CONSTRAINT fk_saved_listings_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT;
