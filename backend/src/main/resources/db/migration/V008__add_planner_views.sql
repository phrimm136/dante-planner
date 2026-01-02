-- Planner views table for tracking unique daily views with deduplication
-- Composite PK: (planner_id, viewer_hash, view_date) ensures one view per viewer per day

CREATE TABLE planner_views (
    planner_id CHAR(36) NOT NULL,
    viewer_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256 hash of viewer identifier',
    view_date DATE NOT NULL COMMENT 'UTC date of view for daily deduplication',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (planner_id, viewer_hash, view_date),
    CONSTRAINT fk_view_planner FOREIGN KEY (planner_id) REFERENCES planners(id) ON DELETE CASCADE,
    INDEX idx_view_date (view_date) COMMENT 'For future cleanup queries'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
