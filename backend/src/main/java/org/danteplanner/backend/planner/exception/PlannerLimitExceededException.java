package org.danteplanner.backend.planner.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

public class PlannerLimitExceededException extends DomainException {

    public PlannerLimitExceededException(long currentCount, int maxLimit) {
        super(ErrorKind.CONFLICT, "PLANNER_LIMIT_EXCEEDED", "Planner limit exceeded: current count is " + currentCount + ", max allowed is " + maxLimit);
    }
}
