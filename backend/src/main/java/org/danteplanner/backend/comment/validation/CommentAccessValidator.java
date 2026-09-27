package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommentAccessValidator {

    public void requireThreadVisible(Planner planner, Long currentUserId) {
        if (currentUserId != null && planner.isOwnedBy(currentUserId)) {
            return;
        }
        if (!planner.isPublished()) {
            throw new CommentForbiddenException("Cannot view comments on unpublished planner");
        }
        if (planner.isOwnerDeactivated()) {
            throw new PlannerNotFoundException(planner.getId());
        }
    }

    public void requireParentInPlanner(PlannerComment parent, UUID plannerId) {
        if (!parent.getPlannerId().equals(plannerId)) {
            throw new CommentForbiddenException("Parent comment belongs to a different planner");
        }
    }
}
