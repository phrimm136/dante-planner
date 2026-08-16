package org.danteplanner.backend.planner.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.exception.PlannerConflictException;

/**
 * The optimistic-locking rule a client write is held to.
 */
@Component
@RequiredArgsConstructor
public class SyncVersionValidator {

    private final EffectiveNoOpPredicate effectiveNoOp;

    /**
     * Decide whether a write may be applied, may be acknowledged without one, or conflicts.
     *
     * <p>Only an explicit request to overwrite whatever it finds is let through unexamined, and a
     * write naming the stored version is applied without any comparison running. A request naming
     * no version is a conflict, not an exemption: a stored row exists, so a write that states no
     * expectation about it cannot be shown to have been made against what is there.</p>
     *
     * <p>A write naming a version the row has moved past is refused only when it would actually
     * move a field. One that would leave every persisted field as it stands is acknowledged with
     * the stored version and writes nothing, so a retried save or a resend of the document the
     * server already holds does not surface a divergence no author made.</p>
     *
     * @param force            whether the caller asked to overwrite regardless
     * @param requestedVersion the version the client believes is stored, null when it names none
     * @param actualVersion    the version currently stored
     * @param stored           the content row as it stands
     * @param carried          the values the request would apply to that row
     * @return whether to apply the write or to acknowledge it without one
     * @throws PlannerConflictException if the stored version moved under a write that would change
     *                                  the row, or the client named no version
     */
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
