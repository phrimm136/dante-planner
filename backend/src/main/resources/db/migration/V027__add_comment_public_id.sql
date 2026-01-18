-- V027: Add public UUIDs to planner_comments and users
-- Exposes UUID instead of internal BIGINT id to clients

-- ============================================================================
-- planner_comments
-- ============================================================================

ALTER TABLE planner_comments
    ADD COLUMN public_id BINARY(16) AFTER id;

UPDATE planner_comments
SET public_id = UNHEX(REPLACE(UUID(), '-', ''))
WHERE public_id IS NULL;

ALTER TABLE planner_comments
    MODIFY COLUMN public_id BINARY(16) NOT NULL,
    ADD UNIQUE INDEX idx_comment_public_id (public_id);

-- ============================================================================
-- users
-- ============================================================================

ALTER TABLE users
    ADD COLUMN public_id BINARY(16) AFTER id;

UPDATE users
SET public_id = UNHEX(REPLACE(UUID(), '-', ''))
WHERE public_id IS NULL;

ALTER TABLE users
    MODIFY COLUMN public_id BINARY(16) NOT NULL,
    ADD UNIQUE INDEX idx_user_public_id (public_id);
