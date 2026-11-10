package org.danteplanner.backend.planner.api;

import java.util.UUID;

import org.danteplanner.backend.shared.freshness.ScopedRead;

public interface PlannerAccess {

    PlannerThread requireExisting(UUID plannerId);

    ScopedRead<UUID> checkPublished(UUID plannerId);
}
