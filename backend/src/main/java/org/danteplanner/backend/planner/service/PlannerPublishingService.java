package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PublicationChange;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.validation.PlannerPublishValidator;
import org.danteplanner.backend.planner.validation.ValidationPolicy;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Service for the publish lifecycle of a planner.
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
    private final PlannerAccessGuard accessGuard;
    private final PlannerOwnershipValidator ownershipValidator;
    private final PlannerPublishValidator publishValidator;
    private final DomainEventRecorder domainEventRecorder;

    /**
     * A moderator transition that withdraws a planner from public view.
     */
    @FunctionalInterface
    public interface Withdrawal {

        PublicationChange apply(Planner planner);
    }

    @Transactional
    public PlannerResponse publish(Long userId, UUID plannerId) {
        accessGuard.checkNotRestricted(userId);

        return applyPublish(userId, accessGuard.requireExisting(plannerId));
    }

    @Transactional
    public PlannerResponse publish(Long userId, UUID deviceId, UUID plannerId, UpsertPlannerRequest content) {
        accessGuard.checkNotRestricted(userId);

        return applyPublish(userId, upserted(userId, deviceId, plannerId, content));
    }

    @Transactional
    public PlannerResponse unpublish(Long userId, UUID plannerId) {
        return applyUnpublish(userId, accessGuard.requireExisting(plannerId));
    }

    @Transactional
    public PlannerResponse unpublish(Long userId, UUID deviceId, UUID plannerId, UpsertPlannerRequest content) {
        return applyUnpublish(userId, upserted(userId, deviceId, plannerId, content));
    }

    private Planner upserted(Long userId, UUID deviceId, UUID plannerId, UpsertPlannerRequest content) {
        return plannerCommandService
                .upsertAggregate(userId, deviceId, plannerId, content, false)
                .planner();
    }

    private PlannerResponse applyPublish(Long userId, Planner planner) {
        UUID plannerId = planner.getId();
        ownershipValidator.requireOwner(planner, userId);

        publishValidator.requireTitle(planner.getTitle());
        String normalized = contentValidator.validate(planner.getContentJson(), planner.getCategory(),
                planner.getContentVersion(), ValidationPolicy.PUBLISH);
        if (!normalized.equals(planner.getContentJson())) {
            planner.getContent().setContent(normalized);
        }

        PublicationChange change = planner.publish();
        if (!change.changed()) {
            return describe(planner);
        }

        plannerCatalogService.onBecameVisible(planner);
        subscriptionService.createSubscription(userId, plannerId);

        if (change == PublicationChange.FIRST_PUBLISH) {
            domainEventRecorder.recordDomainEvent(DomainEventType.PLANNER_PUBLISHED, plannerId,
                    Map.of("authorId", userId));
        }

        log.info("Published planner {} by user {}", plannerId, userId);
        return describe(planner);
    }

    private PlannerResponse applyUnpublish(Long userId, Planner planner) {
        UUID plannerId = planner.getId();
        ownershipValidator.requireOwner(planner, userId);

        if (!planner.unpublish().changed()) {
            return describe(planner);
        }

        plannerCatalogService.onBecameInvisible(plannerId);

        log.info("Unpublished planner {} by user {}", plannerId, userId);
        return describe(planner);
    }

    private PlannerResponse describe(Planner planner) {
        return PlannerResponse.fromEntity(planner, plannerStatsRepository.upvotesOf(planner.getId()));
    }

    @Transactional
    public Planner withdrawFromPublicView(UUID plannerId, Withdrawal withdrawal) {
        Planner planner = accessGuard.requireExisting(plannerId);

        if (!withdrawal.apply(planner).changed()) {
            return planner;
        }

        plannerCatalogService.onBecameInvisible(plannerId);
        return planner;
    }

    @Transactional
    public Planner changeRecommendedListing(UUID plannerId, Consumer<Planner> change) {
        Planner planner = accessGuard.requireExisting(plannerId);

        change.accept(planner);
        plannerCatalogService.refreshRecommended(plannerId);
        return planner;
    }

    @Transactional(readOnly = true)
    public Page<Planner> listHiddenFromRecommended(Pageable pageable) {
        return plannerRepository.findHiddenFromRecommended(pageable);
    }

    @Transactional(readOnly = true)
    public int upvoteCount(UUID plannerId) {
        return plannerStatsRepository.upvotesOf(plannerId);
    }


    @Transactional
    public ToggleOwnerNotificationsResponse toggleOwnerNotifications(Long userId, UUID plannerId, boolean enabled) {
        Planner planner = accessGuard.requireExisting(plannerId);

        ownershipValidator.requireOwner(planner, userId);

        planner.setOwnerNotificationsEnabled(enabled);

        log.info("User {} toggled owner notifications for planner {} to {}", userId, plannerId, enabled);
        return new ToggleOwnerNotificationsResponse(enabled);
    }
}
