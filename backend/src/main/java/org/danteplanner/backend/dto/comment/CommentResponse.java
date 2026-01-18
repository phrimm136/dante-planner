package org.danteplanner.backend.dto.comment;

import org.danteplanner.backend.entity.PlannerComment;
import org.danteplanner.backend.entity.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for comments.
 * Contains comment data with author information and user vote status.
 */
public record CommentResponse(
    Long id,
    UUID plannerId,
    Long parentCommentId,
    int depth,
    String content,
    AuthorInfo author,
    Instant createdAt,
    Instant editedAt,
    boolean isDeleted,
    int upvoteCount,
    boolean hasUpvoted,
    boolean authorNotificationsEnabled
) {
    /**
     * Author information for a comment.
     * Fields are null for sentinel user (id=0, representing deleted users).
     */
    public record AuthorInfo(
        Long id,
        String usernameKeyword,
        String usernameSuffix
    ) {
        /**
         * Sentinel user ID representing deleted users.
         */
        public static final Long SENTINEL_USER_ID = 0L;

        /**
         * Create AuthorInfo from a User entity.
         * Returns null username fields for sentinel user.
         */
        public static AuthorInfo fromUser(User user) {
            if (user.getId().equals(SENTINEL_USER_ID)) {
                return new AuthorInfo(SENTINEL_USER_ID, null, null);
            }
            return new AuthorInfo(
                user.getId(),
                user.getUsernameKeyword(),
                user.getUsernameSuffix()
            );
        }
    }

    /**
     * Create a CommentResponse from a PlannerComment entity and User.
     *
     * @param comment    the comment entity
     * @param author     the author user entity (null for sentinel user)
     * @param hasUpvoted whether the current user has upvoted this comment
     * @return the comment response DTO
     */
    public static CommentResponse fromEntity(PlannerComment comment, User author, boolean hasUpvoted) {
        // Handle sentinel user (deleted users) - author will be null from batch load
        AuthorInfo authorInfo = author != null
            ? AuthorInfo.fromUser(author)
            : new AuthorInfo(AuthorInfo.SENTINEL_USER_ID, null, null);

        return new CommentResponse(
            comment.getId(),
            comment.getPlannerId(),
            comment.getParentCommentId(),
            comment.getDepth(),
            comment.isDeleted() ? null : comment.getContent(),
            authorInfo,
            comment.getCreatedAt(),
            comment.getEditedAt(),
            comment.isDeleted(),
            comment.getUpvoteCount(),
            hasUpvoted,
            comment.getAuthorNotificationsEnabled()
        );
    }
}
