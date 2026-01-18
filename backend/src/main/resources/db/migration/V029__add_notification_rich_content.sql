-- Add rich content fields to notifications for display and navigation
-- These fields are nullable for backward compatibility with existing notifications

ALTER TABLE notifications
    ADD COLUMN planner_id BINARY(16) NULL AFTER deleted_at,
    ADD COLUMN planner_title VARCHAR(100) NULL AFTER planner_id,
    ADD COLUMN comment_snippet VARCHAR(100) NULL AFTER planner_title,
    ADD COLUMN comment_public_id BINARY(16) NULL AFTER comment_snippet;

-- Index for looking up notifications by planner (if needed for future features)
CREATE INDEX idx_notifications_planner ON notifications(planner_id);
