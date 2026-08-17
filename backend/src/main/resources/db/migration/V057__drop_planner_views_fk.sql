-- planner_views gives up its foreign key to the planner core. MySQL refuses to
-- partition a table that owns a foreign key, and the partitioning key for this
-- table is view_date.
--
-- The ON DELETE CASCADE the constraint carried is now an explicit set-based
-- delete in the planner account purge, run in the same transaction and before
-- the planner core rows go. The other five child foreign keys keep theirs.
--
-- The constraint's index need is served by the leftmost prefix of the primary
-- key (planner_id, viewer_hash, view_date), so the drop leaves no index behind.
--
-- Guarded against re-execution; MySQL DDL is non-transactional.

SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'planner_views'
      AND constraint_name = 'fk_view_planner');
SET @ddl = IF(@fk_exists > 0,
    'ALTER TABLE planner_views DROP FOREIGN KEY fk_view_planner',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
