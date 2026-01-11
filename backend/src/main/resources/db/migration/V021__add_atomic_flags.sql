-- V021: Add atomic flags to planners table
-- version: Optimistic locking to prevent concurrent update conflicts
-- recommended_notified_at: Atomic flag to ensure exactly-one notification delivery

ALTER TABLE planners
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN recommended_notified_at TIMESTAMP NULL;
