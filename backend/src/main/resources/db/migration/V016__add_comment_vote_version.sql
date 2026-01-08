-- V016: Add version column to comment votes for optimistic locking
-- Prevents race conditions during concurrent vote toggles

ALTER TABLE planner_comment_votes
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
