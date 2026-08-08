package org.danteplanner.backend.comment.dto;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.danteplanner.backend.user.entity.User;

import java.util.List;
import java.util.UUID;

/**
 * Hierarchical comment tree node DTO.
 * Contains only fields required for display, with nested replies.
 * No internal IDs exposed - uses UUID for comment, no user ID at all.
 * Tree is built server-side with isAuthor computed.
 *
 * <p>{@code updatedAt} is null unless the comment was edited, and
 * {@code authorNotificationsEnabled} is null for every viewer except the comment's author.</p>
 */
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
    Boolean authorNotificationsEnabled,
    List<CommentTreeNode> replies
) {

    /**
     * Create a tree node for a broadcast, which reaches every viewer rather than one.
     *
     * <p>Carries no viewer state: a broadcast has no current user for authorship or upvote to be
     * relative to, and a comment is broadcast at creation, when it has no replies.</p>
     *
     * @param comment        the comment entity
     * @param parentPublicId the public UUID of the comment this one replies to, null at top level
     * @param author         the author user entity (null for deleted users)
     * @return the tree node
     */
    public static CommentTreeNode forBroadcast(
            PlannerComment comment,
            UUID parentPublicId,
            User author
    ) {
        return fromEntity(comment, parentPublicId, author, null, false, List.of());
    }

    /**
     * Create a tree node from entity with computed fields.
     *
     * @param comment         the comment entity
     * @param parentPublicId  the public UUID of the comment this one replies to, null at top level
     * @param author          the author user entity (null for deleted users)
     * @param currentUserId   the current user's ID (null if unauthenticated)
     * @param hasUpvoted      whether current user has upvoted
     * @param replies         nested child comments
     */
    public static CommentTreeNode fromEntity(
            PlannerComment comment,
            UUID parentPublicId,
            User author,
            Long currentUserId,
            boolean hasUpvoted,
            List<CommentTreeNode> replies
    ) {
        // Empty for an absent, sentinel, or deactivated author. A soft-deleted account is loaded
        // like any other, so it is excluded here rather than by the query that fetched it.
        String authorEpithet = CommentConstants.DELETED_CONTENT;
        String authorSuffix = CommentConstants.DELETED_CONTENT;
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
        String updatedAt = comment.getEditedAt() != null ? comment.getEditedAt().toString() : null;

        // Empty string for deleted content (not null)
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
                isAuthor ? comment.getAuthorNotificationsEnabled() : null,
                replies
        );
    }
}
