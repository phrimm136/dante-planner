package org.danteplanner.backend.comment.dto;

import java.time.Instant;
import org.danteplanner.backend.shared.security.Viewer;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.comment.repository.CommentRow;
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

    public enum Upvote {
        CAST,
        NONE
    }

    public static CommentTreeNode forBroadcast(
            CommentRow comment,
            Parent parent,
            Author author
    ) {
        return node(comment, parent, author, false, Upvote.NONE, List.of());
    }

    public static CommentTreeNode fromEntity(
            CommentRow comment,
            Parent parent,
            Author author,
            Viewer viewer,
            Upvote upvote,
            List<CommentTreeNode> replies
    ) {
        boolean isAuthor = viewer.is(comment.userId());
        return node(comment, parent, author, isAuthor, upvote, replies);
    }

    private static CommentTreeNode node(
            CommentRow comment,
            Parent parent,
            Author author,
            boolean isAuthor,
            Upvote upvote,
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

        String createdAt = comment.createdAt().toString();

        Instant editedAt = comment.editedAt();
        String updatedAt = editedAt != null ? editedAt.toString() : null;

        String content = comment.isDeleted() || authorHidden
                ? CommentConstants.DELETED_CONTENT
                : comment.content();

        return new CommentTreeNode(
                comment.publicId(),
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
                comment.upvoteCount(),
                upvote == Upvote.CAST,
                isAuthor && comment.authorNotificationsEnabled(),
                replies
        );
    }
}
