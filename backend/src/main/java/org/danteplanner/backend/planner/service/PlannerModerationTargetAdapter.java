package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.moderation.api.PlannerModerationTarget;
import org.danteplanner.backend.moderation.api.VisiblePlanner;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlannerModerationTargetAdapter implements PlannerModerationTarget {

    private final PlannerPublishingService plannerPublishingService;
    private final PlannerAccessGuard accessGuard;

    @Override
    public VisiblePlanner requirePublished(UUID plannerId) {
        return new VisiblePlanner(accessGuard.checkPublished(plannerId).value());
    }

    @Override
    public ModeratedPlanner takeDown(UUID plannerId) {
        return plannerPublishingService.withdrawFromPublicView(plannerId, new ModeratorAction.TakeDown());
    }

    @Override
    public ModeratedPlanner unpublish(UUID plannerId) {
        return plannerPublishingService.withdrawFromPublicView(plannerId, new ModeratorAction.Unpublish());
    }

    @Override
    public ModeratedPlanner hideFromRecommended(UUID plannerId, Long moderatorId, String reason) {
        return plannerPublishingService.changeRecommendedListing(plannerId,
                new ModeratorAction.Hide(moderatorId, reason));
    }

    @Override
    public ModeratedPlanner unhideFromRecommended(UUID plannerId) {
        return plannerPublishingService.changeRecommendedListing(plannerId, new ModeratorAction.Unhide());
    }

    @Override
    public int upvoteCount(UUID plannerId) {
        return plannerPublishingService.upvoteCount(plannerId);
    }
}
