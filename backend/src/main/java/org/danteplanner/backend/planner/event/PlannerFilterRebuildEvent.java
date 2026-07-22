package org.danteplanner.backend.planner.event;

import java.util.Set;
import java.util.UUID;

/**
 * Requests a filter-index rebuild for a planner after the owning write commits.
 * Null content and keywords clear the indexes (unpublish/delete/takedown).
 */
public record PlannerFilterRebuildEvent(
    UUID plannerId,
    String contentJson,
    Set<String> selectedKeywords
) {

    public static PlannerFilterRebuildEvent rebuild(UUID plannerId, String contentJson, Set<String> keywords) {
        return new PlannerFilterRebuildEvent(plannerId, contentJson, keywords);
    }

    public static PlannerFilterRebuildEvent clear(UUID plannerId) {
        return new PlannerFilterRebuildEvent(plannerId, null, null);
    }
}
