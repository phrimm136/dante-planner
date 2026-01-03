-- Rename version column to schema_version for clarity
-- schema_version tracks data format changes for migration support
ALTER TABLE planners CHANGE COLUMN version schema_version INT NOT NULL DEFAULT 1;

-- Add content_version column for game content version (e.g., 6 for MD6, 5 for RR5)
-- No default - application sets this from config when creating planners
-- For existing data migration, set to 6 (they are all MD6 planners)
ALTER TABLE planners ADD COLUMN content_version INT NOT NULL AFTER schema_version;
UPDATE planners SET content_version = 6 WHERE content_version IS NULL OR content_version = 0;

-- Add planner_type column to distinguish between different game content types
-- No default - application sets this when creating planners
-- For existing data migration, set to MIRROR_DUNGEON (all existing are MD planners)
ALTER TABLE planners ADD COLUMN planner_type ENUM('MIRROR_DUNGEON', 'REFRACTED_RAILWAY') NOT NULL AFTER content_version;
UPDATE planners SET planner_type = 'MIRROR_DUNGEON';

-- Add index for filtering by planner type
CREATE INDEX idx_planner_type ON planners (planner_type);
