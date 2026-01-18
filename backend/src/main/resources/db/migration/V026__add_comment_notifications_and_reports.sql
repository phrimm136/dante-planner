-- V026: Add comment notification toggles and comment report system

-- Add author notification toggle to planner_comments
ALTER TABLE planner_comments
    ADD COLUMN author_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Add owner notification toggle to planners
ALTER TABLE planners
    ADD COLUMN owner_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Comment reports table for user reports on comments
CREATE TABLE planner_comment_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_comment_report_comment FOREIGN KEY (comment_id) REFERENCES planner_comments(id),
    CONSTRAINT fk_comment_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_comment_report_reporter_comment (reporter_id, comment_id),
    INDEX idx_comment_report_comment (comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
