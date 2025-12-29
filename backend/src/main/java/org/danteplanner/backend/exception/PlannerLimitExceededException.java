package org.danteplanner.backend.exception;

public class PlannerLimitExceededException extends RuntimeException {

    public PlannerLimitExceededException(long currentCount, int maxLimit) {
        super("Planner limit exceeded: current count is " + currentCount + ", max allowed is " + maxLimit);
    }
}
