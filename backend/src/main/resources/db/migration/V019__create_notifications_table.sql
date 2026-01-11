-- V019: Create notifications table
-- Supports user notifications for planner milestones, comments, and replies

CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content_id VARCHAR(255) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    `read` BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,

    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    INDEX idx_notifications_user_read (user_id, `read`, created_at DESC),
    INDEX idx_notifications_created (created_at),
    UNIQUE KEY uk_notification_dedup (user_id, content_id, notification_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
