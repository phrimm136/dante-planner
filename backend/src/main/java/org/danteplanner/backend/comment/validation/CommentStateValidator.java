package org.danteplanner.backend.comment.validation;

import com.google.errorprone.annotations.CheckReturnValue;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.comment.validation.CommentAccessValidator.ParentInPlanner;
import org.danteplanner.backend.planner.api.VoteAlreadyExistsException;
import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.shared.failure.Result;

import java.util.UUID;

@CheckReturnValue
public final class CommentStateValidator {

    private CommentStateValidator() {
    }

    @Parsed
    public record LiveComment(CommentRow comment) {

        public static Result<LiveComment, CommentRefusal> of(CommentRow comment, CommentRefusal refusal) {
            return comment.isDeleted() ? Result.err(refusal) : Result.ok(new LiveComment(comment));
        }
    }

    @Parsed
    public record ReplyableParent(CommentRow comment) {

        public static Result<ReplyableParent, CommentRefusal> of(ParentInPlanner placed) {
            CommentRow parent = placed.comment();
            return parent.isDeleted() && parent.depth() == 0
                    ? Result.err(CommentRefusal.REPLY_DELETED)
                    : Result.ok(new ReplyableParent(parent));
        }
    }

    public static Result<ReplyableParent, CommentRefusal> replyableIn(CommentRow parent, UUID plannerId) {
        return ParentInPlanner.of(parent, plannerId).flatMap(ReplyableParent::of);
    }

    public static CommentNotFoundException gone(UUID commentPublicId) {
        return new CommentNotFoundException(commentPublicId);
    }

    public static VoteAlreadyExistsException alreadyVoted(UUID plannerId, Long userId) {
        return new VoteAlreadyExistsException(plannerId, userId);
    }
}
