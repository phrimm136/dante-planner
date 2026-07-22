-- Planner god-table decomposition, step 1 of 4: create the aggregate and
-- projection tables. planner (core) is write-once; planner_content is the owner
-- hot-path row and deliberately carries NO secondary index and NO inbound FK, so
-- a concurrent same-row save can only serialize on its row lock. No FKs among
-- the aggregate/projection tables — deletion is an app-side sweep; FKs point only
-- outward to users or at the write-once core.
--
-- The core user FK is created as fk_planner_user_core because the legacy
-- planners table still holds the name fk_planner_user until the V052 drop
-- (MySQL foreign key names are schema-global); V052 renames it.
--
-- Every statement is rerunnable: MySQL DDL is non-transactional, so a failed
-- run must be safe to execute again.

CREATE TABLE IF NOT EXISTS planner (
    id            BINARY(16)  NOT NULL,
    user_id       BIGINT      NOT NULL,
    planner_type  ENUM('MIRROR_DUNGEON','REFRACTED_RAILWAY') NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_planner_user_core FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS planner_content (
    planner_id             BINARY(16)   NOT NULL,
    title                  VARCHAR(255) NOT NULL DEFAULT 'Untitled',
    status                 VARCHAR(16)  NOT NULL,
    category               VARCHAR(50)  NOT NULL,
    selected_keywords      JSON         NULL,
    content                JSON         NOT NULL,
    content_schema_version INT          NOT NULL DEFAULT 2,
    game_content_version   INT          NOT NULL,
    sync_version           BIGINT       NOT NULL DEFAULT 1,
    row_lock_version       BIGINT       NOT NULL DEFAULT 0,
    device_id              BINARY(16)   NULL,
    last_modified_at       DATETIME(6)  NOT NULL,
    deleted_at             DATETIME(6)  NULL,
    PRIMARY KEY (planner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS planner_publication (
    planner_id                  BINARY(16)  NOT NULL,
    published                   BOOLEAN     NOT NULL DEFAULT FALSE,
    first_published_at          DATETIME(6) NULL,
    owner_notifications_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (planner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS planner_moderation (
    planner_id              BINARY(16)  NOT NULL,
    taken_down_at           DATETIME(6) NULL,
    hidden_from_recommended BOOLEAN     NOT NULL DEFAULT FALSE,
    hidden_by_moderator_id  BIGINT      NULL,
    hidden_reason           TEXT        NULL,
    hidden_at               DATETIME(6) NULL,
    PRIMARY KEY (planner_id),
    CONSTRAINT fk_pmod_moderator FOREIGN KEY (hidden_by_moderator_id)
        REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS planner_catalog (
    planner_id         BINARY(16)   NOT NULL,
    planner_type       ENUM('MIRROR_DUNGEON','REFRACTED_RAILWAY') NOT NULL,
    category           VARCHAR(50)  NOT NULL,
    title              VARCHAR(255) NOT NULL,
    selected_keywords  JSON         NULL,
    first_published_at DATETIME(6)  NOT NULL,
    recommended        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (planner_id),
    INDEX idx_catalog_recent      (first_published_at DESC, planner_type, category),
    INDEX idx_catalog_recommended (recommended, first_published_at DESC, planner_type, category),
    FULLTEXT INDEX ftx_title      (title) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS planner_entity_filter (
    entity_type ENUM('IDENTITY','EGO','EGO_GIFT','THEME_PACK') NOT NULL,
    entity_id   INT UNSIGNED NOT NULL,
    planner_id  BINARY(16)   NOT NULL,
    PRIMARY KEY (entity_type, entity_id, planner_id),
    INDEX idx_pef_planner (planner_id),
    CONSTRAINT fk_pef_planner FOREIGN KEY (planner_id)
        REFERENCES planner(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS planner_keyword_filter (
    keyword    VARCHAR(64) NOT NULL,
    planner_id BINARY(16)  NOT NULL,
    PRIMARY KEY (keyword, planner_id),
    INDEX idx_pkf_planner (planner_id),
    CONSTRAINT fk_pkf_planner FOREIGN KEY (planner_id)
        REFERENCES planner(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Extend planner_stats: comment_count joins the counters; the recommended
-- notification CAS stamp moves off the planners row. Guarded because MySQL has
-- no ADD COLUMN IF NOT EXISTS.

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'planner_stats' AND column_name = 'comment_count');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE planner_stats ADD COLUMN comment_count INT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'planner_stats' AND column_name = 'recommended_notified_at');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE planner_stats ADD COLUMN recommended_notified_at DATETIME(6) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
