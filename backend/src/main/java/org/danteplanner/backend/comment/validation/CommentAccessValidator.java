package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.shared.security.Viewer;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.planner.api.PlannerMiss;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommentAccessValidator {

    @Parsed
    public record VisibleThread(PlannerThread planner) {

        private static VisibleThread of(PlannerThread planner, Viewer viewer) {
            if (!viewer.is(planner.ownerId()) && (!planner.published() || planner.ownerDeactivated())) {
                throw new PlannerNotFoundException(new PlannerMiss.Hidden(planner.id(), planner.ownerId()));
            }
            return new VisibleThread(planner);
        }
    }

    @Parsed
    public record ParentInPlanner(PlannerComment comment) {

        private static ParentInPlanner of(PlannerComment parent, UUID plannerId) {
            if (!parent.getPlannerId().equals(plannerId)) {
                throw new CommentForbiddenException("Parent comment belongs to a different planner");
            }
            return new ParentInPlanner(parent);
        }
    }

    public VisibleThread requireThreadVisible(PlannerThread planner, Viewer viewer) {
        return VisibleThread.of(planner, viewer);
    }

    public ParentInPlanner requireParentInPlanner(PlannerComment parent, UUID plannerId) {
        return ParentInPlanner.of(parent, plannerId);
    }
}
