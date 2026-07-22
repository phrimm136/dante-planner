package org.danteplanner.backend.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single choke point for the catalog projection. Membership is visibility
 * (published AND not deleted AND not taken down): {@link #add} on publish,
 * {@link #remove} on unpublish/delete/takedown. Scalar copies stay synchronous
 * with the owning write so the public list reflects a title edit immediately;
 * the derived recommended flag is refreshed on vote-threshold crossings and
 * moderation hide/unhide.
 */
@Service
@Slf4j
public class PlannerCatalogService {

    private final PlannerCatalogRepository catalogRepository;
    private final PlannerStatsRepository statsRepository;
    private final PlannerFilterService filterService;
    private final int recommendedThreshold;

    public PlannerCatalogService(
            PlannerCatalogRepository catalogRepository,
            PlannerStatsRepository statsRepository,
            PlannerFilterService filterService,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.catalogRepository = catalogRepository;
        this.statsRepository = statsRepository;
        this.filterService = filterService;
        this.recommendedThreshold = recommendedThreshold;
    }

    /**
     * A planner became visible (publish): insert its catalog row and rebuild
     * both filter indexes after commit. The single entry point for the
     * became-visible transition, so no caller wires the pair by hand.
     */
    @Transactional
    public void onBecameVisible(Planner planner) {
        add(planner);
        filterService.requestRebuild(planner.getId(), planner.getContentJson(),
                planner.getSelectedKeywords());
    }

    /**
     * A planner became invisible (unpublish, delete, takedown): remove its
     * catalog row and clear both filter indexes after commit.
     */
    @Transactional
    public void onBecameInvisible(UUID plannerId) {
        remove(plannerId);
        filterService.requestClear(plannerId);
    }

    /**
     * A visible planner was edited by its owner: synchronize the catalog scalar
     * copies (read-your-writes for the list) and rebuild the filter indexes only
     * when the searchable composition changed.
     */
    @Transactional
    public void onVisibleEditCommitted(Planner planner, boolean compositionChanged) {
        syncScalarCopy(planner);
        if (compositionChanged) {
            filterService.requestRebuild(planner.getId(), planner.getContentJson(),
                    planner.getSelectedKeywords());
        }
    }

    /**
     * Insert the catalog row on publish. The recommended flag is computed from
     * current stats and moderation state (a republished planner keeps its votes).
     */
    @Transactional
    public void add(Planner planner) {
        boolean recommended = statsRepository.findById(planner.getId())
                .map(PlannerStats::getUpvotes)
                .orElse(0) >= recommendedThreshold
                && !Boolean.TRUE.equals(planner.getHiddenFromRecommended());
        catalogRepository.save(PlannerCatalog.builder()
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

    /**
     * Synchronize the scalar copies (title, category, keywords) after an owner
     * edit of a published planner. No-op while no catalog row exists.
     */
    @Transactional
    public void syncScalarCopy(Planner planner) {
        catalogRepository.findById(planner.getId()).ifPresent(row -> {
            row.setTitle(planner.getTitle());
            row.setCategory(planner.getCategory());
            row.setSelectedKeywords(planner.getSelectedKeywords());
            catalogRepository.save(row);
        });
    }

    /**
     * Remove the catalog row on unpublish, soft-delete, or takedown.
     */
    @Transactional
    public void remove(UUID plannerId) {
        catalogRepository.deleteById(plannerId);
        log.debug("Catalog row removed for planner {}", plannerId);
    }

    /**
     * Recompute the derived recommended flag (vote-threshold crossing,
     * moderation hide/unhide).
     */
    @Transactional
    public void refreshRecommended(UUID plannerId) {
        catalogRepository.refreshRecommended(plannerId, recommendedThreshold);
    }
}
