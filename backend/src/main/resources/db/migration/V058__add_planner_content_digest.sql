-- The backfill hashes the stored form of `content`, which is MySQL's re-serialization of the
-- JSON document rather than the bytes the author wrote. Those digests are internally consistent
-- and safe to compare against each other, but they are not evidence about author bytes; the
-- application recomputes a row's digest from the author's bytes on its first save after this
-- migration.

ALTER TABLE planner_content ADD COLUMN content_digest BINARY(32) NULL;

UPDATE planner_content SET content_digest = UNHEX(SHA2(content, 256));

ALTER TABLE planner_content MODIFY COLUMN content_digest BINARY(32) NOT NULL;
