-- Add user ban tracking and moderation audit trail
-- Enables permanent ban enforcement and immutable audit log for all moderation actions

-- Add ban tracking columns to users table
ALTER TABLE users ADD COLUMN banned_at TIMESTAMP(6) NULL;
ALTER TABLE users ADD COLUMN banned_by BIGINT NULL;

-- Add index for enforcement checks
CREATE INDEX idx_users_banned_at ON users(banned_at);

-- Add foreign key constraint for banned_by
ALTER TABLE users ADD CONSTRAINT fk_users_banned_by FOREIGN KEY (banned_by) REFERENCES users(id) ON DELETE SET NULL;

-- Create moderation_actions audit table
CREATE TABLE moderation_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    action_type VARCHAR(50) NOT NULL,
    actor_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    duration_minutes INT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_moderation_action_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_moderation_action_target FOREIGN KEY (target_id) REFERENCES users(id) ON DELETE RESTRICT,

    INDEX idx_moderation_target_created (target_id, created_at DESC),
    INDEX idx_moderation_actor (actor_id),
    INDEX idx_moderation_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
