-- Add first_published_at column to track when planner was first published
-- Used for one-time publish notification (prevents spam on republish)

ALTER TABLE planners ADD COLUMN first_published_at TIMESTAMP(6) NULL;

-- Backfill: set first_published_at for already-published planners to their created_at
UPDATE planners SET first_published_at = created_at WHERE published = TRUE AND first_published_at IS NULL;
