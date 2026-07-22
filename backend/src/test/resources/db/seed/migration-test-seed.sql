-- Migration smoke-test seed data
-- Purpose: Populate every table so future migrations that ALTER/MODIFY columns
--          are tested against non-empty tables (catches strict-mode failures).
--
-- CI flow:
--   1. Run EXISTING migrations (merged to main)
--   2. Run this seed AS OF THE BASE BRANCH (simulates production data)
--   3. Run NEW migrations (from the PR under test)
--   If step 3 fails, the new migration is unsafe against populated tables.
--   Because step 2 uses the base-branch copy, this file must always match the
--   schema after all MERGED migrations — update it in the same PR as a schema
--   change so the NEXT PR seeds correctly.
--
-- Maintenance:
--   - ENUM/JSON values must match the schema after all MERGED migrations
--   - Every keyword and ENUM value should appear in at least one row
--
-- Schema version: V052 (planner aggregate + projections; planners table gone)
--
-- Coverage:
--   - selected_keywords: all 35 keywords across 4 planners (JSON arrays)
--   - planner_type: MIRROR_DUNGEON, REFRACTED_RAILWAY
--   - status: draft, saved
--   - vote_type: UP
--   - entity_type: IDENTITY, EGO, EGO_GIFT, THEME_PACK
--   - content: JSON with equipment, gifts, floorSelections structure
--   - planner_catalog: visible rows only; one recommended (upvotes >= 10)

-- ============================================================================
-- users (sentinel id=0 already exists from V009)
-- ============================================================================

INSERT IGNORE INTO users (id, public_id, email, provider, provider_id, username_epithet, username_suffix, role, created_at, updated_at)
VALUES
    (1, UNHEX('11111111111111111111111111111111'), 'seed-user-1@test.local', 'google', 'seed_google_001', 'RATIONAL', 'A1B2C', 'NORMAL', NOW(), NOW()),
    (2, UNHEX('22222222222222222222222222222222'), 'seed-user-2@test.local', 'google', 'seed_google_002', 'BRILLIANT', 'D3E4F', 'ADMIN', NOW(), NOW()),
    (3, UNHEX('33333333333333333333333333333333'), 'seed-user-3@test.local', 'google', 'seed_google_003', 'NAIVE', 'G5H6I', 'NORMAL', NOW(), NOW());

-- ============================================================================
-- user_settings
-- ============================================================================

INSERT IGNORE INTO user_settings (user_id, sync_enabled, notify_comments, notify_recommendations, notify_new_publications)
VALUES
    (1, TRUE, TRUE, TRUE, FALSE),
    (2, TRUE, TRUE, TRUE, TRUE),
    (3, NULL, TRUE, TRUE, FALSE);

-- ============================================================================
-- planner (write-once core)
-- ============================================================================

INSERT IGNORE INTO planner (id, user_id, planner_type, created_at)
VALUES
    (UNHEX('AAAA0001000000000000000000000001'), 1, 'MIRROR_DUNGEON',    NOW(6)),
    (UNHEX('AAAA0002000000000000000000000002'), 2, 'REFRACTED_RAILWAY', NOW(6)),
    (UNHEX('AAAA0003000000000000000000000003'), 1, 'MIRROR_DUNGEON',    NOW(6)),
    (UNHEX('AAAA0004000000000000000000000004'), 3, 'MIRROR_DUNGEON',    NOW(6));

-- ============================================================================
-- planner_content — exercises JSON keywords, JSON content, VARCHAR columns
-- ============================================================================

