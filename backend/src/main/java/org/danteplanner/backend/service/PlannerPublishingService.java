package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.planner.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.SseEventType;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.PlannerForbiddenException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.validation.PlannerContentValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for the publish lifecycle of a planner.
 * Handles toggling publish status and owner notification settings.
 */
@Service
@Slf4j
public class PlannerPublishingService {

    private final PlannerRepository plannerRepository;
    private final PlannerContentValidator contentValidator;
    private final PlannerIndexService plannerIndexService;
    private final PlannerSubscriptionService subscriptionService;
    private final SseService notificationSseService;
    private final NotificationService notificationService;
    private final PlannerAccessGuard accessGuard;

    public PlannerPublishingService(
            PlannerRepository plannerRepository,
            PlannerContentValidator contentValidator,
            PlannerIndexService plannerIndexService,
            PlannerSubscriptionService subscriptionService,
            SseService notificationSseService,
            NotificationService notificationService,
            PlannerAccessGuard accessGuard) {
        this.plannerRepository = plannerRepository;
        this.contentValidator = contentValidator;
        this.plannerIndexService = plannerIndexService;
        this.subscriptionService = subscriptionService;
        this.notificationSseService = notificationSseService;
        this.notificationService = notificationService;
        this.accessGuard = accessGuard;
    }

    /**
     * Toggle the published status of a planner.
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @return the updated planner
     * @throws PlannerNotFoundException  if planner not found
     * @throws PlannerForbiddenException if user is not the owner
     */
    @Transactional
    public Planner togglePublish(Long userId, UUID plannerId) {
        // Check if user has any restrictions
        accessGuard.checkUserRestrictions(userId);

        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        // Verify ownership
        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(plannerId);
        }

        // Block publishing if planner was taken down by moderator
        if (planner.isTakenDown() && !planner.getPublished()) {
            throw new PlannerForbiddenException(plannerId);
        }

        // If publishing (not unpublishing), validate content with strict mode
        if (!planner.getPublished()) {
            // Title is required for publish
            if (planner.getTitle() == null || planner.getTitle().isBlank()) {
                throw new PlannerValidationException("MISSING_TITLE", "Title is required for publishing");
            }
            contentValidator.validate(planner.getContent(), planner.getCategory(), true);
        }

        // Toggle published status
        boolean wasPublished = planner.getPublished();
        planner.setPublished(!wasPublished);
        Planner saved = plannerRepository.save(planner);

        if (!wasPublished) {
            plannerIndexService.reindex(plannerId, saved.getContent());
        } else {
            plannerIndexService.deleteIndex(plannerId);
        }

        // Auto-subscribe owner when publishing (not unpublishing)
        if (!wasPublished && saved.getPublished()) {
            subscriptionService.createSubscription(userId, plannerId);

            // First-time publish notification (one-time only)
            if (planner.getFirstPublishedAt() == null) {
                planner.setFirstPublishedAt(Instant.now());
                saved = plannerRepository.save(planner);

                // Create DB notifications for users with setting enabled
                notificationService.notifyPlannerPublished(userId, plannerId, saved.getTitle());

                // Broadcast SSE to all connected users except author
                User author = saved.getUser();
                notificationSseService.broadcastToAll(userId, SseEventType.NOTIFY_PUBLISHED.getValue(), Map.of(
                        "plannerId", plannerId.toString(),
                        "plannerTitle", saved.getTitle(),
                        "authorEpithet", author.getUsernameEpithet(),
                        "authorSuffix", author.getUsernameSuffix()
                ));
                log.info("Broadcast first-publish notification for planner {} by user {}", plannerId, userId);
            }
        }

        log.info("Toggled publish status for planner {} to {} by user {}",
                plannerId, saved.getPublished(), userId);

        return saved;
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
        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(plannerId);
        }

        planner.setOwnerNotificationsEnabled(enabled);
        plannerRepository.save(planner);

        log.info("User {} toggled owner notifications for planner {} to {}", userId, plannerId, enabled);
        return new ToggleOwnerNotificationsResponse(enabled);
    }
}
