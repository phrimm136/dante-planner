package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class CommentAuthorshipValidator {

    public void requireAuthorToEdit(PlannerComment comment, Long userId) {
        requireAuthor(comment, userId, "Only the author can edit this comment");
    }

    public void requireAuthorToDelete(PlannerComment comment, Long userId) {
        requireAuthor(comment, userId, "Only the author can delete this comment");
    }

    public void requireAuthorToToggleNotifications(PlannerComment comment, Long userId) {
        requireAuthor(comment, userId, "Only the author can toggle notification settings");
    }

    private void requireAuthor(PlannerComment comment, Long userId, String refusal) {
        if (!comment.getUserId().equals(userId)) {
            throw new CommentForbiddenException(comment.getId(), refusal);
        }
    }
}
