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

import java.util.Objects;
import java.util.Set;
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
        filterService.requestRebuild(planner.getId());
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
     * Withdraw every listing a user owns, leaving the planners themselves published.
     *
     * <p>Publication state is untouched, so {@link #restoreAllOwnedBy(Long)} puts the same set
     * back without the owner republishing. The filter indexes are left alone: search is rooted at
     * the catalog, so a withdrawn planner is unreachable through them.</p>
     *
     * @param userId the owning user
     */
    @Transactional
    public void hideAllOwnedBy(Long userId) {
        int withdrawn = catalogRepository.withdrawAllOwnedBy(userId);
        log.debug("Withdrew {} catalog rows owned by user {}", withdrawn, userId);
    }

    /**
     * Re-list every planner a user owns that is still published and not taken down.
     *
     * @param userId the owning user
     */
    @Transactional
    public void restoreAllOwnedBy(Long userId) {
        int restored = catalogRepository.restoreAllOwnedBy(userId, recommendedThreshold);
        log.debug("Restored {} catalog rows owned by user {}", restored, userId);
    }

    /**
     * A visible planner was edited by its owner: synchronize the catalog scalar
     * copies (read-your-writes for the list) and rebuild the filter indexes only
     * when the searchable composition changed.
     */
    @Transactional
    public void onVisibleEditCommitted(Planner planner) {
        syncScalarCopy(planner);
        if (searchableCompositionChanged(planner)) {
            filterService.requestRebuild(planner.getId());
        }
    }

    /**
     * Whether the edit moved anything the filter indexes are extracted from: the
     * content document or the keyword set.
     *
     * <p>Both are read off the in-memory aggregate against the values it carried at
     * load. Re-reading the stored column instead would answer "changed" every time,
     * because MySQL re-serializes a JSON column and hands back a different string
     * than the one written.</p>
     */
    private boolean searchableCompositionChanged(Planner planner) {
        return !Objects.equals(planner.getContentJson(), planner.getLoadedContentJson())
                || !orEmpty(planner.getSelectedKeywords()).equals(orEmpty(planner.getLoadedKeywords()));
    }

    private static Set<String> orEmpty(Set<String> keywords) {
        return keywords != null ? keywords : Set.of();
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
