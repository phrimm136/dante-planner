CREATE TABLE IF NOT EXISTS planner_comment_content (
    comment_id                   BIGINT    NOT NULL,
    content                      TEXT      NOT NULL,
    edited_at                    TIMESTAMP NULL,
    deleted_at                   TIMESTAMP NULL,
    author_notifications_enabled BOOLEAN   NOT NULL DEFAULT TRUE,
    PRIMARY KEY (comment_id),
    CONSTRAINT fk_comment_content_comment FOREIGN KEY (comment_id)
        REFERENCES planner_comments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS planner_comment_stats (
    comment_id   BIGINT NOT NULL,
    upvote_count INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (comment_id),
    CONSTRAINT fk_comment_stats_comment FOREIGN KEY (comment_id)
        REFERENCES planner_comments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO planner_comment_content
    (comment_id, content, edited_at, deleted_at, author_notifications_enabled)
SELECT id, content, edited_at, deleted_at, author_notifications_enabled
FROM planner_comments;

INSERT IGNORE INTO planner_comment_stats (comment_id, upvote_count)
SELECT id, upvote_count
FROM planner_comments;
