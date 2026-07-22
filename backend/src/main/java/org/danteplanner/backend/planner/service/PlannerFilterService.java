package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.PlannerEntityFilter;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.event.PlannerFilterRebuildEvent;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Maintains both search inverted indexes for a planner: content entities
 * ({@code planner_entity_filter}) and keywords ({@code planner_keyword_filter}).
 * Rows exist only while the planner is visible. Writers request maintenance via
 * {@link #requestRebuild}/{@link #requestClear}: the multi-statement index work
 * runs AFTER the owning transaction commits, in its own transaction, keeping
 * the cross-region write path short.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerFilterService {

    private final PlannerEntityFilterRepository entityFilterRepository;
    private final PlannerKeywordFilterRepository keywordFilterRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Request a post-commit rebuild of both filter indexes from the given
     * content snapshot. Call from the owning transaction.
     */
    public void requestRebuild(UUID plannerId, String contentJson, Set<String> selectedKeywords) {
        eventPublisher.publishEvent(PlannerFilterRebuildEvent.rebuild(plannerId, contentJson, selectedKeywords));
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
        rebuildFilters(event.plannerId(), event.contentJson(), event.selectedKeywords());
    }

    /**
     * Rebuild both filter tables for a planner from its content JSON and keyword set.
     * Must run within an existing transaction (caller provides @Transactional).
     */
    @Transactional
    public void rebuildFilters(UUID plannerId, String contentJson, Set<String> selectedKeywords) {
        entityFilterRepository.deleteByPlannerId(plannerId);
        keywordFilterRepository.deleteByPlannerId(plannerId);

        if (contentJson != null && !contentJson.isBlank()) {
            JsonNode root;
            try {
                root = objectMapper.readTree(contentJson);
            } catch (Exception e) {
                log.error("Failed to parse content JSON for planner {}: {}", plannerId, e.getMessage());
                root = null;
            }
            if (root != null) {
                List<PlannerEntityFilter> entries = PlannerContentEntityExtractor.extract(root).stream()
                        .map(ref -> new PlannerEntityFilter(ref.type(), ref.id(), plannerId))
                        .toList();
                if (!entries.isEmpty()) {
                    entityFilterRepository.saveAll(entries);
                }
            }
        }

        if (selectedKeywords != null && !selectedKeywords.isEmpty()) {
            List<PlannerKeywordFilter> keywords = selectedKeywords.stream()
                    .map(k -> new PlannerKeywordFilter(k, plannerId))
                    .toList();
            keywordFilterRepository.saveAll(keywords);
        }
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
