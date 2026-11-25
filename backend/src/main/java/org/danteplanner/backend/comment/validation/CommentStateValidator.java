package org.danteplanner.backend.comment.validation;

import com.google.errorprone.annotations.CheckReturnValue;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.validation.CommentAccessValidator.ParentInPlanner;
import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.shared.failure.Result;

import java.util.UUID;

@CheckReturnValue
public final class CommentStateValidator {

    private CommentStateValidator() {
    }

    @Parsed
    public record LiveComment(PlannerComment comment) {

        public static Result<LiveComment, CommentRefusal> of(PlannerComment comment, CommentRefusal refusal) {
            return comment.isDeleted() ? Result.err(refusal) : Result.ok(new LiveComment(comment));
        }
    }

    @Parsed
    public record ReplyableParent(PlannerComment comment) {

        public static Result<ReplyableParent, CommentRefusal> of(ParentInPlanner placed) {
            PlannerComment parent = placed.comment();
            return parent.isDeleted() && parent.getDepth() == 0
                    ? Result.err(CommentRefusal.REPLY_DELETED)
                    : Result.ok(new ReplyableParent(parent));
        }
    }

    public static Result<ReplyableParent, CommentRefusal> replyableIn(PlannerComment parent, UUID plannerId) {
        return ParentInPlanner.of(parent, plannerId).flatMap(ReplyableParent::of);
    }
}