-- Planner 1: MD planner with status effects + attack types keywords
INSERT IGNORE INTO planner_content (planner_id, title, status, category, selected_keywords, content, content_schema_version, game_content_version, sync_version, row_lock_version, last_modified_at)
VALUES (
    UNHEX('AAAA0001000000000000000000000001'),
    'Seed MD Planner - Status Effects', 'saved', '5F',
    '["Combustion","Laceration","Vibration","Burst","Sinking","Breath","Charge","Slash","Penetrate","Hit"]',
    JSON_OBJECT(
        'equipment', JSON_OBJECT(
            'slot1', JSON_OBJECT(
                'identity', JSON_OBJECT('id', '10101'),
                'egos', JSON_ARRAY(JSON_OBJECT('id', '20101'), JSON_OBJECT('id', '20102'))
            ),
            'slot2', JSON_OBJECT(
                'identity', JSON_OBJECT('id', '10201'),
                'egos', JSON_ARRAY(JSON_OBJECT('id', '20201'))
            )
        ),
        'selectedGiftIds', JSON_ARRAY('30001', '30002'),
        'observationGiftIds', JSON_ARRAY('30003'),
        'comprehensiveGiftIds', JSON_ARRAY('30004'),
        'floorSelections', JSON_ARRAY(
            JSON_OBJECT('giftIds', JSON_ARRAY('30005', '30006'), 'themePackId', '1001'),
            JSON_OBJECT('giftIds', JSON_ARRAY('30007'), 'themePackId', NULL)
        )
    ),
    1, 6, 1, 0, NOW(6)
);

-- Planner 2: RR planner with affinity keywords
INSERT IGNORE INTO planner_content (planner_id, title, status, category, selected_keywords, content, content_schema_version, game_content_version, sync_version, row_lock_version, last_modified_at)
VALUES (
    UNHEX('AAAA0002000000000000000000000002'),
    'Seed RR Planner - Affinities', 'saved', '10F',
    '["CRIMSON","SCARLET","AMBER","SHAMROCK","AZURE","INDIGO","VIOLET"]',
    JSON_OBJECT(
        'equipment', JSON_OBJECT(
            'slot1', JSON_OBJECT(
                'identity', JSON_OBJECT('id', '10301'),
                'egos', JSON_ARRAY(JSON_OBJECT('id', '20301'))
            )
        ),
        'selectedGiftIds', JSON_ARRAY('30010'),
        'observationGiftIds', JSON_ARRAY(),
        'comprehensiveGiftIds', JSON_ARRAY(),
        'floorSelections', JSON_ARRAY()
    ),
    1, 5, 1, 0, NOW(6)
);

-- Planner 3: MD planner with synergy keywords (includes EmergencyChargeForceField)
INSERT IGNORE INTO planner_content (planner_id, title, status, category, selected_keywords, content, content_schema_version, game_content_version, sync_version, row_lock_version, last_modified_at)
VALUES (
    UNHEX('AAAA0003000000000000000000000003'),
    'Seed MD Planner - Synergy Keywords', 'saved', '15F',
    '["Assemble","KnowledgeExplored","AaCePcBt","SwordPlayOfTheHomeland","EchoOfMansion","TimeSuspend","EmergencyChargeForceField","BloodDinner","BlackCloud","RetaliationBook","HeishouSynergy","Bullet","BlessingOfIndexPrescriptAlly","Inspire","9828","SojiRyoshuEntangle","DawnTeam"]',
    JSON_OBJECT(
        'equipment', JSON_OBJECT(
            'slot1', JSON_OBJECT(
                'identity', JSON_OBJECT('id', '10401'),
                'egos', JSON_ARRAY(JSON_OBJECT('id', '20401'))
            )
        ),
        'selectedGiftIds', JSON_ARRAY('30020'),
        'observationGiftIds', JSON_ARRAY(),
        'comprehensiveGiftIds', JSON_ARRAY(),
        'floorSelections', JSON_ARRAY()
    ),
    1, 6, 1, 0, NOW(6)
);

-- Planner 4: draft planner with remaining keywords (9154)
INSERT IGNORE INTO planner_content (planner_id, title, status, category, selected_keywords, content, content_schema_version, game_content_version, sync_version, row_lock_version, last_modified_at)
VALUES (
    UNHEX('AAAA0004000000000000000000000004'),
    'Seed Draft Planner', 'draft', '5F',
    '["9154"]',
    JSON_OBJECT('equipment', JSON_OBJECT(), 'selectedGiftIds', JSON_ARRAY(), 'observationGiftIds', JSON_ARRAY(), 'comprehensiveGiftIds', JSON_ARRAY(), 'floorSelections', JSON_ARRAY()),
    1, 6, 1, 0, NOW(6)
);

-- ============================================================================
-- planner_publication
-- ============================================================================

