package org.danteplanner.backend.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when a user attempts to report a planner they've already reported.
 * Reports are immutable - users can only report once per planner.
 */
@Getter
public class ReportAlreadyExistsException extends RuntimeException {

    private final UUID plannerId;
    private final Long userId;

    public ReportAlreadyExistsException(UUID plannerId, Long userId) {
        super(String.format("User %d has already reported planner %s. Reports cannot be submitted twice.", userId, plannerId));
        this.plannerId = plannerId;
        this.userId = userId;
    }
}
