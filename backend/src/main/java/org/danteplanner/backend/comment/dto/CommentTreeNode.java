package org.danteplanner.backend.comment.dto;

import java.time.Instant;
import org.danteplanner.backend.shared.security.Viewer;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.danteplanner.backend.user.api.UserSummary;

import java.util.List;
import java.util.UUID;

public record CommentTreeNode(
    UUID id,
    @Nullable UUID parentCommentId,
    String content,
    String authorEpithet,
    String authorSuffix,
    boolean isAuthor,
    String createdAt,
    @Nullable String updatedAt,
    boolean isDeleted,
    int upvoteCount,
    boolean hasUpvoted,
    boolean authorNotificationsEnabled,
    List<CommentTreeNode> replies
) {

    public sealed interface Author {

        record Known(UserSummary summary) implements Author {
        }

        record Missing() implements Author {
        }
    }

    public sealed interface Parent {

        record Thread() implements Parent {
        }

        record Comment(UUID publicId) implements Parent {
        }
    }

    public static CommentTreeNode forBroadcast(
            PlannerComment comment,
            Parent parent,
            Author author
    ) {
        return node(comment, parent, author, false, false, List.of());
    }

    public static CommentTreeNode fromEntity(
            PlannerComment comment,
            Parent parent,
            Author author,
            Viewer viewer,
            boolean hasUpvoted,
            List<CommentTreeNode> replies
    ) {
        boolean isAuthor = viewer.is(comment.getUserId());
        return node(comment, parent, author, isAuthor, hasUpvoted, replies);
    }

    private static CommentTreeNode node(
            PlannerComment comment,
            Parent parent,
            Author author,
            boolean isAuthor,
            boolean hasUpvoted,
            List<CommentTreeNode> replies
    ) {
        boolean authorHidden = true;
        String authorEpithet = CommentConstants.DELETED_CONTENT;
        String authorSuffix = CommentConstants.DELETED_CONTENT;
        switch (author) {
            case Author.Known(UserSummary visible) when !visible.deleted() -> {
                authorHidden = false;
                if (visible.usernameEpithet() != null && visible.usernameSuffix() != null) {
                    authorEpithet = visible.usernameEpithet();
                    authorSuffix = visible.usernameSuffix();
                }
            }
            case Author.Known deleted -> { }
            case Author.Missing missing -> { }
        }

        String createdAt = comment.getCreatedAt().toString();

        Instant editedAt = comment.getEditedAt();
        String updatedAt = editedAt != null ? editedAt.toString() : null;

        String content = comment.isDeleted() || authorHidden
                ? CommentConstants.DELETED_CONTENT
                : comment.getContent();

        return new CommentTreeNode(
                comment.getPublicId(),
                switch (parent) {
                    case Parent.Thread thread -> null;
                    case Parent.Comment(UUID publicId) -> publicId;
                },
                content,
                authorEpithet,
                authorSuffix,
                isAuthor,
                createdAt,
                updatedAt,
                comment.isDeleted(),
                comment.getUpvoteCount(),
                hasUpvoted,
                isAuthor && comment.isAuthorNotificationsEnabled(),
                replies
        );
    }
}
