package org.danteplanner.backend.planner.api;

import java.util.Optional;
import java.util.UUID;

public interface PublishedPlanners {

    boolean isPublished(UUID plannerId);

    Optional<PlannerNotificationTarget> notificationTargetOf(UUID plannerId);
}
