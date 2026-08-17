-- Rebuild both filter inverted indexes for one planner inside the caller's
-- transaction, in a single CALL round trip: clear unconditionally, then
-- re-extract from the stored content only while the planner is visible
-- (published AND not deleted AND not taken down), so a rebuild racing an
-- unpublish cannot resurrect rows. Extraction reads the SAME JSON paths as
-- the V051 backfill and the reconciler's Java oracle
-- (PlannerContentEntityExtractor): equipment.*.identity.id,
-- equipment.*.egos.*.id (object-keyed egos only), the three top-level
-- gift-id arrays, floorSelections[*].giftIds and
-- floorSelections[*].themePackId. Entity ids are integers by contract:
-- non-numeric values are dropped by the REGEXP guard.

CREATE PROCEDURE rebuild_planner_filters(IN p_planner_id BINARY(16))
BEGIN
    DELETE FROM planner_entity_filter WHERE planner_id = p_planner_id;
    DELETE FROM planner_keyword_filter WHERE planner_id = p_planner_id;

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'IDENTITY', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(JSON_EXTRACT(c.content, '$.equipment.*.identity.id'),
                    '$[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'EGO', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(JSON_EXTRACT(c.content, '$.equipment.*.egos.*.id'),
                    '$[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'EGO_GIFT', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.selectedGiftIds[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'EGO_GIFT', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.observationGiftIds[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'EGO_GIFT', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.comprehensiveGiftIds[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'EGO_GIFT', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.floorSelections[*]'
                    COLUMNS (NESTED PATH '$.giftIds[*]' COLUMNS (id VARCHAR(20) PATH '$'))) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'THEME_PACK', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.floorSelections[*]'
                    COLUMNS (id VARCHAR(20) PATH '$.themePackId')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_keyword_filter (keyword, planner_id)
    SELECT jt.kw, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.selected_keywords, '$[*]' COLUMNS (kw VARCHAR(64) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.kw IS NOT NULL AND jt.kw <> '';
END
