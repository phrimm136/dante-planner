-- V015: Add planner comments and comment votes tables
-- Supports threaded comments with upvote-only voting

-- Comments table
CREATE TABLE planner_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    planner_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    parent_comment_id BIGINT,
    content TEXT NOT NULL,
    depth TINYINT NOT NULL DEFAULT 0,
    upvote_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,

    CONSTRAINT fk_comment_planner FOREIGN KEY (planner_id) REFERENCES planners(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_comment_id) REFERENCES planner_comments(id) ON DELETE SET NULL,

    INDEX idx_comment_planner (planner_id, deleted_at),
    INDEX idx_comment_user (user_id),
    INDEX idx_comment_parent (parent_comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comment votes table (composite primary key)
CREATE TABLE planner_comment_votes (
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vote_type VARCHAR(10) NOT NULL DEFAULT 'UP',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,

    PRIMARY KEY (comment_id, user_id),
    CONSTRAINT fk_comment_vote_comment FOREIGN KEY (comment_id) REFERENCES planner_comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_vote_user FOREIGN KEY (user_id) REFERENCES users(id),

    INDEX idx_comment_vote_comment (comment_id),
    INDEX idx_comment_vote_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
