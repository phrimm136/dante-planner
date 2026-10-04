package org.danteplanner.backend.moderation.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PlannerModerationTarget {

    void checkPublished(UUID plannerId);

    ModeratedPlanner takeDown(UUID plannerId);

    ModeratedPlanner unpublish(UUID plannerId);

    ModeratedPlanner hideFromRecommended(UUID plannerId, Long moderatorId, String reason);

    ModeratedPlanner unhideFromRecommended(UUID plannerId);

    Page<ModeratedPlanner> listHiddenFromRecommended(Pageable pageable);

    int upvoteCount(UUID plannerId);
}
