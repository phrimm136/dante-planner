-- Planner god-table decomposition, step 3+4 of 4: repoint the child-table FKs
-- from planners(id) to the write-once planner(id) core, verify backfill parity,
-- then drop the legacy planners table and the replaced planner_content_index.
--
-- Ordered so the parity assertion runs BEFORE any drop: a failed assertion
-- aborts the migration while the source rows are still intact. Every step is
-- guarded against re-execution (MySQL DDL is non-transactional).

DROP PROCEDURE IF EXISTS planner_decompose_repoint;

CREATE PROCEDURE planner_decompose_repoint(
    IN p_table VARCHAR(64), IN p_fk VARCHAR(64), IN p_cascade BOOLEAN)
BEGIN
    -- Repoint only while the FK still targets the legacy planners table.
    IF EXISTS (SELECT 1 FROM information_schema.key_column_usage
               WHERE constraint_schema = DATABASE()
                 AND table_name = p_table
                 AND constraint_name = p_fk
                 AND referenced_table_name = 'planners') THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' DROP FOREIGN KEY ', p_fk);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        SET @ddl = CONCAT('ALTER TABLE ', p_table,
            ' ADD CONSTRAINT ', p_fk,
            ' FOREIGN KEY (planner_id) REFERENCES planner(id)',
            IF(p_cascade, ' ON DELETE CASCADE', ''));
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END;

CALL planner_decompose_repoint('planner_votes',         'fk_vote_planner',         TRUE);
CALL planner_decompose_repoint('planner_views',         'fk_view_planner',         TRUE);
CALL planner_decompose_repoint('planner_comments',      'fk_comment_planner',      TRUE);
CALL planner_decompose_repoint('planner_bookmarks',     'fk_bookmark_planner',     TRUE);
CALL planner_decompose_repoint('planner_subscriptions', 'fk_subscription_planner', TRUE);
CALL planner_decompose_repoint('planner_reports',       'fk_report_planner',       FALSE);

DROP PROCEDURE planner_decompose_repoint;

-- planner_stats keeps NO foreign key: counters are swept app-side (D-series
-- invariant: FKs point only at users or the write-once core).
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'planner_stats'
      AND constraint_name = 'fk_planner_stats_planner');
SET @ddl = IF(@fk_exists > 0,
    'ALTER TABLE planner_stats DROP FOREIGN KEY fk_planner_stats_planner',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- Parity assertion — runs before the drops; SIGNAL aborts the migration.
-- ============================================================================

DROP PROCEDURE IF EXISTS assert_planner_decomposition_parity;

CREATE PROCEDURE assert_planner_decomposition_parity()
BEGIN
    -- Skip when the legacy table is already gone (successful earlier run).
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'planners') THEN

        -- Every legacy row (drafts and soft-deleted included) must exist on
        -- each aggregate table before the source is dropped.
        IF (SELECT COUNT(*) FROM planners) <> (SELECT COUNT(*) FROM planner) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: planner core count != planners count';
        END IF;
        IF (SELECT COUNT(*) FROM planners) <> (SELECT COUNT(*) FROM planner_content) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: planner_content count != planners count';
        END IF;
        IF (SELECT COUNT(*) FROM planners) <> (SELECT COUNT(*) FROM planner_publication) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: planner_publication count != planners count';
        END IF;
        IF (SELECT COUNT(*) FROM planners) <> (SELECT COUNT(*) FROM planner_moderation) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: planner_moderation count != planners count';
        END IF;

        -- Stats must cover every planner (V050 upserts a row per source row).
        IF (SELECT COUNT(*) FROM planner_stats) < (SELECT COUNT(*) FROM planners) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: planner_stats is missing rows';
        END IF;

        -- Catalog membership == visibility.
        IF (SELECT COUNT(*) FROM planner_catalog) <>
           (SELECT COUNT(*) FROM planners
            WHERE published = TRUE AND deleted_at IS NULL AND taken_down_at IS NULL) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: planner_catalog count != visible planners count';
        END IF;

        -- Counter value parity: the backfill overwrote stats from the legacy
        -- columns, so any difference means rows were missed, not drift.
        IF (SELECT COALESCE(SUM(view_count),0) + COALESCE(SUM(upvotes),0) FROM planners) <>
           (SELECT COALESCE(SUM(s.view_count),0) + COALESCE(SUM(s.upvotes),0)
            FROM planner_stats s JOIN planners p ON p.id = s.planner_id) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: stats counter sums != planners counter sums';
        END IF;

    END IF;
END;

CALL assert_planner_decomposition_parity();

DROP PROCEDURE assert_planner_decomposition_parity;

-- ============================================================================
-- Drops — only reached when parity held.
-- ============================================================================

DROP TABLE IF EXISTS planner_content_index;
DROP TABLE IF EXISTS planners;

-- With the legacy fk_planner_user gone, the core FK takes its canonical name.
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'planner'
      AND constraint_name = 'fk_planner_user_core');
SET @ddl = IF(@fk_exists > 0,
    'ALTER TABLE planner DROP FOREIGN KEY fk_planner_user_core',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'planner'
      AND constraint_name = 'fk_planner_user');
SET @ddl = IF(@fk_exists = 0,
    'ALTER TABLE planner ADD CONSTRAINT fk_planner_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
