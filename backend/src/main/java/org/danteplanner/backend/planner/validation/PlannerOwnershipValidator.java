package org.danteplanner.backend.planner.validation;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Who may act on a planner, and what an id already claimed by someone else means.
 */
@Component
@Slf4j
public class PlannerOwnershipValidator {

    /**
     * Require the caller to own the planner.
     *
     * @param planner the loaded planner
     * @param userId  the acting account
     * @throws PlannerForbiddenException if the caller does not own the planner
     */
    public void requireOwner(Planner planner, Long userId) {
        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(planner.getId());
        }
    }

    /**
     * Require a client-chosen planner id to be free for the account to create under.
     *
     * <p>The account's own soft-deleted planner reads as gone, so recreating under its id is
     * refused as a missing planner; another account's live planner is an id collision the caller
     * may not resolve.</p>
     *
     * @param id       the client-chosen planner id
     * @param userId   the account creating the planner
     * @param existing the ownership row already stored under the id
     * @throws PlannerNotFoundException  if the account soft-deleted its own planner under this id
     * @throws PlannerForbiddenException if another account holds a live planner under this id
     */
    public void requireIdAvailable(UUID id, Long userId, PlannerOwnershipRow existing) {
        if (existing.getUserId().equals(userId)) {
            log.warn("Planner {} is soft-deleted for user {} - cannot recreate", id, userId);
            throw new PlannerNotFoundException(id);
        }
        if (existing.getDeletedAt() == null) {
            log.warn("Planner {} exists but belongs to another user (ID collision)", id);
            throw new PlannerForbiddenException(id);
        }
    }
}
