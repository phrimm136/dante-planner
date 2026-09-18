package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.springframework.stereotype.Component;

@Component
public class PlannerLimitValidator {

    public void requireRoomFor(long currentCount, int requestedCount, int limit) {
        if (currentCount + requestedCount > limit) {
            throw new PlannerLimitExceededException(currentCount, limit);
        }
    }
}
