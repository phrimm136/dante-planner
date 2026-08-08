package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.springframework.stereotype.Component;

/**
 * How many planners one account may hold.
 */
@Component
public class PlannerLimitValidator {

    /**
     * Require the account to have room for the planners it is about to add.
     *
     * @param currentCount   planners the account already holds
     * @param requestedCount planners the request would add
     * @param limit          the ceiling the account is held to
     * @throws PlannerLimitExceededException if the request would take the account past the ceiling
     */
    public void requireRoomFor(long currentCount, int requestedCount, int limit) {
        if (currentCount + requestedCount > limit) {
            throw new PlannerLimitExceededException(currentCount, limit);
        }
    }
}
