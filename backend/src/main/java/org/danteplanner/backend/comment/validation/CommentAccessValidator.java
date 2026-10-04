package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommentAccessValidator {

    public void requireThreadVisible(PlannerThread planner, Long currentUserId) {
        if (currentUserId != null && planner.isOwnedBy(currentUserId)) {
            return;
        }
        if (!planner.published() || planner.ownerDeactivated()) {
            throw new PlannerNotFoundException(planner.id());
        }
    }

    public void requireParentInPlanner(PlannerComment parent, UUID plannerId) {
        if (!parent.getPlannerId().equals(plannerId)) {
            throw new CommentForbiddenException("Parent comment belongs to a different planner");
        }
    }
}
