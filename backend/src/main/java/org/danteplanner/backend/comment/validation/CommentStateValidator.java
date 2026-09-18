package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class CommentStateValidator {

    public void requireEditable(PlannerComment comment) {
        requireNotDeleted(comment, "Cannot edit a deleted comment");
    }

    public void requireVotable(PlannerComment comment) {
        requireNotDeleted(comment, "Cannot vote on a deleted comment");
    }

    public void requireReportable(PlannerComment comment) {
        requireNotDeleted(comment, "Cannot report a deleted comment");
    }

    public void requireReplyable(PlannerComment parent) {
        if (parent.isDeleted() && parent.getDepth() == 0) {
            throw new CommentForbiddenException(parent.getId(), "Cannot reply to deleted top-level comment");
        }
    }

    private void requireNotDeleted(PlannerComment comment, String refusal) {
        if (comment.isDeleted()) {
            throw new CommentForbiddenException(comment.getId(), refusal);
        }
    }
}
