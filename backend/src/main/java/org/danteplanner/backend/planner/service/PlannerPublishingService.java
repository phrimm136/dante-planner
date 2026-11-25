package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PublicationOutcome;
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.sync.SyncVersionValidator.WriteMode;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.PlannerDocument;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator.LiveOwner;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator.OwnedPlanner;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;

    /**
     * A moderator transition that withdraws a planner from public view.
     */
    @FunctionalInterface
    public interface Withdrawal {

        PublicationOutcome apply(Planner planner);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlannerResponse publish(Long userId, UUID plannerId) {
        accessGuard.holdActiveAccount(userId);
        lockAsOwner(userId, plannerId);

        return applyPublish(ownershipValidator.requireOwner(accessGuard.requireAggregate(plannerId), userId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlannerResponse publish(Long userId, UUID deviceId, UUID plannerId, UpsertPlannerRequest content) {
        accessGuard.holdActiveAccount(userId);
        plannerCommandService.lockForUpsert(userId, plannerId);
        requireTitleAfterUpsert(userId, plannerId, content);

        return applyPublish(ownershipValidator.requireOwner(
                upserted(userId, deviceId, plannerId, content), userId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlannerResponse unpublish(Long userId, UUID plannerId) {
        lockAsOwner(userId, plannerId);
        return applyUnpublish(ownershipValidator.requireOwner(accessGuard.requireAggregate(plannerId), userId));
    }

    private void lockAsOwner(Long userId, UUID plannerId) {
        LiveOwner owner = ownershipValidator.requireLiveOwner(
                plannerId, userId, plannerRepository.findOwnershipById(plannerId));
        plannerRepository.lockContent(owner.plannerId());
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
                .upsertAggregate(userId, deviceId, plannerId, content, WriteMode.CHECKED)
                .planner();
    }

    private PlannerResponse applyPublish(OwnedPlanner owned) {
        Planner planner = owned.planner();

        publishValidator.requireTitle(planner.getTitle());
        MDCategory category;
        try {
            category = categoryValidator.requireCategoryForType(planner.getPlannerType(), planner.getCategory());
        } catch (PlannerValidationException refusal) {
            InvalidCategoryLog.warn(log, refusal);
            throw refusal;
        }
        PlannerDocument document = contentValidator.parse(planner.getContentJson(), category,
                contentValidator.season(planner.getContentVersion()), Stage.PUBLISH);
        switch (ParsedContent.of(document)) {
            case MIGRATED -> {
                planner.getContent().replaceContent(document);
                FreshnessDeclarations.declare(new FreshnessScope.Planner(planner.getId()),
                        new FreshnessScope.User(planner.getUserId()));
            }
            case AS_STORED -> { }
        }

        switch (planner.publish(Instant.now(clock))) {
            case NONE -> { }
            case FIRST_PUBLISH -> {
                list(planner);
                domainEventRecorder.recordDomainEvent(DomainEventType.PLANNER_PUBLISHED, planner.getId(),
                        Map.of("authorId", planner.getUserId()));
                logPublished(planner);
            }
            case REPUBLISH, WITHDRAWN -> {
                list(planner);
                logPublished(planner);
            }
        }
        return PlannerResponse.fromEntity(planner);
    }

    private enum ParsedContent {
        AS_STORED,
        MIGRATED;

        static ParsedContent of(PlannerDocument document) {
            return document.migrated() ? MIGRATED : AS_STORED;
        }
    }

    private void list(Planner planner) {
        plannerCatalogService.onBecameVisible(planner);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(planner.getId()), new FreshnessScope.Global());
        subscriptionService.createSubscription(planner.getUserId(), planner.getId());
    }

    private static void logPublished(Planner planner) {
        log.info("Published planner {} by user {}", planner.getId(), planner.getUserId());
    }

    private PlannerResponse applyUnpublish(OwnedPlanner owned) {
        Planner planner = owned.planner();
        switch (planner.unpublish()) {
            case NONE -> { }
            case FIRST_PUBLISH, REPUBLISH, WITHDRAWN -> {
                plannerCatalogService.onBecameInvisible(planner.getId());
                FreshnessDeclarations.declare(new FreshnessScope.Planner(planner.getId()),
                        new FreshnessScope.Global());
                log.info("Unpublished planner {} by user {}", planner.getId(), planner.getUserId());
            }
        }
        return PlannerResponse.fromEntity(planner);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Planner withdrawFromPublicView(UUID plannerId, Withdrawal withdrawal) {
        plannerRepository.lockContent(plannerId);
        Planner planner = accessGuard.requireAggregate(plannerId);

        if (!withdrawal.apply(planner).changed()) {
            return planner;
        }

        plannerCatalogService.onBecameInvisible(plannerId);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId), new FreshnessScope.Global());
        return planner;
    }

    @Transactional
    public Planner changeRecommendedListing(UUID plannerId, Consumer<Planner> change) {
        Planner planner = accessGuard.requireAggregate(plannerId);

        change.accept(planner);
        plannerCatalogService.refreshRecommended(plannerId);
        FreshnessDeclarations.declare(new FreshnessScope.Global());
        return planner;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public int upvoteCount(UUID plannerId) {
        return plannerStatsRepository.upvotesOf(plannerId);
    }


    @Transactional
    public ToggleOwnerNotificationsResponse toggleOwnerNotifications(Long userId, UUID plannerId, boolean enabled) {
        OwnedPlanner owned = ownershipValidator.requireOwner(accessGuard.requireAggregate(plannerId), userId);

        owned.planner().setOwnerNotificationsEnabled(enabled);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId));

        log.info("User {} toggled owner notifications for planner {} to {}", userId, plannerId, enabled);
        return new ToggleOwnerNotificationsResponse(enabled);
    }
}
