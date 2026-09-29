package org.danteplanner.backend.planner.service;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository.FloorScopeRow;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.outbox.config.OutboxConstants;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventAttemptRecorder;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.danteplanner.backend.shared.util.PlannerConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drains pending planner-catalog-changed events one planner at a time, deriving each planner's
 * filter rows, withdrawal marker and delete tombstone from its committed row.
 */
@Service
@Slf4j
public class PlannerCatalogSyncService {

    private final DomainEventRepository events;
    private final DomainEventAttemptRecorder attemptRecorder;
    private final PlannerRepository plannerRepository;
    private final PlannerEntityFilterRepository entityFilterRepository;
    private final PlannerKeywordFilterRepository keywordFilterRepository;
    private final GameDataRegistry gameDataRegistry;
    private final ContentTombstoneStore tombstoneStore;
    private final PlannerCatalogSyncService self;
    private final TransactionTemplate batchRead;
    private final Clock clock;

    @Autowired
    public PlannerCatalogSyncService(
            DomainEventRepository events,
            DomainEventAttemptRecorder attemptRecorder,
            PlannerRepository plannerRepository,
            PlannerEntityFilterRepository entityFilterRepository,
            PlannerKeywordFilterRepository keywordFilterRepository,
            GameDataRegistry gameDataRegistry,
            ContentTombstoneStore tombstoneStore,
            @Lazy PlannerCatalogSyncService self,
            PlatformTransactionManager transactionManager) {
        this(events, attemptRecorder, plannerRepository, entityFilterRepository, keywordFilterRepository,
                gameDataRegistry, tombstoneStore, self, transactionManager, Clock.systemUTC());
    }

    PlannerCatalogSyncService(
            DomainEventRepository events,
            DomainEventAttemptRecorder attemptRecorder,
            PlannerRepository plannerRepository,
            PlannerEntityFilterRepository entityFilterRepository,
            PlannerKeywordFilterRepository keywordFilterRepository,
            GameDataRegistry gameDataRegistry,
            ContentTombstoneStore tombstoneStore,
            PlannerCatalogSyncService self,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.events = events;
        this.attemptRecorder = attemptRecorder;
        this.plannerRepository = plannerRepository;
        this.entityFilterRepository = entityFilterRepository;
        this.keywordFilterRepository = keywordFilterRepository;
        this.gameDataRegistry = gameDataRegistry;
        this.tombstoneStore = tombstoneStore;
        this.self = self;
        this.batchRead = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public int drainPending() {
        Instant deadline = clock.instant().plus(PlannerConstants.CATALOG_SYNC_PASS_BUDGET);
        List<DomainEvent> pending = batchRead.execute(status -> events.pendingEventsOfType(
                DomainEventType.PLANNER_CATALOG_CHANGED, OutboxConstants.DISPATCH_ATTEMPT_CAP,
                PageRequest.of(0, PlannerConstants.CATALOG_SYNC_BATCH_SIZE)));
        Map<UUID, List<Long>> idsByPlanner = new LinkedHashMap<>();
        pending.forEach(event -> idsByPlanner
                .computeIfAbsent(event.getAggregateId(), plannerId -> new ArrayList<>())
                .add(event.getId()));

        int synced = 0;
        for (Map.Entry<UUID, List<Long>> planner : idsByPlanner.entrySet()) {
            if (clock.instant().isAfter(deadline)) {
                log.info("Catalog sync pass spent its budget; the remaining planners stay pending for the next pass");
                break;
            }
            List<Long> ids = planner.getValue();
            try {
                self.syncPlanner(planner.getKey(), ids.get(ids.size() - 1));
                synced++;
            } catch (RuntimeException e) {
                recordFailedSync(planner.getKey(), ids, e);
            }
        }
        return synced;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncPlanner(UUID plannerId, long throughEventId) {
        if (plannerRepository.existsPublishedById(plannerId)) {
            rebuildFilters(plannerId);
            tombstoneStore.clearTombstoneOrThrow(ByIdReadGuard.PUBLISHED_PLANNER_SCOPE, plannerId);
        } else {
            clearFilters(plannerId);
            tombstoneStore.writeTombstoneOrThrow(ByIdReadGuard.PUBLISHED_PLANNER_SCOPE, plannerId);
            if (isDeleted(plannerId)) {
                tombstoneStore.writeTombstoneOrThrow(ByIdReadGuard.PLANNER_ENTITY_TYPE, plannerId);
            }
        }
        events.markDispatchedThrough(plannerId, DomainEventType.PLANNER_CATALOG_CHANGED, throughEventId,
                Instant.now());
    }

    @Transactional
    public double backlogAgeSeconds() {
        return events.oldestPendingAgeSecondsOf(DomainEventType.PLANNER_CATALOG_CHANGED.name());
    }

    private void recordFailedSync(UUID plannerId, List<Long> ids, RuntimeException cause) {
        log.warn("Catalog sync of planner {} failed; its {} event(s) stay pending", plannerId, ids.size(), cause);
        for (Long id : ids) {
            if (attemptRecorder.recordAttempt(id) == OutboxConstants.DISPATCH_ATTEMPT_CAP) {
                log.error("Catalog event {} of planner {} spent its last attempt and will not be synced again",
                        id, plannerId, cause);
                Sentry.captureException(cause);
            }
        }
    }

    private boolean isDeleted(UUID plannerId) {
        return plannerRepository.findOwnershipById(plannerId)
                .map(row -> row.getDeletedAt() != null)
                .orElse(true);
    }

    private void rebuildFilters(UUID plannerId) {
        int floorCount = entityFilterRepository.floorScopeOf(plannerId).map(this::floorCountOf).orElse(0);
        entityFilterRepository.rebuildPlannerFilters(plannerId, floorCount);
    }

    private int floorCountOf(FloorScopeRow scope) {
        if (scope.getPlannerType() != PlannerType.MIRROR_DUNGEON) {
            return 0;
        }
        return gameDataRegistry.floorRules().floorCount(MDCategory.fromValue(scope.getCategory()));
    }

    private void clearFilters(UUID plannerId) {
        entityFilterRepository.deleteByPlannerId(plannerId);
        keywordFilterRepository.deleteByPlannerId(plannerId);
    }
}
