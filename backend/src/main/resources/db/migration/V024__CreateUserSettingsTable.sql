-- V024: Create user_settings table for per-user sync and notification preferences
-- user_id is both PK and FK to users (one-to-one relationship)
-- sync_enabled NULL = user hasn't chosen yet (triggers first-login dialog)

CREATE TABLE IF NOT EXISTS user_settings (
    user_id BIGINT NOT NULL PRIMARY KEY,
    sync_enabled BOOLEAN NULL,
    notify_comments BOOLEAN NOT NULL DEFAULT TRUE,
    notify_recommendations BOOLEAN NOT NULL DEFAULT TRUE,
    notify_new_publications BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
