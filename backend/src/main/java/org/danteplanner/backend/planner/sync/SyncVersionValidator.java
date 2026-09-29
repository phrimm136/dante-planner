package org.danteplanner.backend.planner.sync;

import org.springframework.stereotype.Component;

import org.danteplanner.backend.planner.exception.PlannerConflictException;

@Component
public class SyncVersionValidator {

    public WriteArbitration arbitrate(
            boolean force,
            Long requestedVersion,
            long actualVersion,
            PlannerChange change) {
        if (force) {
            return WriteArbitration.WRITE;
        }
        if (requestedVersion == null) {
            throw new PlannerConflictException(null, actualVersion);
        }
        if (actualVersion == requestedVersion.longValue()) {
            return WriteArbitration.WRITE;
        }
        if (change.isNone()) {
            return WriteArbitration.ACK_NO_OP;
        }
        throw new PlannerConflictException(requestedVersion, actualVersion);
    }
}
