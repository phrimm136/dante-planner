-- Planner god-table decomposition, step 2 of 4: backfill the aggregate tables
-- and the visible-only catalog from the legacy planners rows. All existing data
-- (drafts and soft-deleted rows included) is preserved on the aggregate;
-- planner_catalog is seeded ONLY for visible rows (published AND NOT deleted
-- AND NOT taken down). The entity/keyword filter backfill is V051 (app-side,
-- same code path as runtime maintenance).
--
-- Rerunnable: INSERT IGNORE keyed on the shared PK; stats upsert overwrites
-- with the authoritative legacy counters.

INSERT IGNORE INTO planner (id, user_id, planner_type, created_at)
SELECT id, user_id, planner_type, created_at
FROM planners;

INSERT IGNORE INTO planner_content (
    planner_id, title, status, category, selected_keywords, content,
    content_schema_version, game_content_version, sync_version,
    row_lock_version, device_id, last_modified_at, deleted_at)
SELECT
    id,
    title,
    status,
    category,
    CASE WHEN selected_keywords IS NULL OR selected_keywords = '' THEN NULL
         ELSE CONCAT('["', REPLACE(selected_keywords, ',', '","'), '"]')
    END,
    content,
    schema_version,
    content_version,
    sync_version,
    COALESCE(version, 0),
    CASE WHEN device_id REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
         THEN UUID_TO_BIN(device_id)
         ELSE NULL
    END,
    last_modified_at,
    deleted_at
FROM planners;

INSERT IGNORE INTO planner_publication (planner_id, published, first_published_at, owner_notifications_enabled)
SELECT id, published, first_published_at, owner_notifications_enabled
FROM planners;

INSERT IGNORE INTO planner_moderation (
    planner_id, taken_down_at, hidden_from_recommended,
    hidden_by_moderator_id, hidden_reason, hidden_at)
SELECT id, taken_down_at, hidden_from_recommended,
       hidden_by_moderator_id, hidden_reason, hidden_at
FROM planners;

-- Legacy planners counters are the served truth up to this migration; overwrite
-- any drifted dual-write value and create rows the create-path never seeded.
INSERT INTO planner_stats (planner_id, view_count, upvotes, comment_count, recommended_notified_at)
SELECT id, view_count, upvotes, 0, recommended_notified_at
FROM planners
ON DUPLICATE KEY UPDATE
    view_count = VALUES(view_count),
    upvotes = VALUES(upvotes),
    recommended_notified_at = VALUES(recommended_notified_at);

UPDATE planner_stats s
JOIN (SELECT planner_id, COUNT(*) AS live_comments
      FROM planner_comments
      WHERE deleted_at IS NULL
      GROUP BY planner_id) c ON c.planner_id = s.planner_id
SET s.comment_count = c.live_comments;

-- Visible rows only; recommended derived from the counters and moderation
-- state (threshold mirrors planner.recommended-threshold).
INSERT IGNORE INTO planner_catalog (
    planner_id, planner_type, category, title, selected_keywords,
    first_published_at, recommended)
SELECT
    id,
    planner_type,
    category,
    title,
    CASE WHEN selected_keywords IS NULL OR selected_keywords = '' THEN NULL
         ELSE CONCAT('["', REPLACE(selected_keywords, ',', '","'), '"]')
    END,
    COALESCE(first_published_at, created_at),
    (upvotes >= 10 AND hidden_from_recommended = FALSE)
FROM planners
WHERE published = TRUE
  AND deleted_at IS NULL
  AND taken_down_at IS NULL;
