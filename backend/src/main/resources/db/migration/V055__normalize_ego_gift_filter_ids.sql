-- The gift filter index stores the base gift id, not the enhanced encoding the
-- content document carries: content writes an enhanced gift as its level prefixed
-- onto the four-digit base (19154 and 29154 both mean 9154), while the index only
-- answers which planners contain a gift. Existing rows are collapsed here; the
-- INSERT IGNORE absorbs planners already holding both forms, which the primary key
-- then merges into one row. The collapse below must stay identical to
-- PlannerContentEntityExtractor.baseGiftId, which writes the same index from Java.

INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
SELECT entity_type, MOD(entity_id, 10000), planner_id
FROM planner_entity_filter
WHERE entity_type = 'EGO_GIFT'
  AND (entity_id BETWEEN 19000 AND 19999 OR entity_id BETWEEN 29000 AND 29999);

DELETE FROM planner_entity_filter
WHERE entity_type = 'EGO_GIFT'
  AND (entity_id BETWEEN 19000 AND 19999 OR entity_id BETWEEN 29000 AND 29999);

DROP PROCEDURE IF EXISTS rebuild_planner_filters;

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
    SELECT 'EGO_GIFT',
           IF(CAST(jt.id AS UNSIGNED) BETWEEN 19000 AND 19999
              OR CAST(jt.id AS UNSIGNED) BETWEEN 29000 AND 29999,
              MOD(CAST(jt.id AS UNSIGNED), 10000), CAST(jt.id AS UNSIGNED)),
           c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.selectedGiftIds[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'EGO_GIFT',
           IF(CAST(jt.id AS UNSIGNED) BETWEEN 19000 AND 19999
              OR CAST(jt.id AS UNSIGNED) BETWEEN 29000 AND 29999,
              MOD(CAST(jt.id AS UNSIGNED), 10000), CAST(jt.id AS UNSIGNED)),
           c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.observationGiftIds[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'EGO_GIFT',
           IF(CAST(jt.id AS UNSIGNED) BETWEEN 19000 AND 19999
              OR CAST(jt.id AS UNSIGNED) BETWEEN 29000 AND 29999,
              MOD(CAST(jt.id AS UNSIGNED), 10000), CAST(jt.id AS UNSIGNED)),
           c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.comprehensiveGiftIds[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT 'EGO_GIFT',
           IF(CAST(jt.id AS UNSIGNED) BETWEEN 19000 AND 19999
              OR CAST(jt.id AS UNSIGNED) BETWEEN 29000 AND 29999,
              MOD(CAST(jt.id AS UNSIGNED), 10000), CAST(jt.id AS UNSIGNED)),
           c.planner_id
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
