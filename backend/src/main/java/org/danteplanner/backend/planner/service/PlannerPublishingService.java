package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

/**
 * Service for the publish lifecycle of a planner.
 * Handles toggling publish status and owner notification settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerPublishingService {

    private final PlannerRepository plannerRepository;
    private final PlannerStatsRepository plannerStatsRepository;
    private final PlannerCommandService plannerCommandService;
    private final PlannerContentValidator contentValidator;
    private final PlannerFilterService plannerFilterService;
    private final PlannerCatalogService plannerCatalogService;
    private final PlannerSubscriptionService subscriptionService;
    private final SseService notificationSseService;
    private final NotificationService notificationService;
    private final PlannerAccessGuard accessGuard;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Carries the publish SSE broadcast so it can be emitted only after the publishing
     * transaction commits.
     */
    public record PlannerPublishedEvent(Long excludeUserId, String eventType, Map<String, Object> data) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlannerPublished(PlannerPublishedEvent event) {
        notificationSseService.broadcastToAll(event.excludeUserId(), event.eventType(), event.data());
    }

    /**
     * Toggle the published status of a planner.
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @return the updated planner response
     * @throws PlannerNotFoundException  if planner not found
     * @throws PlannerForbiddenException if user is not the owner
     */
    @Transactional
    public PlannerResponse togglePublish(Long userId, UUID plannerId) {
        // Check if user has any restrictions
        accessGuard.checkUserRestrictions(userId);

        Planner planner = plannerRepository.findAggregate(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        // Verify ownership
        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(plannerId);
        }

        boolean firstPublish = planner.getFirstPublishedAt() == null;
        boolean nowPublished = planner.togglePublished();

        // If publishing (not unpublishing), validate content with strict mode
        if (nowPublished) {
            // Title is required for publish
            if (planner.getTitle() == null || planner.getTitle().isBlank()) {
                throw new PlannerValidationException("MISSING_TITLE", "Title is required for publishing");
            }
            contentValidator.validate(planner.getContentJson(), planner.getCategory(), true);
        }

        Planner saved = plannerRepository.save(planner);

        if (nowPublished) {
            plannerCatalogService.add(saved);
            plannerFilterService.requestRebuild(plannerId, saved.getContentJson(), saved.getSelectedKeywords());
        } else {
            plannerCatalogService.remove(plannerId);
            plannerFilterService.requestClear(plannerId);
        }

        // Auto-subscribe owner when publishing (not unpublishing)
        if (nowPublished) {
            subscriptionService.createSubscription(userId, plannerId);

            // First-time publish notification (one-time only)
            if (firstPublish) {
                // Create DB notifications for users with setting enabled
                notificationService.notifyPlannerPublished(userId, plannerId, saved.getTitle());

                // Broadcast SSE to all connected users except author, only after commit
                User author = saved.getUser();
                eventPublisher.publishEvent(new PlannerPublishedEvent(
                        userId, SseEventType.NOTIFY_PUBLISHED.getValue(), Map.of(
                        "plannerId", plannerId.toString(),
                        "plannerTitle", saved.getTitle(),
                        "authorEpithet", author.getUsernameEpithet(),
                        "authorSuffix", author.getUsernameSuffix()
                )));
                log.info("Broadcast first-publish notification for planner {} by user {}", plannerId, userId);
            }
        }

        log.info("Toggled publish status for planner {} to {} by user {}",
                plannerId, saved.getPublished(), userId);

        int upvotes = plannerStatsRepository.findById(plannerId)
                .map(PlannerStats::getUpvotes)
                .orElse(0);
        return PlannerResponse.fromEntity(saved, upvotes);
    }

    /**
     * Publish with content in one request: upsert the carried document (creating
     * the planner if it never synced), then ensure it is published — atomically.
     * A stale-content conflict or takedown rejection rolls back both halves.
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @param req       the content-carrying upsert payload
     * @return the updated planner response
     */
    @Transactional
    public PlannerResponse publishWithContent(Long userId, UUID plannerId, UpsertPlannerRequest req) {
        plannerCommandService.upsertPlanner(userId, null, plannerId, req, false);
        Planner planner = plannerRepository.findAggregateForOwner(plannerId, userId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));
        if (!Boolean.TRUE.equals(planner.getPublished())) {
            return togglePublish(userId, plannerId);
        }
        int upvotes = plannerStatsRepository.findById(plannerId)
                .map(PlannerStats::getUpvotes)
                .orElse(0);
        return PlannerResponse.fromEntity(planner, upvotes);
    }

    /**
     * Toggle owner notifications for a planner.
     * Only the planner owner can toggle this setting.
     *
     * @param userId    the authenticated user ID (must be owner)
     * @param plannerId the planner UUID
     * @param enabled   the new notification setting
     * @return the toggle result
     * @throws PlannerNotFoundException if planner doesn't exist
     * @throws PlannerForbiddenException if user is not the owner
     */
    @Transactional
    public ToggleOwnerNotificationsResponse toggleOwnerNotifications(Long userId, UUID plannerId, boolean enabled) {
        Planner planner = plannerRepository.findAggregate(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(plannerId);
        }

        planner.getPublication().setOwnerNotificationsEnabled(enabled);
        plannerRepository.save(planner);

        log.info("User {} toggled owner notifications for planner {} to {}", userId, plannerId, enabled);
        return new ToggleOwnerNotificationsResponse(enabled);
    }
}
