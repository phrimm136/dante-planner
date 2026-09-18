package org.danteplanner.backend.planner.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

@Getter
public class PlannerConflictException extends DomainException {

    private static final String ERROR_CODE = "SYNC_CONFLICT";
    private static final String SERVER_VERSION_PROPERTY = "serverVersion";

    private final Long actualVersion;

    public PlannerConflictException(Long expectedVersion, Long actualVersion) {
        super(ErrorKind.CONFLICT, ERROR_CODE,
                "Sync version conflict: expected " + expectedVersion + " but found " + actualVersion);
        getBody().setProperty(SERVER_VERSION_PROPERTY, actualVersion);
        this.actualVersion = actualVersion;
    }
}
