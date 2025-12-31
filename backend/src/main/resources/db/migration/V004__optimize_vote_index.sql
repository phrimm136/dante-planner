-- Optimize vote index for primary lookup pattern
-- The original index (planner_id, deleted_at) doesn't cover the user_id lookup
-- This new index supports: findByUserIdAndPlannerIdAndDeletedAtIsNull()
DROP INDEX idx_planner_active_votes ON planner_votes;
CREATE INDEX idx_planner_active_votes ON planner_votes (user_id, planner_id, deleted_at);
