-- Add public_id UUID to notifications table
-- Follows same pattern as planner_comments.public_id

ALTER TABLE notifications
ADD COLUMN public_id BINARY(16) NULL;

-- Generate UUIDs for existing rows
UPDATE notifications SET public_id = UUID_TO_BIN(UUID()) WHERE public_id IS NULL;

-- Make column NOT NULL and add unique constraint
ALTER TABLE notifications
MODIFY COLUMN public_id BINARY(16) NOT NULL,
ADD CONSTRAINT uk_notification_public_id UNIQUE (public_id);

-- Add index for lookups by public_id
CREATE INDEX idx_notification_public_id ON notifications(public_id);
