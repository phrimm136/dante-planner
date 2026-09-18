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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerFilterService {

    private final PlannerEntityFilterRepository entityFilterRepository;
    private final PlannerKeywordFilterRepository keywordFilterRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void requestRebuild(UUID plannerId) {
        eventPublisher.publishEvent(PlannerFilterRebuildEvent.rebuild(plannerId));
    }

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

    @Transactional
    public void rebuildFilters(UUID plannerId) {
        entityFilterRepository.rebuildPlannerFilters(plannerId);
    }

    @Transactional
    public void clearFilters(UUID plannerId) {
        entityFilterRepository.deleteByPlannerId(plannerId);
        keywordFilterRepository.deleteByPlannerId(plannerId);
        log.debug("Cleared filter rows for planner {}", plannerId);
    }
}
