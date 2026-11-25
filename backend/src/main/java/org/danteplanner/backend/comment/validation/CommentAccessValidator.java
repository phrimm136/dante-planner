package org.danteplanner.backend.comment.validation;

import com.google.errorprone.annotations.CheckReturnValue;
import org.danteplanner.backend.shared.security.Viewer;

import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.planner.api.PlannerMiss;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.shared.failure.Result;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@CheckReturnValue
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
    public record ParentInPlanner(CommentRow comment) {

        public static Result<ParentInPlanner, CommentRefusal> of(CommentRow parent, UUID plannerId) {
            return parent.plannerId().equals(plannerId)
                    ? Result.ok(new ParentInPlanner(parent))
                    : Result.err(CommentRefusal.OTHER_PLANNER);
        }
    }

    public VisibleThread requireThreadVisible(PlannerThread planner, Viewer viewer) {
        return VisibleThread.of(planner, viewer);
    }
}
