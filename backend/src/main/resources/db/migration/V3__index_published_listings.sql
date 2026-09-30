-- Public browsing: WHERE status = 'PUBLISHED' ORDER BY published_at DESC, id DESC.
-- The composite index serves both the filter and the order (InnoDB secondary indexes
-- implicitly end with the primary key, which covers the id tie-breaker).
CREATE INDEX idx_listings_status_published_at ON listings (status, published_at);

-- Fully covered by the composite index above (same leading column).
DROP INDEX idx_listings_status ON listings;
