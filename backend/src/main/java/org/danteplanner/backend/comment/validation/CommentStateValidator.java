package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.comment.validation.CommentAccessValidator.ParentInPlanner;
import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.stereotype.Component;

@Component
public class CommentStateValidator {

    @Parsed
    public record LiveComment(PlannerComment comment) {

        private static LiveComment of(PlannerComment comment, String refusal) {
            if (comment.isDeleted()) {
                throw new CommentForbiddenException(refusal);
            }
            return new LiveComment(comment);
        }
    }

    @Parsed
    public record ReplyableParent(PlannerComment comment) {

        private static ReplyableParent of(PlannerComment parent) {
            if (parent.isDeleted() && parent.getDepth() == 0) {
                throw new CommentForbiddenException("Cannot reply to deleted top-level comment");
            }
            return new ReplyableParent(parent);
        }
    }

    public LiveComment requireEditable(PlannerComment comment) {
        return LiveComment.of(comment, "Cannot edit a deleted comment");
    }

    public LiveComment requireVotable(PlannerComment comment) {
        return LiveComment.of(comment, "Cannot vote on a deleted comment");
    }

    public LiveComment requireReportable(PlannerComment comment) {
        return LiveComment.of(comment, "Cannot report a deleted comment");
    }

    public ReplyableParent requireReplyable(PlannerComment parent) {
        return ReplyableParent.of(parent);
    }

    public ReplyableParent requireReplyable(ParentInPlanner parent) {
        return ReplyableParent.of(parent.comment());
    }
}