INSERT IGNORE INTO planner_publication (planner_id, published, first_published_at, owner_notifications_enabled)
VALUES
    (UNHEX('AAAA0001000000000000000000000001'), TRUE,  NOW(6), TRUE),
    (UNHEX('AAAA0002000000000000000000000002'), TRUE,  NOW(6), TRUE),
    (UNHEX('AAAA0003000000000000000000000003'), TRUE,  NOW(6), TRUE),
    (UNHEX('AAAA0004000000000000000000000004'), FALSE, NULL,   TRUE);

-- ============================================================================
-- planner_moderation
-- ============================================================================

INSERT IGNORE INTO planner_moderation (planner_id, taken_down_at, hidden_from_recommended)
VALUES
    (UNHEX('AAAA0001000000000000000000000001'), NULL, FALSE),
    (UNHEX('AAAA0002000000000000000000000002'), NULL, FALSE),
    (UNHEX('AAAA0003000000000000000000000003'), NULL, FALSE),
    (UNHEX('AAAA0004000000000000000000000004'), NULL, FALSE);

-- ============================================================================
-- planner_stats — comment_count matches the live planner_comments rows below
-- ============================================================================

INSERT IGNORE INTO planner_stats (planner_id, view_count, upvotes, comment_count)
VALUES
    (UNHEX('AAAA0001000000000000000000000001'), 42, 5,  2),
    (UNHEX('AAAA0002000000000000000000000002'), 18, 3,  0),
    (UNHEX('AAAA0003000000000000000000000003'), 87, 12, 0),
    (UNHEX('AAAA0004000000000000000000000004'), 0,  0,  0);

-- ============================================================================
-- planner_catalog — visible rows only; planner 3 is recommended (12 >= 10)
-- ============================================================================

INSERT IGNORE INTO planner_catalog (planner_id, planner_type, category, title, selected_keywords, first_published_at, recommended)
VALUES
    (UNHEX('AAAA0001000000000000000000000001'), 'MIRROR_DUNGEON', '5F', 'Seed MD Planner - Status Effects',
     '["Combustion","Laceration","Vibration","Burst","Sinking","Breath","Charge","Slash","Penetrate","Hit"]', NOW(6), FALSE),
    (UNHEX('AAAA0002000000000000000000000002'), 'REFRACTED_RAILWAY', '10F', 'Seed RR Planner - Affinities',
     '["CRIMSON","SCARLET","AMBER","SHAMROCK","AZURE","INDIGO","VIOLET"]', NOW(6), FALSE),
    (UNHEX('AAAA0003000000000000000000000003'), 'MIRROR_DUNGEON', '15F', 'Seed MD Planner - Synergy Keywords',
     '["Assemble","KnowledgeExplored","AaCePcBt","SwordPlayOfTheHomeland","EchoOfMansion","TimeSuspend","EmergencyChargeForceField","BloodDinner","BlackCloud","RetaliationBook","HeishouSynergy","Bullet","BlessingOfIndexPrescriptAlly","Inspire","9828","SojiRyoshuEntangle","DawnTeam"]', NOW(6), TRUE);

-- ============================================================================
-- planner_entity_filter — exercises entity_type ENUM (integer entity ids)
-- ============================================================================

INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
VALUES
    ('IDENTITY',  10101, UNHEX('AAAA0001000000000000000000000001')),
    ('IDENTITY',  10201, UNHEX('AAAA0001000000000000000000000001')),
    ('EGO',       20101, UNHEX('AAAA0001000000000000000000000001')),
    ('EGO',       20102, UNHEX('AAAA0001000000000000000000000001')),
    ('EGO_GIFT',  30001, UNHEX('AAAA0001000000000000000000000001')),
    ('EGO_GIFT',  30002, UNHEX('AAAA0001000000000000000000000001')),
    ('THEME_PACK', 1001, UNHEX('AAAA0001000000000000000000000001'));

-- ============================================================================
-- planner_keyword_filter
-- ============================================================================

INSERT IGNORE INTO planner_keyword_filter (keyword, planner_id)
VALUES
    ('Combustion', UNHEX('AAAA0001000000000000000000000001')),
    ('Sinking',    UNHEX('AAAA0001000000000000000000000001')),
    ('CRIMSON',    UNHEX('AAAA0002000000000000000000000002')),
    ('DawnTeam',   UNHEX('AAAA0003000000000000000000000003'));

