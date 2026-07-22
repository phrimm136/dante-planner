-- Planner god-table decomposition, step 2b of 4: rebuild the entity and keyword
-- inverted indexes for every visible planner, reading the SAME JSON paths the
-- runtime extractor indexes: equipment.*.identity.id, equipment.*.egos.*.id
-- (egos as an object keyed by type; array-shaped egos are not indexed),
-- selectedGiftIds / observationGiftIds / comprehensiveGiftIds,
-- floorSelections[*].giftIds and floorSelections[*].themePackId. Entity ids are
-- integers by contract: non-numeric values are dropped by the REGEXP guard.
-- Keywords come from the rename-applied selected_keywords JSON array. Scope is
-- visible planners only, matching the clear-on-unpublish maintenance semantics.
--
-- Rerunnable: INSERT IGNORE on the natural PKs.

-- IDENTITY — equipment.*.identity.id (wildcard extract auto-wraps matches)
INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
SELECT 'IDENTITY', jt.id, c.planner_id
FROM planner_content c
JOIN planner_publication pub ON pub.planner_id = c.planner_id
JOIN planner_moderation m ON m.planner_id = c.planner_id
JOIN JSON_TABLE(JSON_EXTRACT(c.content, '$.equipment.*.identity.id'),
                '$[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
  AND jt.id REGEXP '^[0-9]+$';

-- EGO — equipment.*.egos.*.id (object-keyed egos only, as at runtime)
INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
SELECT 'EGO', jt.id, c.planner_id
FROM planner_content c
JOIN planner_publication pub ON pub.planner_id = c.planner_id
JOIN planner_moderation m ON m.planner_id = c.planner_id
JOIN JSON_TABLE(JSON_EXTRACT(c.content, '$.equipment.*.egos.*.id'),
                '$[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
  AND jt.id REGEXP '^[0-9]+$';

-- EGO_GIFT — the three top-level gift-id arrays
INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
SELECT 'EGO_GIFT', jt.id, c.planner_id
FROM planner_content c
JOIN planner_publication pub ON pub.planner_id = c.planner_id
JOIN planner_moderation m ON m.planner_id = c.planner_id
JOIN JSON_TABLE(c.content, '$.selectedGiftIds[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
  AND jt.id REGEXP '^[0-9]+$';

INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
SELECT 'EGO_GIFT', jt.id, c.planner_id
FROM planner_content c
JOIN planner_publication pub ON pub.planner_id = c.planner_id
JOIN planner_moderation m ON m.planner_id = c.planner_id
JOIN JSON_TABLE(c.content, '$.observationGiftIds[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
  AND jt.id REGEXP '^[0-9]+$';

INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
SELECT 'EGO_GIFT', jt.id, c.planner_id
FROM planner_content c
JOIN planner_publication pub ON pub.planner_id = c.planner_id
JOIN planner_moderation m ON m.planner_id = c.planner_id
JOIN JSON_TABLE(c.content, '$.comprehensiveGiftIds[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
  AND jt.id REGEXP '^[0-9]+$';

-- EGO_GIFT — per-floor gift ids
INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
SELECT 'EGO_GIFT', jt.id, c.planner_id
FROM planner_content c
JOIN planner_publication pub ON pub.planner_id = c.planner_id
JOIN planner_moderation m ON m.planner_id = c.planner_id
JOIN JSON_TABLE(c.content, '$.floorSelections[*]'
                COLUMNS (NESTED PATH '$.giftIds[*]' COLUMNS (id VARCHAR(20) PATH '$'))) jt
WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
  AND jt.id REGEXP '^[0-9]+$';

-- THEME_PACK — floorSelections[*].themePackId
INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
SELECT 'THEME_PACK', jt.id, c.planner_id
FROM planner_content c
JOIN planner_publication pub ON pub.planner_id = c.planner_id
JOIN planner_moderation m ON m.planner_id = c.planner_id
JOIN JSON_TABLE(c.content, '$.floorSelections[*]'
                COLUMNS (id VARCHAR(20) PATH '$.themePackId')) jt
WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
  AND jt.id REGEXP '^[0-9]+$';

-- Keywords — from the rename-applied selected_keywords JSON array
INSERT IGNORE INTO planner_keyword_filter (keyword, planner_id)
SELECT jt.kw, c.planner_id
FROM planner_content c
JOIN planner_publication pub ON pub.planner_id = c.planner_id
JOIN planner_moderation m ON m.planner_id = c.planner_id
JOIN JSON_TABLE(c.selected_keywords, '$[*]' COLUMNS (kw VARCHAR(64) PATH '$')) jt
WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
  AND jt.kw IS NOT NULL AND jt.kw <> '';
