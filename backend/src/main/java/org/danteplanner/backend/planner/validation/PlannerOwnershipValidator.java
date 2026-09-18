package org.danteplanner.backend.planner.validation;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class PlannerOwnershipValidator {

    public void requireOwner(Planner planner, Long userId) {
        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(planner.getId());
        }
    }

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
