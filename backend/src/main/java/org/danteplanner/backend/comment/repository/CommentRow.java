package org.danteplanner.backend.comment.repository;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record CommentRow(
        Long id,
        UUID publicId,
        UUID plannerId,
        Long userId,
        @Nullable Long parentCommentId,
        int depth,
        Instant createdAt,
        String content,
        @Nullable Instant editedAt,
        @Nullable Instant deletedAt,
        boolean authorNotificationsEnabled,
        int upvoteCount
) {

    static final String SELECT = """
            SELECT new org.danteplanner.backend.comment.repository.CommentRow(
                c.id, c.publicId, c.plannerId, c.userId, c.parentCommentId, c.depth, c.createdAt,
                t.content, t.editedAt, t.deletedAt, t.authorNotificationsEnabled, s.upvoteCount)
            FROM PlannerComment c
            JOIN PlannerCommentContent t ON t.commentId = c.id
            JOIN PlannerCommentStats s ON s.commentId = c.id
            """;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
