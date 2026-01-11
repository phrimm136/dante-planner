-- V023: Remove downvote functionality from voting system
-- Irreversible migration: Deletes all DOWN votes and removes downvotes column
-- Voting system becomes upvote-only after this migration

-- Delete all downvotes from planner_votes table (irreversible)
DELETE FROM planner_votes WHERE vote_type = 'DOWN';

-- Delete all downvotes from planner_comment_votes table (irreversible)
DELETE FROM planner_comment_votes WHERE vote_type = 'DOWN';

-- Drop index that references downvotes column (if exists)
DROP INDEX IF EXISTS idx_published_votes ON planners;

-- Drop downvotes column from planners table
ALTER TABLE planners DROP COLUMN downvotes;
