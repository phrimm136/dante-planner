-- The digest was computed over the write path's normalized rendering of the content document,
-- which no client can reproduce from its own bytes. With the wire exposure withdrawn, the column
-- has no reader: stale writes are now arbitrated by comparing the content as a parsed JSON tree.

ALTER TABLE planner_content DROP COLUMN content_digest;
