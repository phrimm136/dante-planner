package org.danteplanner.backend.integration;

import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerEntityFilter;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.searchindex.PlannerContentEntityExtractor;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.support.FilterRowsSync;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.planner.sync.SyncVersionValidator.WriteMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

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
    private PlannerContentEntityExtractor entityExtractor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

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
    private PlatformTransactionManager transactionManager;

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

    private StoredPlanner publishedPlanner(String title, Set<String> keywords) {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(keywords)
                .published(true)
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());
        TestDataFactory.addToCatalog(catalogService, planner);
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        // MySQL re-serializes a JSON column, so "the content did not change" is only
        // expressible against the stored form, not the string handed to the insert.
        return plannerRepository.findAggregateForOwner(planner.id(), owner.getId()).orElseThrow();
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

    private Set<String> extractorOracle(StoredPlanner planner) throws Exception {
        return entityExtractor.extract(objectMapper.readTree(planner.content().json()),
                        MDCategory.fromValue(planner.content().category())).stream()
                .map(ref -> ref.type() + ":" + ref.id())
                .collect(Collectors.toSet());
    }

    private UpsertPlannerRequest edit(StoredPlanner planner, String title, Set<String> keywords) {
        return UpsertRequests.resending(planner)
                .title(title)
                .status(PlannerStatus.SAVED)
                .content(keywords == null ? planner.content().json()
                        : TestDataFactory.withSelectedKeywords(planner.content().json(), keywords))
                .selectedKeywords(keywords)
                .build();
    }

    @Test
    void edit_WhenCompositionUnchanged_LeavesFilterIndexUntouched() {
        StoredPlanner planner = publishedPlanner("Before Edit", Set.of("Sinking", "Combustion"));
        UUID plannerId = planner.id();
        plantUnreachableEntityRow(plannerId);
        Set<String> entityIndexBefore = entityIndex(plannerId);

        commandService.upsertPlanner(owner.getId(), deviceId, plannerId,
                edit(planner, "After Edit", null), WriteMode.CHECKED);

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
    void edit_WhenCompositionChanged_RebuildsBothFilterIndexes() throws Exception {
        StoredPlanner planner = publishedPlanner("Keyword Edit", Set.of("Sinking"));
        UUID plannerId = planner.id();
        plantUnreachableEntityRow(plannerId);

        // Re-read: publishing fires AFTER_COMMIT listeners that bump this row's version, so the
        // instance returned by publishedPlanner() is already stale by the time the edit is built.
        planner = plannerRepository.findAggregate(plannerId).orElseThrow();
        commandService.upsertPlanner(owner.getId(), deviceId, plannerId,
                edit(planner, "Keyword Edit", Set.of("Combustion", "Burst")), WriteMode.CHECKED);

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
    void softDelete_WhenApplied_WithdrawsPlannerFromEveryProjection() {
        StoredPlanner planner = publishedPlanner("Doomed", Set.of("Sinking"));
        UUID plannerId = planner.id();
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
        UUID plannerId = publishedPlanner("Hidden Floor", Set.of("Sinking")).id();
        StoredPlanner planner = plannerRepository.findAggregate(plannerId).orElseThrow();

        commandService.upsertPlanner(owner.getId(), deviceId, plannerId, UpsertRequests.resending(planner)
                .status(PlannerStatus.SAVED)
                .content(SIX_FLOOR_CONTENT)
                .build(), WriteMode.CHECKED);

        StoredPlanner saved = plannerRepository.findAggregate(plannerId).orElseThrow();
        assertThat(saved.content().json()).contains("1006");
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

    private StoredPlanner fifteenFloorPlanner(String category) {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .title("Category Switch " + category)
                .category(category)
                .content(FIFTEEN_FLOOR_CONTENT)
                .published(true)
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());
        TestDataFactory.addToCatalog(catalogService, planner);
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        return plannerRepository.findAggregate(planner.id()).orElseThrow();
    }

    private void switchCategory(StoredPlanner planner, String category) {
        commandService.upsertPlanner(owner.getId(), deviceId, planner.id(), UpsertRequests.resending(planner)
                .category(category)
                .status(PlannerStatus.SAVED)
                .build(), WriteMode.CHECKED);
    }

    @Test
    void edit_WhenAFifteenFloorPlannerSwitchesToFiveFloorsWithIdenticalContent_DropsFloorsSixToFifteen()
            throws Exception {
        StoredPlanner planner = fifteenFloorPlanner("15F");
        assertThat(entityIndex(planner.id())).containsAll(FLOORS_SIX_TO_FIFTEEN);

        switchCategory(planner, "5F");

        StoredPlanner saved = plannerRepository.findAggregate(planner.id()).orElseThrow();
        assertThat(saved.content().category()).isEqualTo("5F");
        assertThat(entityIndex(planner.id()))
                .doesNotContainAnyElementsOf(FLOORS_SIX_TO_FIFTEEN)
                .contains("THEME_PACK:1001", "THEME_PACK:1005", "EGO_GIFT:9101", "EGO_GIFT:9105")
                .isEqualTo(extractorOracle(saved));
    }

    @Test
    void edit_WhenAFiveFloorPlannerSwitchesToFifteenFloorsWithIdenticalContent_IndexesFloorsSixToFifteen()
            throws Exception {
        StoredPlanner planner = fifteenFloorPlanner("5F");
        assertThat(entityIndex(planner.id())).doesNotContainAnyElementsOf(FLOORS_SIX_TO_FIFTEEN);

        switchCategory(planner, "15F");

        StoredPlanner saved = plannerRepository.findAggregate(planner.id()).orElseThrow();
        assertThat(saved.content().category()).isEqualTo("15F");
        assertThat(entityIndex(planner.id()))
                .containsAll(FLOORS_SIX_TO_FIFTEEN)
                .isEqualTo(extractorOracle(saved));
    }
}
