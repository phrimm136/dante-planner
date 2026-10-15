DROP PROCEDURE IF EXISTS rebuild_planner_filters_scoped;

CREATE PROCEDURE rebuild_planner_filters_scoped(IN p_planner_id BINARY(16), IN p_floor_count INT)
BEGIN
    CREATE TEMPORARY TABLE IF NOT EXISTS tmp_pef_new (
        entity_type ENUM('IDENTITY','EGO','EGO_GIFT','THEME_PACK') NOT NULL,
        entity_id INT UNSIGNED NOT NULL, planner_id BINARY(16) NOT NULL,
        PRIMARY KEY (entity_type, entity_id, planner_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    CREATE TEMPORARY TABLE IF NOT EXISTS tmp_pkf_new (
        keyword VARCHAR(64) NOT NULL, planner_id BINARY(16) NOT NULL,
        PRIMARY KEY (keyword, planner_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    DELETE FROM tmp_pef_new;
    DELETE FROM tmp_pkf_new;


    INSERT IGNORE INTO tmp_pef_new (entity_type, entity_id, planner_id)
    SELECT 'IDENTITY', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(JSON_EXTRACT(c.content, '$.equipment.*.identity.id'),
                    '$[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO tmp_pef_new (entity_type, entity_id, planner_id)
    SELECT 'EGO', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(JSON_EXTRACT(c.content, '$.equipment.*.egos.*.id'),
                    '$[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO tmp_pef_new (entity_type, entity_id, planner_id)
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

    INSERT IGNORE INTO tmp_pef_new (entity_type, entity_id, planner_id)
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

    INSERT IGNORE INTO tmp_pef_new (entity_type, entity_id, planner_id)
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

    INSERT IGNORE INTO tmp_pef_new (entity_type, entity_id, planner_id)
    SELECT 'EGO_GIFT',
           IF(CAST(jt.id AS UNSIGNED) BETWEEN 19000 AND 19999
              OR CAST(jt.id AS UNSIGNED) BETWEEN 29000 AND 29999,
              MOD(CAST(jt.id AS UNSIGNED), 10000), CAST(jt.id AS UNSIGNED)),
           c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.floorSelections[*]'
                    COLUMNS (floor_no FOR ORDINALITY,
                             NESTED PATH '$.giftIds[*]' COLUMNS (id VARCHAR(20) PATH '$'))) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.floor_no <= p_floor_count
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO tmp_pef_new (entity_type, entity_id, planner_id)
    SELECT 'THEME_PACK', jt.id, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.content, '$.floorSelections[*]'
                    COLUMNS (floor_no FOR ORDINALITY, id VARCHAR(20) PATH '$.themePackId')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.floor_no <= p_floor_count
      AND jt.id REGEXP '^[0-9]+$';

    INSERT IGNORE INTO tmp_pkf_new (keyword, planner_id)
    SELECT jt.kw, c.planner_id
    FROM planner_content c
    JOIN planner_publication pub ON pub.planner_id = c.planner_id
    JOIN planner_moderation m ON m.planner_id = c.planner_id
    JOIN JSON_TABLE(c.selected_keywords, '$[*]' COLUMNS (kw VARCHAR(64) PATH '$')) jt
    WHERE c.planner_id = p_planner_id
      AND pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
      AND jt.kw IS NOT NULL AND jt.kw <> '';
    DELETE f FROM planner_entity_filter f
    LEFT JOIN tmp_pef_new n ON n.entity_type = f.entity_type AND n.entity_id = f.entity_id
         AND n.planner_id = f.planner_id
    WHERE f.planner_id = p_planner_id AND n.entity_id IS NULL;
    DELETE f FROM planner_keyword_filter f
    LEFT JOIN tmp_pkf_new n ON n.keyword COLLATE utf8mb4_0900_bin = f.keyword COLLATE utf8mb4_0900_bin AND n.planner_id = f.planner_id
    WHERE f.planner_id = p_planner_id AND n.keyword IS NULL;
    INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
    SELECT n.entity_type, n.entity_id, n.planner_id FROM tmp_pef_new n
    WHERE NOT EXISTS (SELECT 1 FROM planner_entity_filter f WHERE f.entity_type = n.entity_type
                      AND f.entity_id = n.entity_id AND f.planner_id = n.planner_id);
    INSERT IGNORE INTO planner_keyword_filter (keyword, planner_id)
    SELECT n.keyword, n.planner_id FROM tmp_pkf_new n
    WHERE NOT EXISTS (SELECT 1 FROM planner_keyword_filter f WHERE f.keyword COLLATE utf8mb4_0900_bin = n.keyword COLLATE utf8mb4_0900_bin
                      AND f.planner_id = n.planner_id);
END;
