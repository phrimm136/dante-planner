package org.danteplanner.backend.integration;

import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.searchindex.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.FilterRowsSync;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Filter rebuild over existing rows: rebuilding a visible planner's filter
 * indexes while rows from a prior build are present must replace them in one
 * transaction, not collide on the composite primary key.
 */
class PlannerFilterRebuildIT extends ExclusiveDatabaseSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

    @Autowired
    private PlannerEntityFilterRepository entityFilterRepository;

    @Autowired
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlannerContentEntityExtractor extractor;

    @Autowired
    private GameDataRegistry gameDataRegistry;

    @Autowired
    private DataSource dataSource;

    private User owner;

    private static final String SIX_FLOOR_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]}",
            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]},"
                    + "{\"themePackId\":\"1006\",\"difficulty\":0,\"giftIds\":[\"9004\"]}");

    private static final String MALFORMED_OBJECT_FLOORS = """
            {"floorSelections":[
              {"themePackId":"1001","giftIds":["9001"]},
              7,
              {"themePackId":1003,"giftIds":["9003"]},
              {"themePackId":"1004","giftIds":["9002",9005]},
              {"giftIds":["9004"]}
            ]}
            """;

    private static final String FIFTEEN_FLOORS = IntStream.range(0, 15)
            .mapToObj(floor -> "{\"themePackId\":\"" + (1001 + floor) + "\",\"giftIds\":[\"" + (9001 + floor) + "\"]}")
            .collect(Collectors.joining(",", "{\"floorSelections\":[", "]}"));

    @BeforeEach
    void setUp() {
        // First statement of the only @BeforeEach: JUnit does not order sibling
        // @BeforeEach methods, so a separate wipe method could run after setup.
        emptyPlannerTables(new JdbcTemplate(dataSource));
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "rebuild-owner@example.com");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
    }

    @Test
    void rebuildFilters_WhenRowsAlreadyExist_ReplacesWithoutDuplicateKey() {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .selectedKeywords(Set.of("Sinking", "Combustion"))
                .published(true)
                .save(plannerSeeds);

        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        long entityRowsAfterFirstBuild = entityFilterRepository.count();
        assertThat(entityRowsAfterFirstBuild).isPositive();

        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        assertThat(entityFilterRepository.count()).isEqualTo(entityRowsAfterFirstBuild);
        assertThat(keywordFilterRepository.findAll())
                .extracting(PlannerKeywordFilter::getKeyword)
                .containsExactlyInAnyOrder("Sinking", "Combustion");
    }

    @Test
    void rebuildFilters_WhenAStoredKeywordDiffersFromTheContentOnlyInCase_KeepsExactlyTheContentsSpelling() {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .selectedKeywords(Set.of("Sinking"))
                .published(true)
                .save(plannerSeeds);
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        assertThat(storedKeywords(planner)).containsExactly("Sinking");

        new JdbcTemplate(dataSource).update(
                "UPDATE planner_content SET selected_keywords = '[\"sinking\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.id().toString());
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        assertThat(storedKeywords(planner))
                .as("the case-insensitive key holds one spelling, and the rebuild compares by binary collation")
                .containsExactly("sinking");
    }

    @Test
    void rebuildFilters_WhenTheContentDropsSomeKeysAndAddsOthers_LeavesExactlyTheNewSet() throws Exception {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .selectedKeywords(Set.of("Sinking", "Combustion"))
                .published(true)
                .save(plannerSeeds);
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        Set<String> before = indexedRows(planner);
        assertThat(before).contains("IDENTITY:10101", "EGO_GIFT:9001");

        new JdbcTemplate(dataSource).update("UPDATE planner_content SET content = ?, selected_keywords = ?"
                        + " WHERE planner_id = UUID_TO_BIN(?)",
                TestDataFactory.VALID_CONTENT
                        .replace("\"selectedGiftIds\":[\"9001\"]", "\"selectedGiftIds\":[\"9003\"]")
                        .replace("{\"id\":\"10101\"", "{\"id\":\"10102\""),
                "[\"Burst\", \"Combustion\"]", planner.id().toString());
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        StoredPlanner changed = plannerRepository.findAggregate(planner.id()).orElseThrow();
        assertThat(indexedRows(changed))
                .doesNotContain("IDENTITY:10101", "EGO_GIFT:9001")
                .contains("IDENTITY:10102", "EGO_GIFT:9003")
                .isEqualTo(oracleRows(changed));
        assertThat(storedKeywords(changed)).containsExactlyInAnyOrder("Burst", "Combustion");
    }

    private List<String> storedKeywords(StoredPlanner planner) {
        return keywordFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.id()))
                .map(PlannerKeywordFilter::getKeyword)
                .toList();
    }

    @Test
    void rebuildFilters_WhenFactoryContent_MatchesExtractorOracle() throws Exception {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .published(true)
                .save(plannerSeeds);

        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        Set<String> expected = extractor
                .extract(objectMapper.readTree(planner.content().json()), MDCategory.fromValue(planner.content().category()))
                .stream()
                .map(ref -> ref.type().name() + ":" + ref.id())
                .collect(Collectors.toSet());
        Set<String> actual = entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.id()))
                .map(f -> f.getEntityType().name() + ":" + f.getEntityId())
                .collect(Collectors.toSet());

        assertThat(expected).isNotEmpty();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rebuildFilters_WhenEnhancedGiftIds_IndexesBaseIdOnly() throws Exception {
        String content = """
                {"equipment":{},
                 "selectedGiftIds":["9154","19154","29154"],
                 "observationGiftIds":["19001"],
                 "comprehensiveGiftIds":[],
                 "floorSelections":[{"giftIds":["29002"],"themePackId":null}]}
                """;
        StoredPlanner planner = TestDataFactory.planner(owner)
                .published(true)
                .content(content)
                .save(plannerSeeds);

        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        Set<Integer> gifts = entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.id()))
                .filter(f -> f.getEntityType().name().equals("EGO_GIFT"))
                .map(f -> f.getEntityId())
                .collect(Collectors.toSet());

        // 9154/19154/29154 are one gift, so the three collapse to a single row.
        assertThat(gifts).containsExactlyInAnyOrder(9154, 9001, 9002);

        Set<String> oracle = extractor
                .extract(objectMapper.readTree(content), MDCategory.fromValue(planner.content().category()))
                .stream()
                .filter(ref -> ref.type().name().equals("EGO_GIFT"))
                .map(ref -> String.valueOf(ref.id()))
                .collect(Collectors.toSet());
        assertThat(oracle).isEqualTo(gifts.stream().map(String::valueOf).collect(Collectors.toSet()));
    }

    private Set<String> indexedRows(StoredPlanner planner) {
        return entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.id()))
                .map(f -> f.getEntityType().name() + ":" + f.getEntityId())
                .collect(Collectors.toSet());
    }

    private Set<String> oracleRows(StoredPlanner planner) throws Exception {
        return extractor
                .extract(objectMapper.readTree(planner.content().json()), MDCategory.fromValue(planner.content().category()))
                .stream()
                .map(ref -> ref.type().name() + ":" + ref.id())
                .collect(Collectors.toSet());
    }

    @Test
    void rebuildFilters_WhenAFiveFloorPlannerStoresASixthFloor_SkipsItsThemePackAndGifts() throws Exception {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .category("5F")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerSeeds);

        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        assertThat(indexedRows(planner))
                .doesNotContain("THEME_PACK:1006", "EGO_GIFT:9004")
                .contains("THEME_PACK:1001", "THEME_PACK:1002", "THEME_PACK:1003", "THEME_PACK:1004",
                        "THEME_PACK:1005", "EGO_GIFT:9002")
                .isEqualTo(oracleRows(planner));
    }

    @Test
    void rebuildFilters_WhenAFifteenFloorPlannerStoresTheSameSixthFloor_IndexesItsThemePackAndGifts() throws Exception {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .category("15F")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerSeeds);

        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        assertThat(indexedRows(planner))
                .contains("THEME_PACK:1006", "EGO_GIFT:9004")
                .isEqualTo(oracleRows(planner));
    }

    @Test
    void rebuildFilters_WhenEachCategoryStoresFifteenFloors_IndexesExactlyTheJavaFloorCount() throws Exception {
        for (MDCategory category : MDCategory.values()) {
            StoredPlanner planner = TestDataFactory.planner(owner)
                    .category(category.getValue())
                    .content(FIFTEEN_FLOORS)
                    .published(true)
                    .save(plannerSeeds);

            FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

            Set<String> rendered = IntStream.range(0, gameDataRegistry.floorRules().floorCount(category))
                    .boxed()
                    .flatMap(floor -> Stream.of(
                            "THEME_PACK:" + (1001 + floor), "EGO_GIFT:" + (9001 + floor)))
                    .collect(Collectors.toSet());
            assertThat(indexedRows(planner)).as(category.getValue())
                    .isEqualTo(rendered)
                    .isEqualTo(oracleRows(planner));
        }
    }

    private void callProcedure(StoredPlanner planner, int floorCount) {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> entityFilterRepository.rebuildPlannerFilters(planner.id(), floorCount));
    }

    private static Set<String> floorRows(int fromFloor, int toFloor) {
        return IntStream.range(fromFloor, toFloor)
                .boxed()
                .flatMap(floor -> Stream.of("THEME_PACK:" + (1001 + floor), "EGO_GIFT:" + (9001 + floor)))
                .collect(Collectors.toSet());
    }

    @Test
    void rebuildPlannerFilters_WhenCountIsTenOnAFifteenFloorPlanner_IndexesFloorsOneToTen() {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .category("15F")
                .content(FIFTEEN_FLOORS)
                .published(true)
                .save(plannerSeeds);

        callProcedure(planner, 10);

        assertThat(indexedRows(planner)).isEqualTo(floorRows(0, 10));
    }

    @Test
    void rebuildPlannerFilters_WhenCountExceedsTheStoredFloors_IndexesEveryStoredFloor() {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .category("5F")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerSeeds);

        callProcedure(planner, 15);

        assertThat(indexedRows(planner))
                .contains("THEME_PACK:1001", "THEME_PACK:1005", "THEME_PACK:1006", "EGO_GIFT:9002", "EGO_GIFT:9004");
    }

    @Test
    void rebuildPlannerFilters_WhenCountIsZero_IndexesNoFloorButKeepsEquipmentAndTopLevelGifts() {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .category("15F")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerSeeds);

        callProcedure(planner, 0);

        assertThat(indexedRows(planner))
                .contains("IDENTITY:10101", "EGO:20101", "EGO_GIFT:9001")
                .doesNotContain("EGO_GIFT:9002", "EGO_GIFT:9004")
                .noneMatch(row -> row.startsWith("THEME_PACK:"));
    }

    @Test
    void rebuildFilters_WhenARefractedRailwayPlannerIsPublished_KeepsItsNonFloorRows() {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .plannerType(PlannerType.REFRACTED_RAILWAY)
                .category("RR_PLACEHOLDER")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerSeeds);

        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        assertThat(indexedRows(planner))
                .contains("IDENTITY:10101", "EGO:20101", "EGO_GIFT:9001")
                .doesNotContain("EGO_GIFT:9002", "EGO_GIFT:9004")
                .noneMatch(row -> row.startsWith("THEME_PACK:"));
    }

    private void callRolloutProcedure(StoredPlanner planner) {
        new JdbcTemplate(dataSource).update("CALL rebuild_planner_filters(UUID_TO_BIN(?))", planner.id().toString());
    }

    @Test
    void rebuildPlannerFilters_WhenThePreviousImageCallsTheOneArgumentProcedure_IndexesWhatTheScopedOneDoes()
            throws Exception {
        for (MDCategory category : MDCategory.values()) {
            StoredPlanner planner = TestDataFactory.planner(owner)
                    .category(category.getValue())
                    .content(FIFTEEN_FLOORS)
                    .published(true)
                    .save(plannerSeeds);

            FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
            Set<String> scoped = indexedRows(planner);
            callRolloutProcedure(planner);

            assertThat(indexedRows(planner)).as(category.getValue())
                    .isEqualTo(scoped)
                    .isEqualTo(floorRows(0, gameDataRegistry.floorRules().floorCount(category)))
                    .isEqualTo(oracleRows(planner));
        }
    }

    @Test
    void rebuildPlannerFilters_WhenTheOneArgumentProcedureMeetsARefractedRailwayPlanner_IndexesNoFloor() {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .plannerType(PlannerType.REFRACTED_RAILWAY)
                .category("RR_PLACEHOLDER")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerSeeds);

        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        Set<String> scoped = indexedRows(planner);
        callRolloutProcedure(planner);

        assertThat(indexedRows(planner))
                .isEqualTo(scoped)
                .contains("IDENTITY:10101", "EGO_GIFT:9001")
                .noneMatch(row -> row.startsWith("THEME_PACK:"));
    }

    @Test
    void rebuildFilters_WhenObjectFloorsFailTheBoundary_IndexesTheSalvagedFieldsTheExtractorIndexes() throws Exception {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .category("5F")
                .content(MALFORMED_OBJECT_FLOORS)
                .published(true)
                .save(plannerSeeds);

        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        assertThat(indexedRows(planner))
                .filteredOn(row -> row.startsWith("THEME_PACK:") || row.startsWith("EGO_GIFT:"))
                .containsExactlyInAnyOrder("THEME_PACK:1001", "EGO_GIFT:9001", "THEME_PACK:1003", "EGO_GIFT:9003",
                        "THEME_PACK:1004", "EGO_GIFT:9002", "EGO_GIFT:9005", "EGO_GIFT:9004");
        assertThat(indexedRows(planner)).isEqualTo(oracleRows(planner));
    }

    @Test
    void floorScopeOf_WhenReadInsideARebuildTransaction_HoldsOffACategoryChangeUntilCommit() throws Exception {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .category("5F")
                .content(FIFTEEN_FLOORS)
                .published(true)
                .save(plannerSeeds);

        new TransactionTemplate(transactionManager).executeWithoutResult(rebuild -> {
            assertThat(entityFilterRepository.floorScopeOf(planner.id())).isPresent();

            assertThatThrownBy(() -> changeCategory(planner, "15F"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Lock wait timeout");
        });

        assertThat(changeCategory(planner, "15F")).isEqualTo(1);
    }

    private int changeCategory(StoredPlanner planner, String category) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement session = connection.createStatement();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE planner_content SET category = ? WHERE planner_id = UUID_TO_BIN(?)")) {
            session.execute("SET SESSION innodb_lock_wait_timeout = 1");
            try {
                update.setString(1, category);
                update.setString(2, planner.id().toString());
                return update.executeUpdate();
            } finally {
                session.execute("SET SESSION innodb_lock_wait_timeout = DEFAULT");
            }
        }
    }
}
