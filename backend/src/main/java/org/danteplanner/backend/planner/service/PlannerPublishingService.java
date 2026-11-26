package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Listing;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.PublishOutcome;
import org.danteplanner.backend.planner.entity.WithdrawalOutcome;
import org.danteplanner.backend.planner.gamedata.Stage;
import org.danteplanner.backend.planner.repository.PlannerModerationRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.sync.SyncVersionValidator.WriteMode;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.PlannerDocument;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator.LiveOwner;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator.OwnedPlanner;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator.Publishable;
import org.danteplanner.backend.shared.dto.NotificationSetting;
import org.danteplanner.backend.shared.failure.WriteOutcome;
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

/**
 * Service for the publish lifecycle of a planner.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerPublishingService {

    private final PlannerRepository plannerRepository;
    private final PlannerStatsRepository plannerStatsRepository;
    private final PlannerPublicationRepository publicationRepository;
    private final PlannerModerationRepository moderationRepository;
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
        SaveOutcome saved = plannerCommandService.upsertAggregate(userId, deviceId, plannerId, content,
                WriteMode.CHECKED);

        return applyPublish(ownershipValidator.requireOwner(saved.planner(), userId));
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

    private PlannerResponse applyPublish(OwnedPlanner owned) {
        Publishable publishable = publishValidator.requirePublishable(owned.planner());
        MDCategory category;
        try {
            category = categoryValidator.requireCategoryForType(owned.planner().plannerType(),
                    owned.planner().content().category());
        } catch (PlannerValidationException refusal) {
            InvalidCategoryLog.warn(log, refusal);
            throw refusal;
        }
        PlannerDocument document = contentValidator.parse(owned.planner().content().json(), category,
                contentValidator.season(owned.planner().content().gameContentVersion()), Stage.PUBLISH);
        Instant now = clock.instant();
        StoredPlanner planner = switch (ParsedContent.of(document)) {
            case MIGRATED -> plannerCommandService.migrate(owned.planner(), document, now);
            case AS_STORED -> owned.planner();
        };

        Publication before = publishable.publication();
        PublishOutcome outcome = publish(planner.id(), before, now);
        switch (outcome) {
            case NONE -> { }
            case FIRST_PUBLISH -> {
                list(planner, publishable, before.published(now));
                domainEventRecorder.recordDomainEvent(DomainEventType.PLANNER_PUBLISHED, planner.id(),
                        Map.of("authorId", planner.userId()));
                logPublished(planner);
            }
            case REPUBLISH -> {
                list(planner, publishable, before.published(now));
                logPublished(planner);
            }
        }
        return PlannerResponse.from(planner, outcome.after(before, now));
    }

    private PublishOutcome publish(UUID plannerId, Publication before, Instant now) {
        return switch (before) {
            case Publication.Draft draft -> PublishOutcome.FIRST_PUBLISH.when(
                    WriteOutcome.of(publicationRepository.publishFirst(plannerId, now)));
            case Publication.Unpublished unpublished -> PublishOutcome.REPUBLISH.when(
                    WriteOutcome.of(publicationRepository.republish(plannerId)));
            case Publication.Published published -> PublishOutcome.NONE;
        };
    }

    private enum ParsedContent {
        AS_STORED,
        MIGRATED;

        static ParsedContent of(PlannerDocument document) {
            return document.migrated() ? MIGRATED : AS_STORED;
        }
    }

    private void list(StoredPlanner planner, Publishable publishable, Publication.Published publication) {
        plannerCatalogService.onBecameVisible(planner, publishable, publication);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(planner.id()), new FreshnessScope.Global());
        subscriptionService.createSubscription(planner.userId(), planner.id());
    }

    private static void logPublished(StoredPlanner planner) {
        log.info("Published planner {} by user {}", planner.id(), planner.userId());
    }

    private PlannerResponse applyUnpublish(OwnedPlanner owned) {
        StoredPlanner planner = owned.planner();
        switch (plannerCommandService.withdraw(planner)) {
            case NONE -> { }
            case WITHDRAWN -> {
                plannerCatalogService.onBecameInvisible(planner.id());
                FreshnessDeclarations.declare(new FreshnessScope.Planner(planner.id()),
                        new FreshnessScope.Global());
                log.info("Unpublished planner {} by user {}", planner.id(), planner.userId());
            }
        }
        return PlannerResponse.from(planner, planner.publication().withdrawn());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ModeratedPlanner withdrawFromPublicView(UUID plannerId, ModeratorAction.Withdrawal action) {
        plannerRepository.lockContent(plannerId);
        StoredPlanner planner = accessGuard.requireAggregate(plannerId);

        switch (withdraw(planner, action)) {
            case NONE -> { }
            case WITHDRAWN -> {
                plannerCatalogService.onBecameInvisible(plannerId);
                FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId), new FreshnessScope.Global());
            }
        }
        return moderated(planner, planner.publication().withdrawn(), planner.listing());
    }

    private WithdrawalOutcome withdraw(StoredPlanner planner, ModeratorAction.Withdrawal action) {
        return switch (action) {
            case ModeratorAction.TakeDown takeDown -> WithdrawalOutcome.of(
                            WriteOutcome.of(moderationRepository.takeDown(planner.id(), clock.instant())))
                    .or(plannerCommandService.withdraw(planner));
            case ModeratorAction.Unpublish unpublish -> plannerCommandService.withdraw(planner);
        };
    }

    @Transactional
    public ModeratedPlanner changeRecommendedListing(UUID plannerId, ModeratorAction.Relisting action) {
        StoredPlanner planner = accessGuard.requireAggregate(plannerId);

        Listing listing = action.listingAt(clock.instant());
        switch (relist(plannerId, listing)) {
            case APPLIED -> {
                plannerCatalogService.refreshRecommended(plannerId);
                FreshnessDeclarations.declare(new FreshnessScope.Global());
            }
            case NOTHING -> throw PlannerAccessGuard.absent(plannerId);
        }
        return moderated(planner, planner.publication(), listing);
    }

    private WriteOutcome relist(UUID plannerId, Listing listing) {
        return switch (listing) {
            case Listing.Listed listed -> WriteOutcome.of(moderationRepository.unhide(plannerId));
            case Listing.Hidden(Long by, String reason, Instant at) ->
                    WriteOutcome.of(moderationRepository.hide(plannerId, by, reason, at));
        };
    }

    private static ModeratedPlanner moderated(StoredPlanner planner, Publication publication, Listing listing) {
        return switch (listing) {
            case Listing.Listed listed -> new ModeratedPlanner(planner.id(), planner.content().title(),
                    publication.isPublished(), false, null, null, null);
            case Listing.Hidden(Long by, String reason, Instant at) -> new ModeratedPlanner(planner.id(),
                    planner.content().title(), publication.isPublished(), true, by, reason, at);
        };
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public int upvoteCount(UUID plannerId) {
        return plannerStatsRepository.upvotesOf(plannerId);
    }


    @Transactional
    public ToggleOwnerNotificationsResponse toggleOwnerNotifications(Long userId, UUID plannerId,
            NotificationSetting setting) {
        OwnedPlanner owned = ownershipValidator.requireOwner(accessGuard.requireAggregate(plannerId), userId);

        switch (WriteOutcome.of(publicationRepository.setOwnerNotifications(owned.planner().id(),
                setting == NotificationSetting.ON))) {
            case APPLIED -> FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId));
            case NOTHING -> throw PlannerAccessGuard.absent(plannerId);
        }

        log.info("User {} set owner notifications for planner {} to {}", userId, plannerId, setting);
        return ToggleOwnerNotificationsResponse.of(setting);
    }
}
