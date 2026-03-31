-- Migration smoke-test seed data
-- Purpose: Populate every table so future migrations that ALTER/MODIFY columns
--          are tested against non-empty tables (catches strict-mode failures).
--
-- CI flow:
--   1. Run EXISTING migrations (merged to main)
--   2. Run this seed (simulates production data)
--   3. Run NEW migrations (from the PR under test)
--   If step 3 fails, the new migration is unsafe against populated tables.
--
-- Maintenance:
--   - SET/ENUM values must match the schema after all MERGED migrations
--   - When a migration changes SET/ENUM/column types, update this seed in the SAME PR
--   - Every SET member and ENUM value must appear in at least one row
--
-- Schema version: V041 (last migration reflected in seed values)
--
-- Coverage:
--   - selected_keywords: all 31 SET members across 4 planners
--   - planner_type: MIRROR_DUNGEON, REFRACTED_RAILWAY
--   - status: draft, saved
--   - vote_type: UP
--   - entity_type: IDENTITY, EGO, EGO_GIFT, THEME_PACK
--   - content: JSON with equipment, gifts, floorSelections structure
--   - All 14 tables populated with FK-valid data

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
    (2, TRUE, TRUE, TRUE, TRUE);

-- ============================================================================
-- planners — exercises SET, ENUM, JSON, VARCHAR columns
-- ============================================================================

-- Planner 1: MD planner with status effects + attack types keywords
INSERT IGNORE INTO planners (id, user_id, title, category, status, content, schema_version, sync_version, content_version, planner_type, published, upvotes, selected_keywords, view_count, first_published_at)
VALUES (
    UNHEX('AAAA0001000000000000000000000001'), 1,
    'Seed MD Planner - Status Effects', '5F', 'saved',
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
            JSON_OBJECT('giftIds', JSON_ARRAY('30005', '30006'), 'themePackId', 'TP001'),
            JSON_OBJECT('giftIds', JSON_ARRAY('30007'), 'themePackId', NULL)
        )
    ),
    1, 1, 6, 'MIRROR_DUNGEON', TRUE, 5,
    'Combustion,Laceration,Vibration,Burst,Sinking,Breath,Charge,Slash,Penetrate,Hit',
    42, NOW(6)
);

-- Planner 2: RR planner with affinity keywords
INSERT IGNORE INTO planners (id, user_id, title, category, status, content, schema_version, sync_version, content_version, planner_type, published, upvotes, selected_keywords, view_count, first_published_at)
VALUES (
    UNHEX('AAAA0002000000000000000000000002'), 2,
    'Seed RR Planner - Affinities', '10F', 'saved',
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
    1, 1, 5, 'REFRACTED_RAILWAY', TRUE, 3,
    'CRIMSON,SCARLET,AMBER,SHAMROCK,AZURE,INDIGO,VIOLET',
    18, NOW(6)
);

-- Planner 3: MD planner with synergy keywords (includes EmergencyChargeForceField)
INSERT IGNORE INTO planners (id, user_id, title, category, status, content, schema_version, sync_version, content_version, planner_type, published, upvotes, selected_keywords, view_count, first_published_at)
VALUES (
    UNHEX('AAAA0003000000000000000000000003'), 1,
    'Seed MD Planner - Synergy Keywords', '15F', 'saved',
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
    1, 1, 6, 'MIRROR_DUNGEON', TRUE, 12,
    'Assemble,KnowledgeExplored,AaCePcBt,SwordPlayOfTheHomeland,EchoOfMansion,TimeSuspend,EmergencyChargeForceField,BloodDinner,BlackCloud,RetaliationBook,HeishouSynergy,Bullet,BlessingOfIndexPrescriptAlly,Inspire',
    87, NOW(6)
);

-- Planner 4: draft planner with remaining keywords (9154, Bullet)
INSERT IGNORE INTO planners (id, user_id, title, category, status, content, schema_version, sync_version, content_version, planner_type, published, upvotes, selected_keywords, view_count)
VALUES (
    UNHEX('AAAA0004000000000000000000000004'), 3,
    'Seed Draft Planner', '5F', 'draft',
    JSON_OBJECT('equipment', JSON_OBJECT(), 'selectedGiftIds', JSON_ARRAY(), 'observationGiftIds', JSON_ARRAY(), 'comprehensiveGiftIds', JSON_ARRAY(), 'floorSelections', JSON_ARRAY()),
    1, 1, 6, 'MIRROR_DUNGEON', FALSE, 0,
    '9154',
    0
);

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

-- ============================================================================
-- planner_content_index — exercises entity_type ENUM
-- Only inserted if table exists (created in V039)
-- ============================================================================

SET @pci_exists = (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'planner_content_index');

SET @pci_sql = IF(@pci_exists > 0,
    CONCAT(
        'INSERT IGNORE INTO planner_content_index (planner_id, entity_type, entity_id) VALUES ',
        '(UNHEX(''AAAA0001000000000000000000000001''), ''IDENTITY'', ''10101''),',
        '(UNHEX(''AAAA0001000000000000000000000001''), ''IDENTITY'', ''10201''),',
        '(UNHEX(''AAAA0001000000000000000000000001''), ''EGO'', ''20101''),',
        '(UNHEX(''AAAA0001000000000000000000000001''), ''EGO'', ''20102''),',
        '(UNHEX(''AAAA0001000000000000000000000001''), ''EGO_GIFT'', ''30001''),',
        '(UNHEX(''AAAA0001000000000000000000000001''), ''EGO_GIFT'', ''30002''),',
        '(UNHEX(''AAAA0001000000000000000000000001''), ''THEME_PACK'', ''TP001'')'
    ),
    'SELECT 1'
);

PREPARE pci_stmt FROM @pci_sql;
EXECUTE pci_stmt;
DEALLOCATE PREPARE pci_stmt;
