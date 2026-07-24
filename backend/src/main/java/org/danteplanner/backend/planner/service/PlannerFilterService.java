package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.event.PlannerFilterRebuildEvent;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Maintains both search inverted indexes for a planner: content entities
 * ({@code planner_entity_filter}) and keywords ({@code planner_keyword_filter}).
 * Rows exist only while the planner is visible. Writers request maintenance via
 * {@link #requestRebuild}/{@link #requestClear}: the index work runs AFTER the
 * owning transaction commits, in its own transaction, keeping the cross-region
 * write path short. The rebuild is a single server-side procedure call
 * (migration V053) that clears and re-extracts from the committed content,
 * guarded by planner visibility so a stale rebuild cannot resurrect rows for a
 * just-unpublished planner.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerFilterService {

    private final PlannerEntityFilterRepository entityFilterRepository;
    private final PlannerKeywordFilterRepository keywordFilterRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Request a post-commit rebuild of both filter indexes from the planner's
     * committed content. Call from the owning transaction.
     */
    public void requestRebuild(UUID plannerId) {
        eventPublisher.publishEvent(PlannerFilterRebuildEvent.rebuild(plannerId));
    }

    /**
     * Request a post-commit clear of both filter indexes
     * (unpublish/delete/takedown). Call from the owning transaction.
     */
    public void requestClear(UUID plannerId) {
        eventPublisher.publishEvent(PlannerFilterRebuildEvent.clear(plannerId));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFilterRebuildRequested(PlannerFilterRebuildEvent event) {
        if (event.clear()) {
            clearFilters(event.plannerId());
        } else {
            rebuildFilters(event.plannerId());
        }
    }

    /**
     * Rebuild both filter tables from the planner's committed content and
     * keyword set, in one server-side procedure call.
     * Must run within an existing transaction (caller provides @Transactional).
     */
    @Transactional
    public void rebuildFilters(UUID plannerId) {
        entityFilterRepository.rebuildPlannerFilters(plannerId);
    }

    /**
     * Remove all filter rows for a planner.
     * Called on unpublish, soft-delete, and takedown.
     */
    @Transactional
    public void clearFilters(UUID plannerId) {
        entityFilterRepository.deleteByPlannerId(plannerId);
        keywordFilterRepository.deleteByPlannerId(plannerId);
        log.debug("Cleared filter rows for planner {}", plannerId);
    }
}
