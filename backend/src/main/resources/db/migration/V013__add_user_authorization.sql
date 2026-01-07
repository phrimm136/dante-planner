-- Add user role column (NORMAL is default for existing and new users)
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'NORMAL';

-- Add timeout column (nullable - null means no timeout)
ALTER TABLE users ADD COLUMN timeout_until TIMESTAMP WITH TIME ZONE;

-- Index for efficient role queries (e.g., counting admins)
CREATE INDEX idx_users_role ON users(role);
