package org.danteplanner.backend.planner.api;

import java.util.UUID;

public record PlannerThread(UUID id, Long ownerId, boolean published, boolean ownerDeactivated) {

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }
}
