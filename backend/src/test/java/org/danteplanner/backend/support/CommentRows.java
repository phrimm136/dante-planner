package org.danteplanner.backend.support;

import java.time.Instant;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import org.jspecify.annotations.Nullable;
import org.mockito.stubbing.Answer;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.CommentRow;

public final class CommentRows {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private CommentRows() {
    }

    public static CommentRow live(Long id, UUID publicId, UUID plannerId, Long authorId, String content,
            @Nullable Long parentId, int depth) {
        return new CommentRow(id, publicId, plannerId, authorId, parentId, depth, CREATED_AT, content, null, null,
                true, 0);
    }

    public static CommentRow deleted(CommentRow row, Instant deletedAt) {
        return new CommentRow(row.id(), row.publicId(), row.plannerId(), row.userId(), row.parentCommentId(),
                row.depth(), row.createdAt(), "", row.editedAt(), deletedAt, row.authorNotificationsEnabled(),
                row.upvoteCount());
    }

    public static CommentRow muted(CommentRow row) {
        return new CommentRow(row.id(), row.publicId(), row.plannerId(), row.userId(), row.parentCommentId(),
                row.depth(), row.createdAt(), row.content(), row.editedAt(), row.deletedAt(), false,
                row.upvoteCount());
    }

    public static Answer<PlannerComment> insertedAs(Long id) {
        return invocation -> {
            PlannerComment inserted = invocation.getArgument(0);
            ReflectionTestUtils.setField(inserted, "id", id);
            ReflectionTestUtils.setField(inserted, "publicId", UUID.randomUUID());
            ReflectionTestUtils.setField(inserted, "createdAt", CREATED_AT);
            return inserted;
        };
    }
}
