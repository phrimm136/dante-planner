package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.comment.validation.CommentStateValidator.LiveComment;
import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.stereotype.Component;

@Component
public class CommentAuthorshipValidator {

    @Parsed
    public record AuthoredComment(PlannerComment comment) {

        private static AuthoredComment of(PlannerComment comment, Long userId, String refusal) {
            if (!comment.getUserId().equals(userId)) {
                throw new CommentForbiddenException(refusal);
            }
            return new AuthoredComment(comment);
        }
    }

    public AuthoredComment requireAuthorToEdit(LiveComment live, Long userId) {
        return AuthoredComment.of(live.comment(), userId, "Only the author can edit this comment");
    }

    public AuthoredComment requireAuthorToDelete(PlannerComment comment, Long userId) {
        return AuthoredComment.of(comment, userId, "Only the author can delete this comment");
    }

    public AuthoredComment requireAuthorToToggleNotifications(PlannerComment comment, Long userId) {
        return AuthoredComment.of(comment, userId, "Only the author can toggle notification settings");
    }
}
