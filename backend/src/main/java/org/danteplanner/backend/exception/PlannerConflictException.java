package org.danteplanner.backend.exception;

import lombok.Getter;

@Getter
public class PlannerConflictException extends RuntimeException {

    private final Long actualVersion;

    public PlannerConflictException(Long expectedVersion, Long actualVersion) {
        super("Sync version conflict: expected " + expectedVersion + " but found " + actualVersion);
        this.actualVersion = actualVersion;
    }
}
