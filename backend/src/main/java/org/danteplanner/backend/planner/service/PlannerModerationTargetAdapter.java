package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.moderation.api.PlannerModerationTarget;
import org.danteplanner.backend.planner.entity.Planner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlannerModerationTargetAdapter implements PlannerModerationTarget {

    private final PlannerPublishingService plannerPublishingService;
    private final PlannerAccessGuard accessGuard;

    @Override
    public void checkPublished(UUID plannerId) {
        accessGuard.checkPublished(plannerId);
    }

    @Override
    public ModeratedPlanner takeDown(UUID plannerId) {
        return moderated(plannerPublishingService.withdrawFromPublicView(plannerId, Planner::takeDown));
    }

    @Override
    public ModeratedPlanner unpublish(UUID plannerId) {
        return moderated(plannerPublishingService.withdrawFromPublicView(plannerId, Planner::unpublish));
    }

    @Override
    public ModeratedPlanner hideFromRecommended(UUID plannerId, Long moderatorId, String reason) {
        return moderated(plannerPublishingService.changeRecommendedListing(plannerId,
                planner -> planner.hideFromRecommended(moderatorId, reason)));
    }

    @Override
    public ModeratedPlanner unhideFromRecommended(UUID plannerId) {
        return moderated(plannerPublishingService.changeRecommendedListing(plannerId,
                Planner::unhideFromRecommended));
    }

    @Override
    public Page<ModeratedPlanner> listHiddenFromRecommended(Pageable pageable) {
        return plannerPublishingService.listHiddenFromRecommended(pageable).map(this::moderated);
    }

    @Override
    public int upvoteCount(UUID plannerId) {
        return plannerPublishingService.upvoteCount(plannerId);
    }

    private ModeratedPlanner moderated(Planner planner) {
        return new ModeratedPlanner(
                planner.getId(),
                planner.getTitle(),
                planner.isPublished(),
                planner.isHiddenFromRecommended(),
                planner.getModeration().getHiddenByModeratorId(),
                planner.getModeration().getHiddenReason(),
                planner.getModeration().getHiddenAt());
    }
}
