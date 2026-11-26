package org.danteplanner.backend.planner.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator.Publishable;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository.FloorScopeRow;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.RecommendedSql;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.StoredPlanner.StoredContent;
import org.danteplanner.backend.planner.sync.JsonDocuments;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.user.api.AccountLifecycleParticipant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Single choke point for the catalog projection.
 */
@Service
@Slf4j
public class PlannerCatalogService implements AccountLifecycleParticipant {

    private final PlannerCatalogRepository catalogRepository;
    private final PlannerRepository plannerRepository;
    private final PlannerStatsRepository statsRepository;
    private final PlannerEntityFilterRepository entityFilterRepository;
    private final PlannerKeywordFilterRepository keywordFilterRepository;
    private final FloorRules floorRules;
    private final ObjectMapper objectMapper;
    private final int recommendedThreshold;

    public PlannerCatalogService(
            PlannerCatalogRepository catalogRepository,
            PlannerRepository plannerRepository,
            PlannerStatsRepository statsRepository,
            PlannerEntityFilterRepository entityFilterRepository,
            PlannerKeywordFilterRepository keywordFilterRepository,
            FloorRules floorRules,
            ObjectMapper objectMapper,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.catalogRepository = catalogRepository;
        this.plannerRepository = plannerRepository;
        this.statsRepository = statsRepository;
        this.entityFilterRepository = entityFilterRepository;
        this.keywordFilterRepository = keywordFilterRepository;
        this.floorRules = floorRules;
        this.objectMapper = objectMapper;
        this.recommendedThreshold = recommendedThreshold;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onBecameVisible(StoredPlanner planner, Publishable publishable, Publication.Published publication) {
        add(planner, publishable, publication);
        syncFilterRows(planner.id());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onBecameInvisible(UUID plannerId) {
        remove(plannerId);
        syncFilterRows(plannerId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void accountDeactivated(Long userId) {
        plannerRepository.lockContentOwnedBy(userId);
        List<UUID> publishedIds = plannerRepository.findPublishedIdsOwnedBy(userId);
        int withdrawn = catalogRepository.withdrawAllOwnedBy(userId);
        log.debug("Withdrew {} catalog rows owned by user {}", withdrawn, userId);
        publishedIds.forEach(this::syncFilterRows);
        FreshnessDeclarations.declare(new FreshnessScope.Global());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void accountReactivated(Long userId) {
        plannerRepository.lockContentOwnedBy(userId);
        List<UUID> publishedIds = plannerRepository.findPublishedIdsOwnedBy(userId);
        int restored = catalogRepository.restoreAllOwnedBy(userId, recommendedThreshold);
        log.debug("Restored {} catalog rows owned by user {}", restored, userId);
        publishedIds.forEach(this::syncFilterRows);
        FreshnessDeclarations.declare(new FreshnessScope.Global());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onVisibleEditCommitted(StoredPlanner before, StoredPlanner after) {
        StoredContent saved = after.content();
        catalogRepository.copyScalars(after.id(), saved.title(), saved.category(), saved.keywords());
        switch (SearchRows.between(before.content(), saved, objectMapper)) {
            case KEEP -> { }
            case REBUILD -> syncFilterRows(after.id());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void syncFilterRows(UUID plannerId) {
        Optional<FloorScopeRow> scope = entityFilterRepository.floorScopeOf(plannerId);
        if (plannerRepository.existsPublishedById(plannerId)) {
            entityFilterRepository.rebuildPlannerFilters(plannerId, scope.map(this::floorCountOf).orElse(0));
        } else {
            entityFilterRepository.deleteByPlannerId(plannerId);
            keywordFilterRepository.deleteByPlannerId(plannerId);
        }
    }

    private int floorCountOf(FloorScopeRow scope) {
        if (scope.getPlannerType() != PlannerType.MIRROR_DUNGEON) {
            return 0;
        }
        return floorRules.floorCount(MDCategory.fromValue(scope.getCategory()));
    }

    private enum SearchRows {
        KEEP,
        REBUILD;

        static SearchRows between(StoredContent before, StoredContent after, ObjectMapper mapper) {
            boolean unchanged = sameDocument(before.json(), after.json(), mapper)
                    && before.keywords().equals(after.keywords())
                    && before.category().equals(after.category());
            return unchanged ? KEEP : REBUILD;
        }

        private static boolean sameDocument(String before, String after, ObjectMapper mapper) {
            try {
                return JsonDocuments.sameDocument(mapper.readTree(before), mapper.readTree(after));
            } catch (JacksonException e) {
                return false;
            }
        }
    }

    @Transactional
    public void add(StoredPlanner planner, Publishable publishable, Publication.Published publication) {
        boolean recommended = RecommendedSql.isRecommended(
                statsRepository.upvotesOf(planner.id()),
                planner.listing(),
                recommendedThreshold);
        StoredContent content = planner.content();
        catalogRepository.insert(PlannerCatalog.builder()
                .plannerId(planner.id())
                .plannerType(planner.plannerType())
                .category(content.category())
                .title(publishable.title().value())
                .selectedKeywords(content.keywords())
                .firstPublishedAt(publication.firstAt())
                .recommended(recommended)
                .build());
        log.debug("Catalog row added for planner {}", planner.id());
    }

    @Transactional
    public void remove(UUID plannerId) {
        catalogRepository.deleteByPlannerId(plannerId);
        log.debug("Catalog row removed for planner {}", plannerId);
    }

    @Transactional
    public void refreshRecommended(UUID plannerId) {
        catalogRepository.refreshRecommended(plannerId, recommendedThreshold);
    }
}
