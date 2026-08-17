package org.danteplanner.backend.planner.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import lombok.Getter;

/**
 * A write that names a stored version the planner has already moved past.
 *
 * <p>The response carries the current version alongside the triple every business error carries, so
 * {@code GlobalExceptionHandler} answers it through its own handler rather than the shared one.</p>
 */
@Getter
public class PlannerConflictException extends DomainException {

    private final Long actualVersion;

    public PlannerConflictException(Long expectedVersion, Long actualVersion) {
        super(ErrorKind.CONFLICT, "SYNC_CONFLICT",
                "Sync version conflict: expected " + expectedVersion + " but found " + actualVersion);
        this.actualVersion = actualVersion;
    }
}
