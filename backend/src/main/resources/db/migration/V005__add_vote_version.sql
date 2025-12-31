-- Add version column for optimistic locking
-- Prevents race conditions with concurrent vote updates on same user-planner pair
ALTER TABLE planner_votes
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
