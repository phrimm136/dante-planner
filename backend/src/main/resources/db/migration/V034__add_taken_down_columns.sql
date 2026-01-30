-- Add moderator takedown tracking for planners and expand moderation audit
-- Enables moderators to remove planners from public while allowing owner to sync private copy

-- Add takedown timestamp to planners table
-- Actor is tracked in moderation_actions table (no need for taken_down_by column)
ALTER TABLE planners ADD COLUMN taken_down_at TIMESTAMP(6) NULL;

-- Add index for public queries (filter taken_down_at IS NULL)
CREATE INDEX idx_planners_taken_down_at ON planners(taken_down_at);

-- Migrate moderation_actions from target_id (BIGINT internal ID) to target_uuid (VARCHAR public UUID)
-- This enables public-facing moderation history where users see UUIDs, not internal IDs

-- Add new target_uuid column
ALTER TABLE moderation_actions ADD COLUMN target_uuid VARCHAR(36) NULL;

-- Add target_type column to distinguish USER, PLANNER, COMMENT targets
ALTER TABLE moderation_actions ADD COLUMN target_type VARCHAR(20) NULL;

-- Backfill existing rows with 'USER' type (all V033 actions were user-targeted)
UPDATE moderation_actions SET target_type = 'USER' WHERE target_type IS NULL;

-- Now make it NOT NULL after backfill
ALTER TABLE moderation_actions MODIFY COLUMN target_type VARCHAR(20) NOT NULL;

-- Drop foreign key constraint before dropping column
ALTER TABLE moderation_actions DROP FOREIGN KEY fk_moderation_action_target;

-- Drop old composite index
DROP INDEX idx_moderation_target_created ON moderation_actions;

-- Drop old target_id column
ALTER TABLE moderation_actions DROP COLUMN target_id;

-- Create new composite index with target_uuid
CREATE INDEX idx_moderation_target_uuid_created ON moderation_actions(target_uuid, created_at DESC);

-- Add index for filtering by target type
CREATE INDEX idx_moderation_target_type ON moderation_actions(target_type);
