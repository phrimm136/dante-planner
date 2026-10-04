package org.danteplanner.backend.planner.api;

import java.util.UUID;

public interface PlannerCommentCounts {

    void incrementCommentCount(UUID plannerId);

    void decrementCommentCount(UUID plannerId);
}