-- ============================================================================
-- planner_votes — exercises vote_type ENUM
-- ============================================================================

INSERT IGNORE INTO planner_votes (user_id, planner_id, vote_type, created_at, version)
VALUES
    (2, UNHEX('AAAA0001000000000000000000000001'), 'UP', NOW(), 0),
    (3, UNHEX('AAAA0001000000000000000000000001'), 'UP', NOW(), 0),
    (1, UNHEX('AAAA0002000000000000000000000002'), 'UP', NOW(), 0);

-- ============================================================================
-- planner_bookmarks
-- ============================================================================

INSERT IGNORE INTO planner_bookmarks (user_id, planner_id, created_at)
VALUES
    (1, UNHEX('AAAA0002000000000000000000000002'), NOW()),
    (2, UNHEX('AAAA0003000000000000000000000003'), NOW());

-- ============================================================================
-- planner_views
-- ============================================================================

INSERT IGNORE INTO planner_views (planner_id, viewer_hash, view_date, created_at)
VALUES
    (UNHEX('AAAA0001000000000000000000000001'), SHA2('viewer-001', 256), CURDATE(), NOW()),
    (UNHEX('AAAA0002000000000000000000000002'), SHA2('viewer-002', 256), CURDATE(), NOW());

-- ============================================================================
-- planner_comments
-- ============================================================================

INSERT IGNORE INTO planner_comments (id, public_id, planner_id, user_id, parent_comment_id, content, depth, upvote_count, created_at)
VALUES
    (1, UNHEX('CC000001000000000000000000000001'), UNHEX('AAAA0001000000000000000000000001'), 2, NULL, 'Great planner build.', 0, 2, NOW()),
    (2, UNHEX('CC000002000000000000000000000002'), UNHEX('AAAA0001000000000000000000000001'), 3, 1, 'Agreed, very effective.', 1, 0, NOW());

-- ============================================================================
-- planner_comment_votes
-- ============================================================================

INSERT IGNORE INTO planner_comment_votes (comment_id, user_id, vote_type, created_at, version)
VALUES
    (1, 1, 'UP', NOW(), 0),
    (1, 3, 'UP', NOW(), 0);

-- ============================================================================
-- notifications
-- ============================================================================

INSERT IGNORE INTO notifications (id, public_id, user_id, content_id, notification_type, `read`, created_at, planner_id, planner_title, comment_snippet, comment_public_id)
VALUES
    (1, UNHEX('BB000001000000000000000000000001'), 1, 'comment:1', 'NEW_COMMENT', FALSE, NOW(),
     UNHEX('AAAA0001000000000000000000000001'), 'Seed MD Planner - Status Effects', 'Great planner build.', UNHEX('CC000001000000000000000000000001')),
    (2, UNHEX('BB000002000000000000000000000002'), 1, 'milestone:upvotes:5', 'UPVOTE_MILESTONE', TRUE, NOW(),
     UNHEX('AAAA0001000000000000000000000001'), 'Seed MD Planner - Status Effects', NULL, NULL);

-- ============================================================================
-- planner_subscriptions
-- ============================================================================

INSERT IGNORE INTO planner_subscriptions (user_id, planner_id, enabled, created_at)
VALUES
    (1, UNHEX('AAAA0001000000000000000000000001'), TRUE, NOW()),
    (2, UNHEX('AAAA0001000000000000000000000001'), TRUE, NOW());

-- ============================================================================
-- planner_reports
-- ============================================================================

INSERT IGNORE INTO planner_reports (user_id, planner_id, created_at)
VALUES (3, UNHEX('AAAA0002000000000000000000000002'), NOW());

-- ============================================================================
-- planner_comment_reports
-- ============================================================================

INSERT IGNORE INTO planner_comment_reports (comment_id, reporter_id, reason, created_at)
VALUES (2, 1, 'SPAM', NOW());

-- ============================================================================
-- moderation_actions
-- ============================================================================

INSERT IGNORE INTO moderation_actions (action_type, actor_id, target_uuid, target_type, reason, created_at)
VALUES ('TIMEOUT', 2, '33333333-3333-3333-3333-333333333333', 'USER', 'Test moderation action', NOW(6));
