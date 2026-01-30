-- Add explicit index on username_suffix for moderation lookups
-- MySQL doesn't support partial indexes, so using compound index with deleted_at
-- This optimizes queries like: WHERE username_suffix = ? AND deleted_at IS NULL

CREATE INDEX idx_users_username_suffix_deleted ON users(username_suffix, deleted_at);
