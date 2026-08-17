package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.springframework.stereotype.Component;

/**
 * What a withdrawn comment no longer permits.
 *
 * <p>A withdrawn comment keeps its row so the thread beneath it survives, which is why every
 * action on one has to be refused explicitly rather than by the row's absence.</p>
 */
@Component
public class CommentStateValidator {

    /**
     * Require the comment to still be present before its body is rewritten.
     *
     * @param comment the loaded comment
     * @throws CommentForbiddenException if the comment was withdrawn
     */
    public void requireEditable(PlannerComment comment) {
        requireNotDeleted(comment, "Cannot edit a deleted comment");
    }

    /**
     * Require the comment to still be present before a vote lands on it.
     *
     * @param comment the loaded comment
     * @throws CommentForbiddenException if the comment was withdrawn
     */
    public void requireVotable(PlannerComment comment) {
        requireNotDeleted(comment, "Cannot vote on a deleted comment");
    }

    /**
     * Require the comment to still be present before a report is filed against it.
     *
     * @param comment the loaded comment
     * @throws CommentForbiddenException if the comment was withdrawn
     */
    public void requireReportable(PlannerComment comment) {
        requireNotDeleted(comment, "Cannot report a deleted comment");
    }

    /**
     * Require a reply's parent to still carry a thread.
     *
     * <p>A withdrawn top-level comment ends its thread; a withdrawn reply does not, because its own
     * children keep the thread continuous.</p>
     *
     * @param parent the comment being replied to
     * @throws CommentForbiddenException if the parent is a withdrawn top-level comment
     */
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
