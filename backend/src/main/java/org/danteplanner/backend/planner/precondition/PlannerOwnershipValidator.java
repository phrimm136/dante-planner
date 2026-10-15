package org.danteplanner.backend.planner.precondition;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class PlannerOwnershipValidator {

    public void requireOwner(Planner planner, Long userId) {
        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(planner.getId());
        }
    }

    public void requireLiveOwner(UUID id, Long userId, Optional<PlannerOwnershipRow> ownership) {
        PlannerOwnershipRow owner = ownership.filter(row -> row.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(id));
        if (!owner.getUserId().equals(userId)) {
            throw new PlannerForbiddenException(id);
        }
    }

    public void requireLiveOwnedBy(UUID id, Long userId, Optional<PlannerOwnershipRow> ownership) {
        if (ownership.filter(row -> row.getUserId().equals(userId) && row.getDeletedAt() == null).isEmpty()) {
            throw new PlannerNotFoundException(id);
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
