package org.danteplanner.backend.planner.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.exception.PlannerConflictException;

@Component
@RequiredArgsConstructor
public class SyncVersionValidator {

    private final EffectiveNoOpPredicate effectiveNoOp;

    public WriteArbitration arbitrate(
            boolean force,
            Long requestedVersion,
            long actualVersion,
            PlannerContent stored,
            CarriedWrite carried) {
        if (force) {
            return WriteArbitration.WRITE;
        }
        if (requestedVersion == null) {
            throw new PlannerConflictException(null, actualVersion);
        }
        if (actualVersion == requestedVersion.longValue()) {
            return WriteArbitration.WRITE;
        }
        if (effectiveNoOp.isEffectiveNoOp(stored, carried)) {
            return WriteArbitration.ACK_NO_OP;
        }
        throw new PlannerConflictException(requestedVersion, actualVersion);
    }
}
