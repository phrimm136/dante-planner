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
     * <p>Only an explicit request to overwrite whatever it finds is let through. A request naming
     * no version is a conflict, not an exemption: a stored row exists, so a write that states no
     * expectation about it cannot be shown to have been made against what is there.</p>
     *
     * @param force            whether the caller asked to overwrite regardless
     * @param requestedVersion the version the client believes is stored, null when it names none
     * @param actualVersion    the version currently stored
     * @throws PlannerConflictException if the stored version moved under the client, or the client
     *                                  named none
     */
    public void requireSyncVersionMatch(boolean force, Long requestedVersion, long actualVersion) {
        if (force) {
            return;
        }
        if (requestedVersion == null || actualVersion != requestedVersion.longValue()) {
            throw new PlannerConflictException(requestedVersion, actualVersion);
        }
    }
}
