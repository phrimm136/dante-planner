-- The backfill hashes the stored form of `content`, which is MySQL's re-serialization of the
-- JSON document rather than the string a client sent. Those digests are internally consistent
-- and safe to compare against each other, but they are not evidence about any client's bytes.
-- Lineage is re-established per row on its first save that carries a content document.
--
-- NOT rerunnable: MySQL has no ADD COLUMN IF NOT EXISTS, so a second run fails on the first
-- statement. Recovering from a failure part-way through means dropping content_digest by hand
-- and running flyway repair before retrying.

ALTER TABLE planner_content ADD COLUMN content_digest BINARY(32) NULL;

UPDATE planner_content SET content_digest = UNHEX(SHA2(content, 256));

ALTER TABLE planner_content MODIFY COLUMN content_digest BINARY(32) NOT NULL;
