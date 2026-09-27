package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.RecommendedSql;
import org.danteplanner.backend.planner.validation.JsonDocuments;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Single choke point for the catalog projection.
 */
@Service
@Slf4j
public class PlannerCatalogService {

    private final PlannerCatalogRepository catalogRepository;
    private final PlannerStatsRepository statsRepository;
    private final PlannerFilterService filterService;
    private final ObjectMapper objectMapper;
    private final ContentTombstoneStore tombstoneStore;
    private final int recommendedThreshold;

    public PlannerCatalogService(
            PlannerCatalogRepository catalogRepository,
            PlannerStatsRepository statsRepository,
            PlannerFilterService filterService,
            ObjectMapper objectMapper,
            ContentTombstoneStore tombstoneStore,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.catalogRepository = catalogRepository;
        this.statsRepository = statsRepository;
        this.filterService = filterService;
        this.objectMapper = objectMapper;
        this.tombstoneStore = tombstoneStore;
        this.recommendedThreshold = recommendedThreshold;
    }

    @Transactional
    public void onBecameVisible(Planner planner) {
        add(planner);
        filterService.requestRebuild(planner.getId());
        UUID plannerId = planner.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tombstoneStore.clearTombstone(ByIdReadGuard.PUBLISHED_PLANNER_SCOPE, plannerId);
            }
        });
    }

    @Transactional
    public void onBecameInvisible(UUID plannerId) {
        remove(plannerId);
        filterService.requestClear(plannerId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tombstoneStore.writeTombstone(ByIdReadGuard.PUBLISHED_PLANNER_SCOPE, plannerId);
            }
        });
    }

    @Transactional
    public void hideAllOwnedBy(Long userId) {
        int withdrawn = catalogRepository.withdrawAllOwnedBy(userId);
        log.debug("Withdrew {} catalog rows owned by user {}", withdrawn, userId);
    }

    @Transactional
    public void restoreAllOwnedBy(Long userId) {
        int restored = catalogRepository.restoreAllOwnedBy(userId, recommendedThreshold);
        log.debug("Restored {} catalog rows owned by user {}", restored, userId);
    }

    @Transactional
    public void onVisibleEditCommitted(Planner planner) {
        syncScalarCopy(planner);
        if (searchableCompositionChanged(planner)) {
            filterService.requestRebuild(planner.getId());
        }
    }

    private boolean searchableCompositionChanged(Planner planner) {
        return !sameDocument(planner.getContentJson(), planner.getLoadedContentJson())
                || !orEmpty(planner.getSelectedKeywords()).equals(orEmpty(planner.getLoadedKeywords()))
                || !Objects.equals(planner.getCategory(), planner.getLoadedCategory());
    }

    private boolean sameDocument(String current, String loaded) {
        if (current == null || loaded == null) {
            return Objects.equals(current, loaded);
        }
        try {
            return JsonDocuments.sameDocument(objectMapper.readTree(current), objectMapper.readTree(loaded));
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static Set<String> orEmpty(Set<String> keywords) {
        return keywords != null ? keywords : Set.of();
    }

    @Transactional
    public void add(Planner planner) {
        boolean recommended = RecommendedSql.isRecommended(
                statsRepository.upvotesOf(planner.getId()),
                planner.isHiddenFromRecommended(),
                recommendedThreshold);
        catalogRepository.insert(PlannerCatalog.builder()
                .plannerId(planner.getId())
                .plannerType(planner.getPlannerType())
                .category(planner.getCategory())
                .title(planner.getTitle())
                .selectedKeywords(planner.getSelectedKeywords())
                .firstPublishedAt(planner.getFirstPublishedAt())
                .recommended(recommended)
                .build());
        log.debug("Catalog row added for planner {}", planner.getId());
    }

    @Transactional
    public void syncScalarCopy(Planner planner) {
        catalogRepository.findById(planner.getId()).ifPresent(row -> {
            row.setTitle(planner.getTitle());
            row.setCategory(planner.getCategory());
            row.setSelectedKeywords(planner.getSelectedKeywords());
        });
    }

    @Transactional
    public void remove(UUID plannerId) {
        catalogRepository.deleteById(plannerId);
        log.debug("Catalog row removed for planner {}", plannerId);
    }

    @Transactional
    public void refreshRecommended(UUID plannerId) {
        catalogRepository.refreshRecommended(plannerId, recommendedThreshold);
    }
}
