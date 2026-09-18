package org.danteplanner.backend.comment.dto;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.danteplanner.backend.user.entity.User;

import java.util.List;
import java.util.UUID;

public record CommentTreeNode(
    UUID id,
    UUID parentCommentId,
    String content,
    String authorEpithet,
    String authorSuffix,
    boolean isAuthor,
    String createdAt,
    String updatedAt,
    boolean isDeleted,
    int upvoteCount,
    boolean hasUpvoted,
    boolean authorNotificationsEnabled,
    List<CommentTreeNode> replies
) {

    public static CommentTreeNode forBroadcast(
            PlannerComment comment,
            UUID parentPublicId,
            User author
    ) {
        return fromEntity(comment, parentPublicId, author, null, false, List.of());
    }

    public static CommentTreeNode fromEntity(
            PlannerComment comment,
            UUID parentPublicId,
            User author,
            Long currentUserId,
            boolean hasUpvoted,
            List<CommentTreeNode> replies
    ) {
        String authorEpithet = CommentConstants.DELETED_CONTENT;
        String authorSuffix = CommentConstants.DELETED_CONTENT;
        if (author != null && !author.isDeleted()
                && author.getUsernameEpithet() != null && author.getUsernameSuffix() != null) {
            authorEpithet = author.getUsernameEpithet();
            authorSuffix = author.getUsernameSuffix();
        }

        boolean isAuthor = currentUserId != null && comment.getUserId().equals(currentUserId);

        String createdAt = comment.getCreatedAt().toString();

        String updatedAt = comment.getEditedAt() != null ? comment.getEditedAt().toString() : null;

        String content = comment.isDeleted() ? CommentConstants.DELETED_CONTENT : comment.getContent();

        return new CommentTreeNode(
                comment.getPublicId(),
                parentPublicId,
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
