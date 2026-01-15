-- Rollback: See V007 for index rename

-- Add view count column to planners table
ALTER TABLE planners
    ADD COLUMN view_count INT NOT NULL DEFAULT 0;

-- Add index for sorting by view count
ALTER TABLE planners
    ADD INDEX idx_published_views (published, view_count DESC);

-- Planner bookmarks table for tracking user bookmarks
CREATE TABLE planner_bookmarks (
    user_id BIGINT NOT NULL,
    planner_id BINARY(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, planner_id),
    CONSTRAINT fk_bookmark_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_bookmark_planner FOREIGN KEY (planner_id) REFERENCES planners(id) ON DELETE CASCADE,
    INDEX idx_planner_bookmarks (planner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
