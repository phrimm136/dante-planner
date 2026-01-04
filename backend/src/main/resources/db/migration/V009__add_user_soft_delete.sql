-- User soft-delete support for account deletion with 30-day grace period
-- deleted_at: when user requested deletion (NULL = active account)
-- permanent_delete_scheduled_at: when hard delete will occur

ALTER TABLE users
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN permanent_delete_scheduled_at TIMESTAMP NULL;

-- Index for finding users pending permanent deletion
CREATE INDEX idx_users_deleted_at ON users(deleted_at);
CREATE INDEX idx_users_permanent_delete ON users(permanent_delete_scheduled_at);

-- Sentinel user for reassigning votes after hard delete
-- ID=0 is reserved and will never be returned by IDENTITY generation
INSERT INTO users (id, email, provider, provider_id, created_at, updated_at)
VALUES (0, '[deleted]', 'system', 'DELETED_USER_SENTINEL', NOW(), NOW());
