package org.danteplanner.backend.support;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.danteplanner.backend.planner.api.PlannerNotificationTarget;
import org.danteplanner.backend.planner.api.PublishedPlanners;

public class FakePublishedPlanners implements PublishedPlanners {

    private final Map<UUID, PlannerNotificationTarget> targets = new HashMap<>();
    private final Set<UUID> published = new HashSet<>();

    public void add(PlannerNotificationTarget target, boolean isPublished) {
        targets.put(target.plannerId(), target);
        if (isPublished) {
            published.add(target.plannerId());
        } else {
            published.remove(target.plannerId());
        }
    }

    @Override
    public boolean isPublished(UUID plannerId) {
        return published.contains(plannerId);
    }

    @Override
    public Optional<PlannerNotificationTarget> notificationTargetOf(UUID plannerId) {
        return Optional.ofNullable(targets.get(plannerId));
    }
}
