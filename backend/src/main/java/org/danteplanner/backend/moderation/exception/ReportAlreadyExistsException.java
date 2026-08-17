package org.danteplanner.backend.moderation.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when a user attempts to report a planner they've already reported.
 * Reports are immutable - users can only report once per planner.
 */
@Getter
public class ReportAlreadyExistsException extends DomainException {

    private final UUID plannerId;
    private final Long userId;

    public ReportAlreadyExistsException(UUID plannerId, Long userId) {
        super(ErrorKind.CONFLICT, "REPORT_ALREADY_EXISTS", String.format("User %d has already reported planner %s. Reports cannot be submitted twice.", userId, plannerId));
        this.plannerId = plannerId;
        this.userId = userId;
    }
}
