-- Backfill planner_content_index for existing published plans.
-- Uses MySQL JSON_TABLE to extract entity IDs from the content JSON column.
-- INSERT IGNORE ensures idempotency: safe on fresh DB (no-op) and on re-run (skips duplicates).

-- Identities: equipment.*.identity.id
INSERT IGNORE INTO planner_content_index (planner_id, entity_type, entity_id)
SELECT p.id, 'IDENTITY', jt.identity_id
FROM planners p,
JSON_TABLE(p.content, '$.equipment.*' COLUMNS (
    identity_id VARCHAR(20) PATH '$.identity.id'
)) AS jt
WHERE p.published = true AND p.deleted_at IS NULL
  AND jt.identity_id IS NOT NULL;

-- EGOs: equipment.*.egos.*.id
INSERT IGNORE INTO planner_content_index (planner_id, entity_type, entity_id)
SELECT p.id, 'EGO', jt.ego_id
FROM planners p,
JSON_TABLE(p.content, '$.equipment.*' COLUMNS (
    NESTED PATH '$.egos.*' COLUMNS (
        ego_id VARCHAR(20) PATH '$.id'
    )
)) AS jt
WHERE p.published = true AND p.deleted_at IS NULL
  AND jt.ego_id IS NOT NULL;

-- EGO Gifts: selectedGiftIds
INSERT IGNORE INTO planner_content_index (planner_id, entity_type, entity_id)
SELECT p.id, 'EGO_GIFT', jt.gift_id
FROM planners p,
JSON_TABLE(p.content, '$.selectedGiftIds[*]' COLUMNS (
    gift_id VARCHAR(20) PATH '$'
)) AS jt
WHERE p.published = true AND p.deleted_at IS NULL
  AND jt.gift_id IS NOT NULL;

-- EGO Gifts: observationGiftIds
INSERT IGNORE INTO planner_content_index (planner_id, entity_type, entity_id)
SELECT p.id, 'EGO_GIFT', jt.gift_id
FROM planners p,
JSON_TABLE(p.content, '$.observationGiftIds[*]' COLUMNS (
    gift_id VARCHAR(20) PATH '$'
)) AS jt
WHERE p.published = true AND p.deleted_at IS NULL
  AND jt.gift_id IS NOT NULL;

-- EGO Gifts: comprehensiveGiftIds
INSERT IGNORE INTO planner_content_index (planner_id, entity_type, entity_id)
SELECT p.id, 'EGO_GIFT', jt.gift_id
FROM planners p,
JSON_TABLE(p.content, '$.comprehensiveGiftIds[*]' COLUMNS (
    gift_id VARCHAR(20) PATH '$'
)) AS jt
WHERE p.published = true AND p.deleted_at IS NULL
  AND jt.gift_id IS NOT NULL;

-- EGO Gifts: floorSelections[*].giftIds[*]
INSERT IGNORE INTO planner_content_index (planner_id, entity_type, entity_id)
SELECT p.id, 'EGO_GIFT', jt.gift_id
FROM planners p,
JSON_TABLE(p.content, '$.floorSelections[*]' COLUMNS (
    NESTED PATH '$.giftIds[*]' COLUMNS (
        gift_id VARCHAR(20) PATH '$'
    )
)) AS jt
WHERE p.published = true AND p.deleted_at IS NULL
  AND jt.gift_id IS NOT NULL;

-- Theme Packs: floorSelections[*].themePackId (non-null only)
INSERT IGNORE INTO planner_content_index (planner_id, entity_type, entity_id)
SELECT p.id, 'THEME_PACK', jt.theme_pack_id
FROM planners p,
JSON_TABLE(p.content, '$.floorSelections[*]' COLUMNS (
    theme_pack_id VARCHAR(20) PATH '$.themePackId'
)) AS jt
WHERE p.published = true AND p.deleted_at IS NULL
  AND jt.theme_pack_id IS NOT NULL;
