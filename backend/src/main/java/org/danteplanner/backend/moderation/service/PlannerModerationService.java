package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.dto.ModerationResponse;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Moderator control over a planner's public visibility: takedown, unpublish, and the
 * recommended-list hide flag.
 *
 * <p>Holds both sides of the hidden-from-recommended state — the writes and the listing of what is
 * currently hidden — because the listing returns the same {@link ModerationResponse} the writes
 * return, assembled from the same planner aggregate and upvote counter.</p>
 *
 * <p>Each action names the transition it wants and leaves its audit record; loading the aggregate,
 * persisting it, and repairing the public projection belong to
 * {@link PlannerPublishingService}, so a new action here cannot forget any of the three.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerModerationService {

    private final PlannerPublishingService plannerPublishingService;
    private final ModerationAuditService auditService;

    /**
     * Take down a planner (moderator deletion).
     * Owner can still sync their local copy, but planner is removed from public.
     *
     * @param actorId   the moderator/admin performing the action
     * @param plannerId the planner to take down
     * @param reason    reason for takedown (optional)
     * @return the updated planner
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public Planner deletePlanner(Long actorId, UUID plannerId, String reason) {
        Planner saved = plannerPublishingService.withdrawFromPublicView(plannerId, Planner::takeDown);

        auditService.record(actorId, plannerId.toString(),
                ModerationAction.ActionType.DELETE_PLANNER, ModerationAction.TargetType.PLANNER, reason, null);

        log.info("Planner {} taken down by moderator {} with reason: {}", plannerId, actorId, reason);
        return saved;
    }

    /**
     * Unpublish a planner.
     * Sets the published flag to false regardless of current state.
     *
     * @param actorId   the moderator/admin performing the action
     * @param plannerId the planner to unpublish
     * @return the updated planner
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public Planner unpublishPlanner(Long actorId, UUID plannerId) {
        Planner saved = plannerPublishingService.withdrawFromPublicView(
                plannerId, planner -> planner.unpublish().changed());

        auditService.record(actorId, plannerId.toString(),
                ModerationAction.ActionType.UNPUBLISH_PLANNER, ModerationAction.TargetType.PLANNER, null, null);

        log.info("Planner {} unpublished by moderator {}", plannerId, actorId);
        return saved;
    }

    /**
     * Hide a planner from recommended list.
     * Planner remains accessible via direct link, votes unchanged.
     *
     * @param plannerId   the planner to hide
     * @param moderatorId the moderator/admin performing the action
     * @param request     hide request with reason
     * @return moderation response
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public ModerationResponse hideFromRecommended(UUID plannerId, Long moderatorId, HidePlannerRequest request) {
        Planner saved = plannerPublishingService.changeRecommendedListing(plannerId,
                planner -> planner.hideFromRecommended(moderatorId, request.reason()));

        auditService.record(moderatorId, plannerId.toString(),
                ModerationAction.ActionType.HIDE_FROM_RECOMMENDED, ModerationAction.TargetType.PLANNER,
                request.reason(), null);

        log.info("Planner {} hidden from recommended by moderator {} with reason: {}",
                plannerId, moderatorId, request.reason());

        return describe(saved);
    }

    /**
     * Unhide a planner, restoring it to recommended list.
     *
     * @param plannerId   the planner to unhide
     * @param moderatorId the moderator/admin performing the action
     * @return moderation response
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public ModerationResponse unhideFromRecommended(UUID plannerId, Long moderatorId) {
        Planner saved = plannerPublishingService.changeRecommendedListing(plannerId,
                Planner::unhideFromRecommended);

        auditService.record(moderatorId, plannerId.toString(),
                ModerationAction.ActionType.UNHIDE_FROM_RECOMMENDED, ModerationAction.TargetType.PLANNER, null, null);

        log.info("Planner {} unhidden from recommended by moderator {}", plannerId, moderatorId);

        return describe(saved);
    }

    /**
     * List all hidden planners with pagination.
     *
     * @param pageable pagination parameters
     * @return page of moderation responses
     */
    @Transactional(readOnly = true)
    public Page<ModerationResponse> listHiddenPlanners(Pageable pageable) {
        return plannerPublishingService.listHiddenFromRecommended(pageable)
                .map(this::describe);
    }

    private ModerationResponse describe(Planner planner) {
        return ModerationResponse.fromEntity(planner, plannerPublishingService.upvoteCount(planner.getId()));
    }
}
