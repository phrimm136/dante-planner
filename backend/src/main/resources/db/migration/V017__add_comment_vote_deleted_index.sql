-- V017: Add index on deleted_at for comment votes
-- Required for findUpvotedCommentIds query which filters by deletedAt IS NULL
-- Without this index, query degrades to table scan as vote history grows

CREATE INDEX idx_comment_vote_deleted ON planner_comment_votes(deleted_at);
