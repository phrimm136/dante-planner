package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerEntityFilter;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.validation.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.service.PlannerFilterService;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Owner write commands over their committed projections: an edit that changes the
 * searchable composition rebuilds both filter indexes while an edit that does not
 * leaves them alone, every visible edit republishes the catalog's scalar copies,
 * and a soft delete withdraws the planner from the owner load path, the catalog,
 * and both filter indexes at once.
 *
 * <p>The rebuild and the clear run in an {@code AFTER_COMMIT} listener, so the
 * command is driven without a surrounding test transaction; the listener has
 * already committed by the time the command returns.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerCommandFlowIT extends SharedMySqlContainerSupport {





    /**
     * A theme-pack id no fixture content yields. Planted in the entity index, it survives
     * exactly while no rebuild runs, which is what makes the skipped rebuild observable as
     * state rather than as a call.
     */
    private static final int UNREACHABLE_ENTITY_ID = 424242;

    private static final String PLANTED_ROW = ContentEntityType.THEME_PACK + ":" + UNREACHABLE_ENTITY_ID;

    private static final String SIX_FLOOR_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]}",
            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]},"
                    + "{\"themePackId\":\"1006\",\"difficulty\":0,\"giftIds\":[\"9004\"]}");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerContentRepository contentRepository;

    @Autowired
    private PlannerPublicationRepository publicationRepository;

    @Autowired
    private PlannerCatalogRepository catalogRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerEntityFilterRepository entityFilterRepository;

    @Autowired
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Autowired
    private PlannerCommandService commandService;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerFilterService filterService;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "command-flow-owner@example.com");
        deviceId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
    }

    private Planner publishedPlanner(String title, Set<String> keywords) {
        Planner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(keywords)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        filterService.rebuildFilters(planner.getId());
        // MySQL re-serializes a JSON column, so "the content did not change" is only
        // expressible against the stored form, not the string handed to the insert.
        return plannerRepository.findAggregateForOwner(planner.getId(), owner.getId()).orElseThrow();
    }

    private void plantUnreachableEntityRow(UUID plannerId) {
        entityFilterRepository.save(
                new PlannerEntityFilter(ContentEntityType.THEME_PACK, UNREACHABLE_ENTITY_ID, plannerId));
    }

    private Set<String> entityIndex(UUID plannerId) {
        return entityFilterRepository.findAll().stream()
                .filter(row -> row.getPlannerId().equals(plannerId))
                .map(row -> row.getEntityType() + ":" + row.getEntityId())
                .collect(Collectors.toSet());
    }

    private Set<String> keywordIndex(UUID plannerId) {
        return keywordFilterRepository.findAll().stream()
                .filter(row -> row.getPlannerId().equals(plannerId))
                .map(PlannerKeywordFilter::getKeyword)
                .collect(Collectors.toSet());
    }

    private Set<String> extractorOracle(Planner planner) throws Exception {
        return PlannerContentEntityExtractor.extract(objectMapper.readTree(planner.getContentJson()),
                        MDCategory.fromValue(planner.getCategory())).stream()
                .map(ref -> ref.type() + ":" + ref.id())
                .collect(Collectors.toSet());
    }

    private UpsertPlannerRequest edit(Planner planner, String title, Set<String> keywords) {
        return new UpsertPlannerRequest(
                planner.getId().toString(),
                planner.getCategory(),
                title,
                PlannerStatus.SAVED,
                keywords == null ? planner.getContentJson()
                        : TestDataFactory.withSelectedKeywords(planner.getContentJson(), keywords),
                planner.getContentVersion(),
                PlannerType.MIRROR_DUNGEON,
                planner.getSyncVersion(),
                keywords);
    }

    @Test
    @DisplayName("an edit that leaves the searchable composition alone syncs the catalog copies and never touches the filter index")
    void edit_WhenCompositionUnchanged_LeavesFilterIndexUntouched() {
        Planner planner = publishedPlanner("Before Edit", Set.of("Sinking", "Combustion"));
        UUID plannerId = planner.getId();
        plantUnreachableEntityRow(plannerId);
        Set<String> entityIndexBefore = entityIndex(plannerId);

        commandService.upsertPlanner(owner.getId(), deviceId, plannerId,
                edit(planner, "After Edit", null), false);

        PlannerCatalog catalogRow = catalogRepository.findById(plannerId).orElseThrow();
        assertThat(catalogRow.getTitle())
                .as("the catalog scalar copy carries the new title")
                .isEqualTo("After Edit");
        assertThat(catalogRow.getSelectedKeywords())
                .containsExactlyInAnyOrder("Sinking", "Combustion");

        assertThat(entityIndex(plannerId))
                .as("no rebuild ran: a rebuild clears every row for the planner, "
                        + "so the planted row would be gone")
                .contains(PLANTED_ROW)
                .isEqualTo(entityIndexBefore);
        assertThat(keywordIndex(plannerId))
                .containsExactlyInAnyOrder("Sinking", "Combustion");
    }

    @Test
    @DisplayName("an edit that changes the searchable composition rebuilds both filter indexes from the committed row")
    void edit_WhenCompositionChanged_RebuildsBothFilterIndexes() throws Exception {
        Planner planner = publishedPlanner("Keyword Edit", Set.of("Sinking"));
        UUID plannerId = planner.getId();
        plantUnreachableEntityRow(plannerId);

        // Re-read: publishing fires AFTER_COMMIT listeners that bump this row's version, so the
        // instance returned by publishedPlanner() is already stale by the time the edit is built.
        planner = plannerRepository.findById(plannerId).orElseThrow();
        commandService.upsertPlanner(owner.getId(), deviceId, plannerId,
                edit(planner, "Keyword Edit", Set.of("Combustion", "Burst")), false);

        assertThat(keywordIndex(plannerId))
                .as("the keyword index is re-extracted from the committed keyword set")
                .containsExactlyInAnyOrder("Combustion", "Burst");
        assertThat(entityIndex(plannerId))
                .as("the entity index is re-extracted from the committed content, "
                        + "dropping the planted row the content does not yield")
                .doesNotContain(PLANTED_ROW)
                .isEqualTo(extractorOracle(planner));
        assertThat(catalogRepository.findById(plannerId).orElseThrow().getSelectedKeywords())
                .as("the catalog scalar copy carries the normalized keyword set")
                .containsExactlyInAnyOrder("Combustion", "Burst");
    }

    @Test
    @DisplayName("a soft delete persists the deletion stamp and withdraws the planner from the owner path, the catalog, and both filter indexes")
    void softDelete_WhenApplied_WithdrawsPlannerFromEveryProjection() {
        Planner planner = publishedPlanner("Doomed", Set.of("Sinking"));
        UUID plannerId = planner.getId();
        assertThat(entityIndex(plannerId)).isNotEmpty();
        assertThat(keywordIndex(plannerId)).isNotEmpty();

        commandService.deletePlanner(owner.getId(), plannerId);

        assertThat(contentRepository.findById(plannerId).orElseThrow().getDeletedAt())
                .as("the deletion stamp is on the committed row, not only on the in-memory aggregate")
                .isNotNull();
        assertThat(publicationRepository.findById(plannerId).orElseThrow().isPublished())
                .as("the auto-unpublish that precedes the delete is committed too")
                .isFalse();
        assertThat(plannerRepository.findAggregateForOwner(plannerId, owner.getId()))
                .as("the owner load path no longer returns it")
                .isEmpty();
        assertThat(plannerRepository.countActiveByUserId(owner.getId()))
                .as("it stops counting against the per-user limit")
                .isZero();
        assertThat(catalogRepository.existsById(plannerId))
                .as("the browse projection drops it")
                .isFalse();
        assertThat(entityIndex(plannerId)).isEmpty();
        assertThat(keywordIndex(plannerId)).isEmpty();
    }

    @Test
    void edit_WhenAFiveFloorPlannerSavesASixthFloor_IndexesOnlyTheRenderedFloors() throws Exception {
        UUID plannerId = publishedPlanner("Hidden Floor", Set.of("Sinking")).getId();
        Planner planner = plannerRepository.findById(plannerId).orElseThrow();

        commandService.upsertPlanner(owner.getId(), deviceId, plannerId, new UpsertPlannerRequest(
                plannerId.toString(), "5F", planner.getTitle(), PlannerStatus.SAVED, SIX_FLOOR_CONTENT,
                planner.getContentVersion(), PlannerType.MIRROR_DUNGEON, planner.getSyncVersion(), null), false);

        Planner saved = plannerRepository.findById(plannerId).orElseThrow();
        assertThat(saved.getContentJson()).contains("1006");
        assertThat(entityIndex(plannerId))
                .doesNotContain("THEME_PACK:1006", "EGO_GIFT:9004")
                .contains("THEME_PACK:1001", "THEME_PACK:1002", "THEME_PACK:1003", "THEME_PACK:1004",
                        "THEME_PACK:1005", "EGO_GIFT:9002")
                .isEqualTo(extractorOracle(saved));
    }

    private static final String FIFTEEN_FLOOR_CONTENT = TestDataFactory.VALID_CONTENT.replaceFirst(
            "\"floorSelections\":\\[.*?]}\\]",
            IntStream.range(0, 15)
                    .mapToObj(floor -> "{\"themePackId\":\"" + (1001 + floor) + "\",\"difficulty\":"
                            + (floor < 10 ? 1 : 3) + ",\"giftIds\":[\"" + (9101 + floor) + "\"]}")
                    .collect(Collectors.joining(",", "\"floorSelections\":[", "]")));

    private static final Set<String> FLOORS_SIX_TO_FIFTEEN = IntStream.range(5, 15)
            .boxed()
            .flatMap(floor -> Stream.of("THEME_PACK:" + (1001 + floor), "EGO_GIFT:" + (9101 + floor)))
            .collect(Collectors.toSet());

    private Planner fifteenFloorPlanner(String category) {
        Planner planner = TestDataFactory.planner(owner)
                .title("Category Switch " + category)
                .category(category)
                .content(FIFTEEN_FLOOR_CONTENT)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        filterService.rebuildFilters(planner.getId());
        return plannerRepository.findById(planner.getId()).orElseThrow();
    }

    private void switchCategory(Planner planner, String category) {
        commandService.upsertPlanner(owner.getId(), deviceId, planner.getId(), new UpsertPlannerRequest(
                planner.getId().toString(), category, planner.getTitle(), PlannerStatus.SAVED,
                planner.getContentJson(), planner.getContentVersion(), PlannerType.MIRROR_DUNGEON,
                planner.getSyncVersion(), null), false);
    }

    @Test
    void edit_WhenAFifteenFloorPlannerSwitchesToFiveFloorsWithIdenticalContent_DropsFloorsSixToFifteen()
            throws Exception {
        Planner planner = fifteenFloorPlanner("15F");
        assertThat(entityIndex(planner.getId())).containsAll(FLOORS_SIX_TO_FIFTEEN);

        switchCategory(planner, "5F");

        Planner saved = plannerRepository.findById(planner.getId()).orElseThrow();
        assertThat(saved.getCategory()).isEqualTo("5F");
        assertThat(entityIndex(planner.getId()))
                .doesNotContainAnyElementsOf(FLOORS_SIX_TO_FIFTEEN)
                .contains("THEME_PACK:1001", "THEME_PACK:1005", "EGO_GIFT:9101", "EGO_GIFT:9105")
                .isEqualTo(extractorOracle(saved));
    }

    @Test
    void edit_WhenAFiveFloorPlannerSwitchesToFifteenFloorsWithIdenticalContent_IndexesFloorsSixToFifteen()
            throws Exception {
        Planner planner = fifteenFloorPlanner("5F");
        assertThat(entityIndex(planner.getId())).doesNotContainAnyElementsOf(FLOORS_SIX_TO_FIFTEEN);

        switchCategory(planner, "15F");

        Planner saved = plannerRepository.findById(planner.getId()).orElseThrow();
        assertThat(saved.getCategory()).isEqualTo("15F");
        assertThat(entityIndex(planner.getId()))
                .containsAll(FLOORS_SIX_TO_FIFTEEN)
                .isEqualTo(extractorOracle(saved));
    }
}
