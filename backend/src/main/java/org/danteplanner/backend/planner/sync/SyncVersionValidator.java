package org.danteplanner.backend.planner.sync;

import org.springframework.stereotype.Component;

import org.danteplanner.backend.planner.exception.PlannerConflictException;

@Component
public class SyncVersionValidator {

    public WriteArbitration arbitrate(
            boolean force,
            PlannerConflictException.Requested requested,
            long actualVersion,
            PlannerChange change) {
        if (force) {
            return WriteArbitration.WRITE;
        }
        return switch (requested) {
            case PlannerConflictException.Requested.None none ->
                    throw new PlannerConflictException(none, actualVersion);
            case PlannerConflictException.Requested.Version named when named.version() == actualVersion ->
                    WriteArbitration.WRITE;
            case PlannerConflictException.Requested.Version stale when change.isNone() -> WriteArbitration.ACK_NO_OP;
            case PlannerConflictException.Requested.Version stale ->
                    throw new PlannerConflictException(stale, actualVersion);
        };
    }
}
