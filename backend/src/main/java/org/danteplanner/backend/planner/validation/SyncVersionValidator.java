package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.springframework.stereotype.Component;

/**
 * The optimistic-locking rule a client write is held to.
 */
@Component
public class SyncVersionValidator {

    /**
     * Require the version the client wrote against to still be the stored one.
     *
     * <p>A request carrying no version states no expectation and is let through, as is one that
     * asks to overwrite whatever it finds.</p>
     *
     * @param force            whether the caller asked to overwrite regardless
     * @param requestedVersion the version the client believes is stored, null when it names none
     * @param actualVersion    the version currently stored
     * @throws PlannerConflictException if the stored version moved under the client
     */
    public void requireSyncVersionMatch(boolean force, Long requestedVersion, long actualVersion) {
        if (force || requestedVersion == null) {
            return;
        }
        if (actualVersion != requestedVersion.longValue()) {
            throw new PlannerConflictException(requestedVersion, actualVersion);
        }
    }
}
