ALTER TABLE planner_comments
    MODIFY COLUMN content TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ALGORITHM = INPLACE, LOCK = NONE;

DROP PROCEDURE IF EXISTS reconcile_comment_rows;

DROP PROCEDURE IF EXISTS reconcile_comment_counts;

CREATE PROCEDURE reconcile_comment_rows(IN p_step VARCHAR(20))
BEGIN
    DECLARE v_id BIGINT;
    SELECT MIN(comment_id) INTO v_id FROM reconcile_comment;
    WHILE v_id IS NOT NULL DO
        CASE p_step
            WHEN 'content_row' THEN
                INSERT IGNORE INTO planner_comment_content
                    (comment_id, content, edited_at, deleted_at, author_notifications_enabled)
                SELECT id, COALESCE(content, ''), edited_at, deleted_at, author_notifications_enabled
                FROM planner_comments
                WHERE id = v_id
                FOR SHARE;
            WHEN 'stats_row' THEN
                INSERT IGNORE INTO planner_comment_stats (comment_id, upvote_count)
                SELECT id, upvote_count
                FROM planner_comments
                WHERE id = v_id
                FOR SHARE;
            WHEN 'deletion' THEN
                UPDATE planner_comment_content t
                JOIN reconcile_comment r ON r.comment_id = t.comment_id
                SET t.deleted_at = r.deleted_at
                WHERE t.comment_id = v_id AND t.deleted_at IS NULL;
            WHEN 'new_text' THEN
                UPDATE planner_comment_content SET content = '' WHERE comment_id = v_id;
            WHEN 'old_text' THEN
                UPDATE planner_comments SET content = '' WHERE id = v_id;
        END CASE;
        COMMIT;
        SELECT MIN(comment_id) INTO v_id FROM reconcile_comment WHERE comment_id > v_id;
    END WHILE;
END;

CREATE PROCEDURE reconcile_comment_counts()
BEGIN
    DECLARE v_id BINARY(16);
    SELECT MIN(planner_id) INTO v_id FROM reconcile_count;
    WHILE v_id IS NOT NULL DO
        UPDATE planner_stats s
        JOIN reconcile_count r ON r.planner_id = s.planner_id
        SET s.comment_count = r.live_comments
        WHERE s.planner_id = v_id AND s.comment_count = r.counted;
        COMMIT;
        SELECT MIN(planner_id) INTO v_id FROM reconcile_count WHERE planner_id > v_id;
    END WHILE;
END;

SET @isolation_before_reconcile = @@SESSION.transaction_isolation;

SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

CREATE TEMPORARY TABLE reconcile_comment (PRIMARY KEY (comment_id)) AS
SELECT c.id AS comment_id
FROM planner_comments c
LEFT JOIN planner_comment_content t ON t.comment_id = c.id
WHERE t.comment_id IS NULL;

COMMIT;

CALL reconcile_comment_rows('content_row');

DROP TEMPORARY TABLE reconcile_comment;

CREATE TEMPORARY TABLE reconcile_comment (PRIMARY KEY (comment_id)) AS
SELECT c.id AS comment_id
FROM planner_comments c
LEFT JOIN planner_comment_stats s ON s.comment_id = c.id
WHERE s.comment_id IS NULL;

COMMIT;

CALL reconcile_comment_rows('stats_row');

DROP TEMPORARY TABLE reconcile_comment;

CREATE TEMPORARY TABLE reconcile_comment (PRIMARY KEY (comment_id)) AS
SELECT c.id AS comment_id, c.deleted_at
FROM planner_comments c
JOIN planner_comment_content t ON t.comment_id = c.id
WHERE t.deleted_at IS NULL
  AND c.deleted_at IS NOT NULL;

COMMIT;

CALL reconcile_comment_rows('deletion');

DROP TEMPORARY TABLE reconcile_comment;

CREATE TEMPORARY TABLE reconcile_comment (PRIMARY KEY (comment_id)) AS
SELECT t.comment_id
FROM planner_comment_content t
JOIN planner_comments c ON c.id = t.comment_id
WHERE (t.deleted_at IS NOT NULL OR c.user_id = 0)
  AND CHAR_LENGTH(t.content) > 0;

COMMIT;

CALL reconcile_comment_rows('new_text');

DROP TEMPORARY TABLE reconcile_comment;

CREATE TEMPORARY TABLE reconcile_comment (PRIMARY KEY (comment_id)) AS
SELECT c.id AS comment_id
FROM planner_comments c
JOIN planner_comment_content t ON t.comment_id = c.id
WHERE (t.deleted_at IS NOT NULL OR c.user_id = 0)
  AND CHAR_LENGTH(c.content) > 0;

COMMIT;

CALL reconcile_comment_rows('old_text');

DROP TEMPORARY TABLE reconcile_comment;

CREATE TEMPORARY TABLE reconcile_count (PRIMARY KEY (planner_id)) AS
SELECT s.planner_id, s.comment_count AS counted, COALESCE(live.live_comments, 0) AS live_comments
FROM planner_stats s
LEFT JOIN (
    SELECT c.planner_id, COUNT(*) AS live_comments
    FROM planner_comments c
    JOIN planner_comment_content t ON t.comment_id = c.id
    WHERE t.deleted_at IS NULL
    GROUP BY c.planner_id
) live ON live.planner_id = s.planner_id
WHERE s.comment_count <> COALESCE(live.live_comments, 0);

COMMIT;

CALL reconcile_comment_counts();

DROP TEMPORARY TABLE reconcile_count;

SET SESSION transaction_isolation = @isolation_before_reconcile;

DROP PROCEDURE reconcile_comment_rows;

DROP PROCEDURE reconcile_comment_counts;
