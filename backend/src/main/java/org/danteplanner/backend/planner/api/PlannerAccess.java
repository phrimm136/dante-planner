package org.danteplanner.backend.planner.api;

import java.util.UUID;

public interface PlannerAccess {

    PlannerThread requireExisting(UUID plannerId);

    void checkPublished(UUID plannerId);

    void checkNotRestricted(Long userId);

    void checkNotBanned(Long userId);
}
