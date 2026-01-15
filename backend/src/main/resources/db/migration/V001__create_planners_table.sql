-- Planners table for storing user planner data
CREATE TABLE planners (
    id BINARY(16) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL DEFAULT 'Untitled',
    category ENUM('5F', '10F', '15F') NOT NULL,
    status ENUM('draft', 'saved') NOT NULL DEFAULT 'draft',
    content JSON NOT NULL,
    version INT NOT NULL DEFAULT 1,
    sync_version BIGINT NOT NULL DEFAULT 1,
    device_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    saved_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,

    CONSTRAINT fk_planner_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_modified (user_id, last_modified_at DESC),
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_deleted (user_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
