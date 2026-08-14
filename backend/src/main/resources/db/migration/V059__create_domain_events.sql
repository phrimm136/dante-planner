CREATE TABLE IF NOT EXISTS domain_events (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    event_type    VARCHAR(32) NOT NULL,
    aggregate_id  BINARY(16)  NOT NULL,
    payload       JSON        NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    dispatched_at DATETIME(6) NULL,
    attempts      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_domain_events_undispatched (dispatched_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
