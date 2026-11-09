package org.danteplanner.backend.moderation.api;

import java.util.UUID;

public interface PlannerModerationTarget {

    void checkPublished(UUID plannerId);

    ModeratedPlanner takeDown(UUID plannerId);

    ModeratedPlanner unpublish(UUID plannerId);

    ModeratedPlanner hideFromRecommended(UUID plannerId, Long moderatorId, String reason);

    ModeratedPlanner unhideFromRecommended(UUID plannerId);

    int upvoteCount(UUID plannerId);
}
