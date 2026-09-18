package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.dto.ModerationResponse;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Moderator control over a planner's public visibility: takedown, unpublish, and the
 * recommended-list hide flag.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerModerationService {

    private final PlannerPublishingService plannerPublishingService;
    private final ModerationAuditService auditService;

    @Transactional
    public Planner deletePlanner(Long actorId, UUID plannerId, String reason) {
        Planner saved = plannerPublishingService.withdrawFromPublicView(plannerId, Planner::takeDown);

        auditService.record(actorId, plannerId.toString(),
                ModerationAction.ActionType.DELETE_PLANNER, ModerationAction.TargetType.PLANNER, reason);

        log.info("Planner {} taken down by moderator {} with reason: {}", plannerId, actorId, reason);
        return saved;
    }

    @Transactional
    public Planner unpublishPlanner(Long actorId, UUID plannerId) {
        Planner saved = plannerPublishingService.withdrawFromPublicView(plannerId, Planner::unpublish);

        auditService.record(actorId, plannerId.toString(),
                ModerationAction.ActionType.UNPUBLISH_PLANNER, ModerationAction.TargetType.PLANNER);

        log.info("Planner {} unpublished by moderator {}", plannerId, actorId);
        return saved;
    }

    @Transactional
    public ModerationResponse hideFromRecommended(UUID plannerId, Long moderatorId, HidePlannerRequest request) {
        Planner saved = plannerPublishingService.changeRecommendedListing(plannerId,
                planner -> planner.hideFromRecommended(moderatorId, request.reason()));

        auditService.record(moderatorId, plannerId.toString(),
                ModerationAction.ActionType.HIDE_FROM_RECOMMENDED, ModerationAction.TargetType.PLANNER,
                request.reason());

        log.info("Planner {} hidden from recommended by moderator {} with reason: {}",
                plannerId, moderatorId, request.reason());

        return describe(saved);
    }

    @Transactional
    public ModerationResponse unhideFromRecommended(UUID plannerId, Long moderatorId) {
        Planner saved = plannerPublishingService.changeRecommendedListing(plannerId,
                Planner::unhideFromRecommended);

        auditService.record(moderatorId, plannerId.toString(),
                ModerationAction.ActionType.UNHIDE_FROM_RECOMMENDED, ModerationAction.TargetType.PLANNER);

        log.info("Planner {} unhidden from recommended by moderator {}", plannerId, moderatorId);

        return describe(saved);
    }

    @Transactional(readOnly = true)
    public Page<ModerationResponse> listHiddenPlanners(Pageable pageable) {
        return plannerPublishingService.listHiddenFromRecommended(pageable)
                .map(this::describe);
    }

    private ModerationResponse describe(Planner planner) {
        return ModerationResponse.fromEntity(planner, plannerPublishingService.upvoteCount(planner.getId()));
    }
}
