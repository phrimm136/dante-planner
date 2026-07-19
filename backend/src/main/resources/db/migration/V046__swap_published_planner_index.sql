-- Rollback: DROP INDEX idx_v046_published_recent ON planners;
--   CREATE INDEX idx_v006_published_views ON planners(published, view_count DESC);
--   ALTER TABLE planners ADD INDEX idx_published (published);
CREATE INDEX idx_v046_published_recent ON planners (published, deleted_at, taken_down_at, created_at, category);
DROP INDEX idx_v006_published_views ON planners;
DROP INDEX idx_published ON planners;
