package org.danteplanner.backend.comment.validation;

import com.google.errorprone.annotations.CheckReturnValue;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.comment.validation.CommentStateValidator.LiveComment;
import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.shared.failure.Result;

@CheckReturnValue
public final class CommentAuthorshipValidator {

    private CommentAuthorshipValidator() {
    }

    @Parsed
    public record AuthoredComment(CommentRow comment) {

        public static Result<AuthoredComment, CommentRefusal> of(
                CommentRow comment, Long userId, CommentRefusal refusal) {
            return comment.userId().equals(userId)
                    ? Result.ok(new AuthoredComment(comment))
                    : Result.err(refusal);
        }
    }

    public static Result<AuthoredComment, CommentRefusal> editableBy(CommentRow comment, Long userId) {
        return LiveComment.of(comment, CommentRefusal.EDIT_DELETED)
                .flatMap(live -> AuthoredComment.of(live.comment(), userId, CommentRefusal.EDIT_NOT_AUTHOR));
    }
}
