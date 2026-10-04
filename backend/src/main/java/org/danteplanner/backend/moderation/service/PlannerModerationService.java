package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.dto.ModerationResponse;
import org.danteplanner.backend.moderation.api.PlannerModerationTarget;
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

    private final PlannerModerationTarget plannerTarget;
    private final ModerationAuditService auditService;

    @Transactional
    public ModeratedPlanner deletePlanner(Long actorId, UUID plannerId, String reason) {
        ModeratedPlanner saved = plannerTarget.takeDown(plannerId);

        auditService.record(actorId, plannerId.toString(),
                ModerationActionType.DELETE_PLANNER, ModerationTargetType.PLANNER, reason);

        log.info("Planner {} taken down by moderator {} with reason: {}", plannerId, actorId, reason);
        return saved;
    }

    @Transactional
    public ModeratedPlanner unpublishPlanner(Long actorId, UUID plannerId) {
        ModeratedPlanner saved = plannerTarget.unpublish(plannerId);

        auditService.record(actorId, plannerId.toString(),
                ModerationActionType.UNPUBLISH_PLANNER, ModerationTargetType.PLANNER);

        log.info("Planner {} unpublished by moderator {}", plannerId, actorId);
        return saved;
    }

    @Transactional
    public ModerationResponse hideFromRecommended(UUID plannerId, Long moderatorId, HidePlannerRequest request) {
        ModeratedPlanner saved = plannerTarget.hideFromRecommended(plannerId, moderatorId, request.reason());

        auditService.record(moderatorId, plannerId.toString(),
                ModerationActionType.HIDE_FROM_RECOMMENDED, ModerationTargetType.PLANNER,
                request.reason());

        log.info("Planner {} hidden from recommended by moderator {} with reason: {}",
                plannerId, moderatorId, request.reason());

        return describe(saved);
    }

    @Transactional
    public ModerationResponse unhideFromRecommended(UUID plannerId, Long moderatorId) {
        ModeratedPlanner saved = plannerTarget.unhideFromRecommended(plannerId);

        auditService.record(moderatorId, plannerId.toString(),
                ModerationActionType.UNHIDE_FROM_RECOMMENDED, ModerationTargetType.PLANNER);

        log.info("Planner {} unhidden from recommended by moderator {}", plannerId, moderatorId);

        return describe(saved);
    }

    @Transactional(readOnly = true)
    public Page<ModerationResponse> listHiddenPlanners(Pageable pageable) {
        return plannerTarget.listHiddenFromRecommended(pageable)
                .map(this::describe);
    }

    private ModerationResponse describe(ModeratedPlanner planner) {
        return ModerationResponse.from(planner, plannerTarget.upvoteCount(planner.id()));
    }
}
