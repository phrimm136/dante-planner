package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.api.UserProfile;

public record PlannerPublishedPayload(
    String plannerId,
    String plannerTitle,
    String authorEpithet,
    String authorSuffix
) {

    public static PlannerPublishedPayload fromEntity(Planner planner, UserProfile author) {
        return new PlannerPublishedPayload(
                planner.getId().toString(),
                planner.getTitle(),
                author.getUsernameEpithet(),
                author.getUsernameSuffix());
    }
}
