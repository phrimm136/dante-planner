package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;

public record PlannerPublishedPayload(
    String plannerId,
    String plannerTitle,
    String authorEpithet,
    String authorSuffix
) {

    public static PlannerPublishedPayload fromEntity(Planner planner) {
        User author = planner.getUser();
        return new PlannerPublishedPayload(
                planner.getId().toString(),
                planner.getTitle(),
                author == null ? null : author.getUsernameEpithet(),
                author == null ? null : author.getUsernameSuffix());
    }
}
