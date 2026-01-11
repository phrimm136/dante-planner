-- V020: Add moderation fields to planners table
-- Enables admin/moderator curation of recommended list without deleting content

ALTER TABLE planners
    ADD COLUMN hidden_from_recommended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hidden_by_moderator_id BIGINT NULL,
    ADD COLUMN hidden_reason TEXT NULL,
    ADD COLUMN hidden_at TIMESTAMP NULL;

-- Foreign key to moderator/admin who hid the planner
ALTER TABLE planners
    ADD CONSTRAINT fk_planner_hidden_by_moderator
    FOREIGN KEY (hidden_by_moderator_id) REFERENCES users(id) ON DELETE SET NULL;

-- Index for recommended planner queries (excludes hidden planners)
CREATE INDEX idx_planner_recommended ON planners (published, deleted_at, hidden_from_recommended);
