-- Widen notification timestamps to microsecond precision.
-- Default MySQL TIMESTAMP is whole-second; notification listings order by
-- created_at, so sub-second resolution is required to keep ordering stable for
-- notifications created within the same second.
ALTER TABLE notifications
    MODIFY created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY read_at    TIMESTAMP(6) NULL,
    MODIFY deleted_at TIMESTAMP(6) NULL;
