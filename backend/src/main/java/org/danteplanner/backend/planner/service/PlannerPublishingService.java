package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.shared.sse.SsePublisher;
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
import org.danteplanner.backend.planner.validation.ValidationPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

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
    private final PlannerCatalogService plannerCatalogService;
    private final PlannerSubscriptionService subscriptionService;
    private final SsePublisher ssePublisher;
    private final NotificationService notificationService;
    private final PlannerAccessGuard accessGuard;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Carries the publish SSE broadcast so it can be emitted only after the publishing
     * transaction commits.
     */
    public record PlannerPublishedEvent(
            Long authorId, UUID plannerId, String plannerTitle, Map<String, Object> data) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlannerPublished(PlannerPublishedEvent event) {
        ssePublisher.publishBroadcast(event.authorId(), SseEventType.NOTIFY_PUBLISHED, event.data());
        notificationService.notifyPlannerPublished(
                event.authorId(), event.plannerId(), event.plannerTitle());
    }

    /**
     * Drive a planner to an explicit published state.
     *
     * <p>Idempotent: a request naming the state the planner is already in leaves it untouched, so a
     * retried or failed-over mutation never flips it back.</p>
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @param published the desired published state
     * @return the planner response in the requested state
     * @throws PlannerNotFoundException  if planner not found
     * @throws PlannerForbiddenException if user is not the owner
     */
    @Transactional
    public PlannerResponse setPublished(Long userId, UUID plannerId, boolean published) {
        // Before the lookup, not inside applyPublishedState: a restricted actor must learn nothing
        // about which planners exist, and the two entry points load through different paths.
        accessGuard.checkNotRestricted(userId);

        Planner planner = plannerRepository.findAggregate(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));
        return applyPublishedState(userId, planner, published);
    }

    /**
     * Upsert the carried document and drive the planner to an explicit published state in one
     * request, idempotently.
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @param req       the content-carrying upsert payload
     * @param published the desired published state
     * @return the planner response in the requested state
     */
    @Transactional
    public PlannerResponse setPublishedWithContent(
            Long userId, UUID plannerId, UpsertPlannerRequest req, boolean published) {
        accessGuard.checkNotRestricted(userId);

        Planner planner = plannerCommandService
                .upsertAggregate(userId, null, plannerId, req, false)
                .planner();
        return applyPublishedState(userId, planner, published);
    }

    /**
     * Move an already-loaded aggregate to the requested publication state, leaving it untouched when
     * it is already there.
     *
     * <p>Every path into a publication change crosses this method, so the ownership check lives
     * here rather than at each entry point. The restriction check cannot: it has to precede the
     * lookup so a restricted actor learns nothing about which planners exist.</p>
     */
    private PlannerResponse applyPublishedState(Long userId, Planner planner, boolean published) {
        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(planner.getId());
        }

        if (Boolean.TRUE.equals(planner.getPublished()) == published) {
            return PlannerResponse.fromEntity(planner, currentUpvotes(planner.getId()));
        }
        return applyChange(userId, planner, published);
    }

    private int currentUpvotes(UUID plannerId) {
        return plannerStatsRepository.findById(plannerId)
                .map(PlannerStats::getUpvotes)
                .orElse(0);
    }

    /**
     * Withdraw a planner from public view on a moderator's authority and drop the catalog row that
     * makes it listable.
     *
     * <p>The caller supplies the aggregate transition it wants (takedown, unpublish); which
     * projection has to follow is not the caller's to remember.</p>
     *
     * @param plannerId  the planner to withdraw
     * @param withdrawal the transition to apply to the aggregate
     * @return the persisted planner
     * @throws PlannerNotFoundException if no non-deleted planner carries the id
     */
    @Transactional
    public Planner withdrawFromPublicView(UUID plannerId, Consumer<Planner> withdrawal) {
        Planner planner = plannerRepository.findAggregate(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        withdrawal.accept(planner);
        Planner saved = plannerRepository.save(planner);
        plannerCatalogService.onBecameInvisible(plannerId);
        return saved;
    }

    /**
     * Apply a moderator's change to a planner's standing in the recommended list and recompute the
     * derived flag the public list reads. The planner stays reachable by direct link either way.
     *
     * @param plannerId the planner whose standing changes
     * @param change    the transition to apply to the aggregate
     * @return the persisted planner
     * @throws PlannerNotFoundException if no non-deleted planner carries the id
     */
    @Transactional
    public Planner changeRecommendedListing(UUID plannerId, Consumer<Planner> change) {
        Planner planner = plannerRepository.findAggregate(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        change.accept(planner);
        Planner saved = plannerRepository.save(planner);
        plannerCatalogService.refreshRecommended(plannerId);
        return saved;
    }

    /**
     * The planners a moderator has taken off the recommended list.
     *
     * @param pageable the page to read
     * @return one page of hidden planners
     */
    @Transactional(readOnly = true)
    public Page<Planner> listHiddenFromRecommended(Pageable pageable) {
        return plannerRepository.findHiddenFromRecommended(pageable);
    }

    /**
     * The upvote count shown alongside a planner.
     *
     * @param plannerId the planner ID
     * @return the upvote count, zero when the planner has no stats row yet
     */
    @Transactional(readOnly = true)
    public int upvoteCount(UUID plannerId) {
        return currentUpvotes(plannerId);
    }


    /**
     * Carry an already-loaded aggregate to a publication state it does not currently hold.
     *
     * <p>The caller has established ownership and that the state differs; this applies the change
     * and the projections that follow from it.</p>
     *
     * @param userId    the owner
     * @param planner   the loaded planner aggregate
     * @param published the state to drive the planner to
     * @return the updated planner response
     */
    private PlannerResponse applyChange(Long userId, Planner planner, boolean published) {
        UUID plannerId = planner.getId();

        if (published) {
            if (planner.getTitle() == null || planner.getTitle().isBlank()) {
                throw new PlannerValidationException("MISSING_TITLE", "Title is required for publishing");
            }
            contentValidator.validate(planner.getContentJson(), planner.getCategory(), ValidationPolicy.PUBLISH);
        }

        boolean firstPublish = planner.getFirstPublishedAt() == null;
        planner.setPublished(published);

        Planner saved = plannerRepository.save(planner);

        if (published) {
            plannerCatalogService.onBecameVisible(saved);
        } else {
            plannerCatalogService.onBecameInvisible(plannerId);
        }

        if (published) {
            subscriptionService.createSubscription(userId, plannerId);

            // First-time publish notification (one-time only). The DB fan-out and the SSE
            // broadcast both run from the AFTER_COMMIT listener, so neither persists nor fires
            // when the publish rolls back.
            if (firstPublish) {
                User author = saved.getUser();
                eventPublisher.publishEvent(new PlannerPublishedEvent(
                        userId, plannerId, saved.getTitle(), Map.of(
                        "plannerId", plannerId.toString(),
                        "plannerTitle", saved.getTitle(),
                        "authorEpithet", author.getUsernameEpithet(),
                        "authorSuffix", author.getUsernameSuffix()
                )));
                log.info("Broadcast first-publish notification for planner {} by user {}", plannerId, userId);
            }
        }

        log.info("Set publish status for planner {} to {} by user {}",
                plannerId, saved.getPublished(), userId);

        int upvotes = plannerStatsRepository.findById(plannerId)
                .map(PlannerStats::getUpvotes)
                .orElse(0);
        return PlannerResponse.fromEntity(saved, upvotes);
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

        planner.setOwnerNotificationsEnabled(enabled);
        plannerRepository.save(planner);

        log.info("User {} toggled owner notifications for planner {} to {}", userId, plannerId, enabled);
        return new ToggleOwnerNotificationsResponse(enabled);
    }
}
