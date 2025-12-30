-- Add publishing and voting columns to planners table
ALTER TABLE planners
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN upvotes INT NOT NULL DEFAULT 0,
    ADD COLUMN downvotes INT NOT NULL DEFAULT 0,
    ADD COLUMN selected_keywords SET(
        'Combustion', 'Laceration', 'Vibration', 'Burst', 'Sinking', 'Breath', 'Charge',
        'Slash', 'Penetrate', 'Hit',
        'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET'
    ) DEFAULT NULL;

-- Add indexes for public planner queries
ALTER TABLE planners
    ADD INDEX idx_published (published),
    ADD INDEX idx_published_category (published, category),
    ADD INDEX idx_published_votes (published, upvotes, downvotes);

-- Planner votes table for tracking user votes
CREATE TABLE planner_votes (
    user_id BIGINT NOT NULL,
    planner_id CHAR(36) NOT NULL,
    vote_type ENUM('UP', 'DOWN') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, planner_id),
    CONSTRAINT fk_vote_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_vote_planner FOREIGN KEY (planner_id) REFERENCES planners(id) ON DELETE CASCADE,
    INDEX idx_planner_votes (planner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
