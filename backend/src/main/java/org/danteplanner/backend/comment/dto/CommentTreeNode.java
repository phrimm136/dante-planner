package org.danteplanner.backend.comment.dto;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.user.entity.User;

import java.util.List;
import java.util.UUID;

/**
 * Hierarchical comment tree node DTO.
 * Contains only fields required for display, with nested replies.
 * No internal IDs exposed - uses UUID for comment, no user ID at all.
 * Tree is built server-side with isAuthor computed.
 */
public record CommentTreeNode(
    UUID id,
    String content,
    String authorEpithet,
    String authorSuffix,
    boolean isAuthor,
    String createdAt,
    String updatedAt,
    boolean isUpdated,
    boolean isDeleted,
    int upvoteCount,
    boolean hasUpvoted,
    boolean authorNotificationsEnabled,
    List<CommentTreeNode> replies
) {
    private static final String DELETED_VALUE = "";

    /**
     * Create a tree node from entity with computed fields.
     *
     * @param comment       the comment entity
     * @param author        the author user entity (null for deleted users)
     * @param currentUserId the current user's ID (null if unauthenticated)
     * @param hasUpvoted    whether current user has upvoted
     * @param replies       nested child comments
     */
    public static CommentTreeNode fromEntity(
            PlannerComment comment,
            User author,
            Long currentUserId,
            boolean hasUpvoted,
            List<CommentTreeNode> replies
    ) {
        // Empty for an absent, sentinel, or deactivated author. A soft-deleted account is loaded
        // like any other, so it is excluded here rather than by the query that fetched it.
        String authorEpithet = DELETED_VALUE;
        String authorSuffix = DELETED_VALUE;
        if (author != null && !author.isDeleted()
                && author.getUsernameEpithet() != null && author.getUsernameSuffix() != null) {
            authorEpithet = author.getUsernameEpithet();
            authorSuffix = author.getUsernameSuffix();
        }

        // Compute isAuthor on backend
        boolean isAuthor = currentUserId != null && comment.getUserId().equals(currentUserId);

        // createdAt is always the creation time
        String createdAt = comment.getCreatedAt().toString();

        // updatedAt is editedAt if edited, otherwise null
        boolean isUpdated = comment.getEditedAt() != null;
        String updatedAt = isUpdated ? comment.getEditedAt().toString() : null;

        // Empty string for deleted content (not null)
        String content = comment.isDeleted() ? DELETED_VALUE : comment.getContent();

        return new CommentTreeNode(
                comment.getPublicId(),
                content,
                authorEpithet,
                authorSuffix,
                isAuthor,
                createdAt,
                updatedAt,
                isUpdated,
                comment.isDeleted(),
                comment.getUpvoteCount(),
                hasUpvoted,
                comment.getAuthorNotificationsEnabled(),
                replies
        );
    }
}
