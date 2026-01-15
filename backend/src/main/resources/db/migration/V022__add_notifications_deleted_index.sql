-- Add composite index for notification queries filtering by deleted_at
-- Query pattern: WHERE user_id = ? AND deleted_at IS NULL AND read = ? ORDER BY created_at DESC
-- This index supports the NotificationService.getInbox() query which filters on all these columns

CREATE INDEX idx_notifications_user_deleted_read_created
    ON notifications (user_id, deleted_at, `read`, created_at);

-- This index covers the query pattern:
-- SELECT * FROM notifications
-- WHERE user_id = :userId AND deleted_at IS NULL AND read = :read
-- ORDER BY created_at DESC
--
-- Performance improvement: Prevents full table scan on deleted_at IS NULL filter
-- as the notifications table grows with soft-deleted records over time.
