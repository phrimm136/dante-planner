SET @isolation_before_read_state = @@SESSION.transaction_isolation;

SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

CREATE TEMPORARY TABLE dismissed_notification (PRIMARY KEY (id)) AS
SELECT id
FROM notifications
WHERE deleted_at IS NOT NULL
  AND read_at IS NULL;

COMMIT;

UPDATE dismissed_notification d
STRAIGHT_JOIN notifications n ON n.id = d.id
SET n.read_at = n.deleted_at
WHERE n.read_at IS NULL;

COMMIT;

DROP TEMPORARY TABLE dismissed_notification;

SET SESSION transaction_isolation = @isolation_before_read_state;

ALTER TABLE notifications
    ADD INDEX idx_notifications_user_read_at (user_id, read_at, created_at),
    ALGORITHM = INPLACE, LOCK = NONE;
