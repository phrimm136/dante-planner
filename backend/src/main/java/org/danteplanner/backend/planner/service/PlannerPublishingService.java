package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.notification.service.NotificationDispatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.dto.PlannerPublishedPayload;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PublicationChange;
import org.danteplanner.backend.shared.entity.SseEventType;
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

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Service for the publish lifecycle of a planner.
 * Handles the publish and unpublish intents and owner notification settings.
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
    private final NotificationDispatchService notificationDispatchService;
    private final PlannerAccessGuard accessGuard;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Carries the publish SSE broadcast so it can be emitted only after the publishing
     * transaction commits.
     */
    public record PlannerPublishedEvent(
            Long authorId, UUID plannerId, String plannerTitle, PlannerPublishedPayload data) {
    }

    /**
     * A moderator transition that withdraws a planner from public view.
     */
    @FunctionalInterface
    public interface Withdrawal {

        /**
         * Apply the transition.
         *
         * @param planner the loaded aggregate
         * @return what the transition turned out to be
         */
        PublicationChange apply(Planner planner);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlannerPublished(PlannerPublishedEvent event) {
        ssePublisher.publishBroadcast(event.authorId(), SseEventType.NOTIFY_PUBLISHED, event.data());
        notificationDispatchService.notifyPlannerPublished(
                event.authorId(), event.plannerId(), event.plannerTitle());
    }

    /**
     * Publish a planner the owner has already stored.
     *
     * <p>Idempotent: publishing an already-published planner leaves it untouched, so a retried or
     * failed-over mutation never flips it back.</p>
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @return the published planner response
     * @throws PlannerNotFoundException   if planner not found
     * @throws PlannerForbiddenException  if user is not the owner, or the planner was taken down
     * @throws PlannerValidationException if the planner is not publishable
     */
    @Transactional
    public PlannerResponse publish(Long userId, UUID plannerId) {
        // Before the lookup, not inside applyPublish: a restricted actor must learn nothing about
        // which planners exist, and the entry points load through different paths.
        accessGuard.checkNotRestricted(userId);

        return applyPublish(userId, accessGuard.requireExisting(plannerId));
    }

    /**
     * Upsert the carried document and publish it in one request, so a client-side draft costs one
     * round trip.
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @param content   the document to store before publishing
     * @return the published planner response
     */
    @Transactional
    public PlannerResponse publish(Long userId, UUID plannerId, UpsertPlannerRequest content) {
        accessGuard.checkNotRestricted(userId);

        return applyPublish(userId, upserted(userId, plannerId, content));
    }

    /**
     * Withdraw a planner from public view on its owner's authority.
     *
     * <p>Idempotent, for the same reason {@link #publish(Long, UUID)} is.</p>
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @return the withdrawn planner response
     * @throws PlannerNotFoundException  if planner not found
     * @throws PlannerForbiddenException if user is not the owner
     */
    @Transactional
    public PlannerResponse unpublish(Long userId, UUID plannerId) {
        accessGuard.checkNotRestricted(userId);

        return applyUnpublish(userId, accessGuard.requireExisting(plannerId));
    }

    /**
     * Upsert the carried document and withdraw the planner from public view in one request.
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @param content   the document to store before withdrawing
     * @return the withdrawn planner response
     */
    @Transactional
    public PlannerResponse unpublish(Long userId, UUID plannerId, UpsertPlannerRequest content) {
        accessGuard.checkNotRestricted(userId);

        return applyUnpublish(userId, upserted(userId, plannerId, content));
    }

    private Planner upserted(Long userId, UUID plannerId, UpsertPlannerRequest content) {
        return plannerCommandService
                .upsertAggregate(userId, null, plannerId, content, false)
                .planner();
    }

    /**
     * Publish an already-loaded aggregate.
     *
     * <p>Both entry points cross this method, so the ownership check lives here rather than at each
     * of them. The restriction check cannot: it has to precede the lookup so a restricted actor
     * learns nothing about which planners exist.</p>
     *
     * <p>Publishability is decided before the aggregate moves, so a refusal leaves the planner in
     * the state the caller found it in rather than relying on the rollback to put it back.</p>
     */
    private PlannerResponse applyPublish(Long userId, Planner planner) {
        UUID plannerId = planner.getId();
        requireOwner(userId, planner);

        if (planner.getTitle() == null || planner.getTitle().isBlank()) {
            throw new PlannerValidationException("MISSING_TITLE", "Title is required for publishing");
        }
        contentValidator.validate(planner.getContentJson(), planner.getCategory(), ValidationPolicy.PUBLISH);

        PublicationChange change = planner.publish();
        if (!change.changed()) {
            return describe(planner);
        }

        Planner saved = plannerRepository.save(planner);
        plannerCatalogService.onBecameVisible(saved);
        subscriptionService.createSubscription(userId, plannerId);

        // The DB fan-out and the SSE broadcast both run from the AFTER_COMMIT listener, so neither
        // persists nor fires when the publish rolls back.
        if (change == PublicationChange.FIRST_PUBLISH) {
            eventPublisher.publishEvent(new PlannerPublishedEvent(
                    userId, plannerId, saved.getTitle(),
                    PlannerPublishedPayload.fromEntity(saved)));
            log.info("Broadcast first-publish notification for planner {} by user {}", plannerId, userId);
        }

        log.info("Published planner {} by user {}", plannerId, userId);
        return describe(saved);
    }

    /**
     * Withdraw an already-loaded aggregate from public view, on the same terms as
     * {@link #applyPublish(Long, Planner)}.
     */
    private PlannerResponse applyUnpublish(Long userId, Planner planner) {
        UUID plannerId = planner.getId();
        requireOwner(userId, planner);

        if (!planner.unpublish().changed()) {
            return describe(planner);
        }

        Planner saved = plannerRepository.save(planner);
        plannerCatalogService.onBecameInvisible(plannerId);

        log.info("Unpublished planner {} by user {}", plannerId, userId);
        return describe(saved);
    }

    private void requireOwner(Long userId, Planner planner) {
        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(planner.getId());
        }
    }

    private PlannerResponse describe(Planner planner) {
        return PlannerResponse.fromEntity(planner, plannerStatsRepository.upvotesOf(planner.getId()));
    }

    /**
     * Withdraw a planner from public view on a moderator's authority and drop the catalog row that
     * makes it listable.
     *
     * <p>The caller supplies the aggregate transition it wants (takedown, unpublish); which
     * projection has to follow is not the caller's to remember.</p>
     *
     * <p>Idempotent on the transition's own report: a planner already in the withdrawn state is
     * neither rewritten nor re-projected.</p>
     *
     * @param plannerId  the planner to withdraw
     * @param withdrawal the transition to apply to the aggregate
     * @return the persisted planner, or the untouched one when it was already withdrawn
     * @throws PlannerNotFoundException if no non-deleted planner carries the id
     */
    @Transactional
    public Planner withdrawFromPublicView(UUID plannerId, Withdrawal withdrawal) {
        Planner planner = accessGuard.requireExisting(plannerId);

        if (!withdrawal.apply(planner).changed()) {
            return planner;
        }

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
        Planner planner = accessGuard.requireExisting(plannerId);

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
        return plannerStatsRepository.upvotesOf(plannerId);
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
        Planner planner = accessGuard.requireExisting(plannerId);

        if (!planner.isOwnedBy(userId)) {
            throw new PlannerForbiddenException(plannerId);
        }

        planner.setOwnerNotificationsEnabled(enabled);
        plannerRepository.save(planner);

        log.info("User {} toggled owner notifications for planner {} to {}", userId, plannerId, enabled);
        return new ToggleOwnerNotificationsResponse(enabled);
    }
}
