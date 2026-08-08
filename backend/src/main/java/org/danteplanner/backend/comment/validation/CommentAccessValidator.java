package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.planner.entity.Planner;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Which planner a comment thread may be read through, and which planner a reply may attach to.
 */
@Component
public class CommentAccessValidator {

    /**
     * Require the planner's comment thread to be readable by the caller. A published planner's
     * thread is public; an unpublished planner's belongs to its owner alone.
     *
     * @param planner       the loaded planner
     * @param currentUserId the reading account, null when unauthenticated
     * @throws CommentForbiddenException if the planner is unpublished and the caller does not own it
     */
    public void requireThreadVisible(Planner planner, Long currentUserId) {
        if (!planner.isPublished() && (currentUserId == null || !planner.isOwnedBy(currentUserId))) {
            throw new CommentForbiddenException("Cannot view comments on unpublished planner");
        }
    }

    /**
     * Require the comment being replied to to sit on the planner the reply is addressed to.
     *
     * @param parent    the comment being replied to
     * @param plannerId the planner the reply is addressed to
     * @throws CommentForbiddenException if the parent belongs to another planner
     */
    public void requireParentInPlanner(PlannerComment parent, UUID plannerId) {
        if (!parent.getPlannerId().equals(plannerId)) {
            throw new CommentForbiddenException("Parent comment belongs to a different planner");
        }
    }
}
