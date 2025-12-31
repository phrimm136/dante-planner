-- Add soft delete columns to planner_votes table
ALTER TABLE planner_votes
    ADD COLUMN updated_at TIMESTAMP NULL,
    ADD COLUMN deleted_at TIMESTAMP NULL;

-- Add index for querying active votes by planner
CREATE INDEX idx_planner_active_votes ON planner_votes (planner_id, deleted_at);
