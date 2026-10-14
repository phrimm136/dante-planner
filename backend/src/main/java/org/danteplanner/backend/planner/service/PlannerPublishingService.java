package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PublicationChange;
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.PlannerDocument;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator;
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
    private final PlannerCategoryValidator categoryValidator;
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
        accessGuard.holdActiveAccount(userId);

        return applyPublish(userId, accessGuard.requireAggregate(plannerId));
    }

    @Transactional
    public PlannerResponse publish(Long userId, UUID deviceId, UUID plannerId, UpsertPlannerRequest content) {
        accessGuard.holdActiveAccount(userId);
        requireTitleAfterUpsert(userId, plannerId, content);

        return applyPublish(userId, upserted(userId, deviceId, plannerId, content));
    }

    @Transactional
    public PlannerResponse unpublish(Long userId, UUID plannerId) {
        return applyUnpublish(userId, accessGuard.requireAggregate(plannerId));
    }

    private void requireTitleAfterUpsert(Long userId, UUID plannerId, UpsertPlannerRequest content) {
        if (content.title() != null) {
            publishValidator.requireTitle(content.title());
            return;
        }
        plannerRepository.findAggregateForOwner(plannerId, userId)
                .map(Planner::getTitle)
                .ifPresent(publishValidator::requireTitle);
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
        MDCategory category = categoryValidator.requireCategoryForType(planner.getPlannerType(), planner.getCategory());
        PlannerDocument document = contentValidator.parse(planner.getContentJson(), category,
                contentValidator.season(planner.getContentVersion()), Stage.PUBLISH);
        if (document.migrated()) {
            planner.getContent().replaceContent(document);
        }

        PublicationChange change = planner.publish();
        if (!change.changed()) {
            return PlannerResponse.fromEntity(planner);
        }

        plannerCatalogService.onBecameVisible(planner);
        subscriptionService.createSubscription(userId, plannerId);

        if (change == PublicationChange.FIRST_PUBLISH) {
            domainEventRecorder.recordDomainEvent(DomainEventType.PLANNER_PUBLISHED, plannerId,
                    Map.of("authorId", userId));
        }

        log.info("Published planner {} by user {}", plannerId, userId);
        return PlannerResponse.fromEntity(planner);
    }

    private PlannerResponse applyUnpublish(Long userId, Planner planner) {
        UUID plannerId = planner.getId();
        ownershipValidator.requireOwner(planner, userId);

        if (!planner.unpublish().changed()) {
            return PlannerResponse.fromEntity(planner);
        }

        plannerCatalogService.onBecameInvisible(plannerId);

        log.info("Unpublished planner {} by user {}", plannerId, userId);
        return PlannerResponse.fromEntity(planner);
    }

    @Transactional
    public Planner withdrawFromPublicView(UUID plannerId, Withdrawal withdrawal) {
        Planner planner = accessGuard.requireAggregate(plannerId);

        if (!withdrawal.apply(planner).changed()) {
            return planner;
        }

        plannerCatalogService.onBecameInvisible(plannerId);
        return planner;
    }

    @Transactional
    public Planner changeRecommendedListing(UUID plannerId, Consumer<Planner> change) {
        Planner planner = accessGuard.requireAggregate(plannerId);

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
        Planner planner = accessGuard.requireAggregate(plannerId);

        ownershipValidator.requireOwner(planner, userId);

        planner.setOwnerNotificationsEnabled(enabled);

        log.info("User {} toggled owner notifications for planner {} to {}", userId, plannerId, enabled);
        return new ToggleOwnerNotificationsResponse(enabled);
    }
}
