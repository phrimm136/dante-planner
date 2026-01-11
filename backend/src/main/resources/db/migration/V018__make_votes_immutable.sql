-- V018: Make votes immutable by removing soft-delete infrastructure
-- Votes are now permanent - no toggle, no updates, no soft-delete

-- Remove soft-deleted votes (acceptable data loss per old toggle system)
DELETE FROM planner_votes WHERE deleted_at IS NOT NULL;
DELETE FROM planner_comment_votes WHERE deleted_at IS NOT NULL;

-- Drop soft-delete and update tracking columns from planner_votes
ALTER TABLE planner_votes
    DROP COLUMN deleted_at,
    DROP COLUMN updated_at;

-- Drop soft-delete and update tracking columns from planner_comment_votes
ALTER TABLE planner_comment_votes
    DROP COLUMN deleted_at,
    DROP COLUMN updated_at;

-- Drop index that was used for soft-delete queries
DROP INDEX idx_planner_active_votes ON planner_votes;
