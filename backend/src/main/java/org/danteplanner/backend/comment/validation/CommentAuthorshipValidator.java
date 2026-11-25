package org.danteplanner.backend.comment.validation;

import com.google.errorprone.annotations.CheckReturnValue;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.validation.CommentStateValidator.LiveComment;
import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.shared.failure.Result;

@CheckReturnValue
public final class CommentAuthorshipValidator {

    private CommentAuthorshipValidator() {
    }

    @Parsed
    public record AuthoredComment(PlannerComment comment) {

        public static Result<AuthoredComment, CommentRefusal> of(
                PlannerComment comment, Long userId, CommentRefusal refusal) {
            return comment.getUserId().equals(userId)
                    ? Result.ok(new AuthoredComment(comment))
                    : Result.err(refusal);
        }
    }

    public static Result<AuthoredComment, CommentRefusal> editableBy(PlannerComment comment, Long userId) {
        return LiveComment.of(comment, CommentRefusal.EDIT_DELETED)
                .flatMap(live -> AuthoredComment.of(live.comment(), userId, CommentRefusal.EDIT_NOT_AUTHOR));
    }
}
