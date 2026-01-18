-- V025: Create planner_subscriptions and planner_reports tables
-- Supports subscription notifications and user reports for planners

-- Planner subscriptions table for tracking notification preferences
CREATE TABLE planner_subscriptions (
    user_id BIGINT NOT NULL,
    planner_id BINARY(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, planner_id),
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_planner FOREIGN KEY (planner_id) REFERENCES planners(id) ON DELETE CASCADE,
    INDEX idx_subscription_planner (planner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Planner reports table for user reports (preserved for moderation audit trail)
CREATE TABLE planner_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    planner_id BINARY(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_report_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_report_planner FOREIGN KEY (planner_id) REFERENCES planners(id),
    UNIQUE KEY uk_report_user_planner (user_id, planner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
