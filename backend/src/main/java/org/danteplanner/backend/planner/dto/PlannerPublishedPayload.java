package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.user.api.UserProfile;

public record PlannerPublishedPayload(
    String plannerId,
    String plannerTitle,
    String authorEpithet,
    String authorSuffix
) {

    public static PlannerPublishedPayload from(StoredPlanner planner, UserProfile author) {
        return new PlannerPublishedPayload(
                planner.id().toString(),
                planner.content().title(),
                author.getUsernameEpithet(),
                author.getUsernameSuffix());
    }
}
