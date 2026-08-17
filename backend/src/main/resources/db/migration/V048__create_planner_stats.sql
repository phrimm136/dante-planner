CREATE TABLE planner_stats (
    planner_id BINARY(16) PRIMARY KEY,
    view_count INT NOT NULL DEFAULT 0,
    upvotes INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_planner_stats_planner FOREIGN KEY (planner_id) REFERENCES planners (id) ON DELETE CASCADE
);

INSERT INTO planner_stats (planner_id, view_count, upvotes)
SELECT id, view_count, upvotes
FROM planners;
