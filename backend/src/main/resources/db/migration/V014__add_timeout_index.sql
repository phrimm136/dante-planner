-- Add index for timeout lookups
-- Optimizes queries that filter by timeout_until (e.g., finding timed-out users)
CREATE INDEX idx_users_timeout_until ON users(timeout_until)
WHERE timeout_until IS NOT NULL;
