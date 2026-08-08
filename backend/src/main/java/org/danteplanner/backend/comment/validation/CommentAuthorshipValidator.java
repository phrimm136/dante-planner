package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.springframework.stereotype.Component;

/**
 * The rules that reserve an action on a comment for the account that wrote it.
 *
 * <p>Each action names itself in the refusal, so the caller learns which authority it lacked
 * rather than that it lacked one.</p>
 */
@Component
public class CommentAuthorshipValidator {

    /**
     * Require the caller to be the comment's author before rewriting its body.
     *
     * @param comment the loaded comment
     * @param userId  the acting account
     * @throws CommentForbiddenException if the caller did not write the comment
     */
    public void requireAuthorToEdit(PlannerComment comment, Long userId) {
        requireAuthor(comment, userId, "Only the author can edit this comment");
    }

    /**
     * Require the caller to be the comment's author before withdrawing it.
     *
     * @param comment the loaded comment
     * @param userId  the acting account
     * @throws CommentForbiddenException if the caller did not write the comment
     */
    public void requireAuthorToDelete(PlannerComment comment, Long userId) {
        requireAuthor(comment, userId, "Only the author can delete this comment");
    }

    /**
     * Require the caller to be the comment's author before changing what the thread notifies them
     * about.
     *
     * @param comment the loaded comment
     * @param userId  the acting account
     * @throws CommentForbiddenException if the caller did not write the comment
     */
    public void requireAuthorToToggleNotifications(PlannerComment comment, Long userId) {
        requireAuthor(comment, userId, "Only the author can toggle notification settings");
    }

    private void requireAuthor(PlannerComment comment, Long userId, String refusal) {
        if (!comment.getUserId().equals(userId)) {
            throw new CommentForbiddenException(comment.getId(), refusal);
        }
    }
}
