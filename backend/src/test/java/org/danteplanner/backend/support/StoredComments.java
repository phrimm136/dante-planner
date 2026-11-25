package org.danteplanner.backend.support;

import org.springframework.jdbc.core.JdbcOperations;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;

public final class StoredComments {

    private StoredComments() {
    }

    public static PlannerComment save(PlannerCommentRepository repository, JdbcOperations jdbc,
            PlannerComment comment) {
        PlannerComment saved = repository.saveAndFlush(comment);
        split(jdbc, saved.getId());
        return saved;
    }

    public static void split(JdbcOperations jdbc, Long commentId) {
        jdbc.update("INSERT INTO planner_comment_content (comment_id, content, edited_at, deleted_at, "
                + "author_notifications_enabled) SELECT id, content, edited_at, deleted_at, "
                + "author_notifications_enabled FROM planner_comments WHERE id = ?", commentId);
        jdbc.update("INSERT INTO planner_comment_stats (comment_id, upvote_count) "
                + "SELECT id, upvote_count FROM planner_comments WHERE id = ?", commentId);
    }
}
