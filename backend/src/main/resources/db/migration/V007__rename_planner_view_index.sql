-- Rename index from V006 to use versioned naming convention
-- Rollback: DROP INDEX idx_v006_published_views ON planners; CREATE INDEX idx_published_views ON planners(published, view_count DESC);

DROP INDEX idx_published_views ON planners;
CREATE INDEX idx_v006_published_views ON planners(published, view_count DESC);
