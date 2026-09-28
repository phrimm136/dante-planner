package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.validation.GameDataRegistry;
import org.danteplanner.backend.planner.validation.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.service.PlannerFilterService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerFilterRebuildIT {

    @DynamicPropertySource
    static void ownIndex(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "filter_rebuild");
    }





    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerEntityFilterRepository entityFilterRepository;

    @Autowired
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Autowired
    private PlannerFilterService filterService;

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
        entityFilterRepository.deleteAll();
        keywordFilterRepository.deleteAll();
        plannerRepository.deleteAll();
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
    @DisplayName("a rebuild over existing filter rows replaces them without a duplicate-key failure")
    void rebuildFilters_WhenRowsAlreadyExist_ReplacesWithoutDuplicateKey() {
        Planner planner = TestDataFactory.planner(owner)
                .selectedKeywords(Set.of("Sinking", "Combustion"))
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());
        long entityRowsAfterFirstBuild = entityFilterRepository.count();
        assertThat(entityRowsAfterFirstBuild).isPositive();

        filterService.rebuildFilters(planner.getId());

        assertThat(entityFilterRepository.count()).isEqualTo(entityRowsAfterFirstBuild);
        assertThat(keywordFilterRepository.findAll())
                .extracting(PlannerKeywordFilter::getKeyword)
                .containsExactlyInAnyOrder("Sinking", "Combustion");
    }

    @Test
    @DisplayName("a rebuild whose read view predates a concurrently committed rebuild still replaces the rows")
    void rebuildFilters_WhenConcurrentRebuildCommittedAfterSnapshot_ReplacesWithoutDuplicateKey() {
        Planner planner = TestDataFactory.planner(owner)
                .selectedKeywords(Set.of("Sinking", "Combustion"))
                .published(true)
                .save(plannerRepository);

        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            // Pin this transaction's InnoDB read view before the concurrent commit
            assertThat(entityFilterRepository.count()).isZero();

            concurrent.executeWithoutResult(inner -> filterService.rebuildFilters(planner.getId()));

            filterService.rebuildFilters(planner.getId());
        });

        assertThat(keywordFilterRepository.findAll())
                .extracting(PlannerKeywordFilter::getKeyword)
                .containsExactlyInAnyOrder("Sinking", "Combustion");
    }

    @Test
    @DisplayName("the procedure's extraction matches the Java oracle the drift reconciler audits with")
    void rebuildFilters_WhenFactoryContent_MatchesExtractorOracle() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());

        Set<String> expected = extractor
                .extract(objectMapper.readTree(planner.getContentJson()), MDCategory.fromValue(planner.getCategory()))
                .stream()
                .map(ref -> ref.type().name() + ":" + ref.id())
                .collect(Collectors.toSet());
        Set<String> actual = entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.getId()))
                .map(f -> f.getEntityType().name() + ":" + f.getEntityId())
                .collect(Collectors.toSet());

        assertThat(expected).isNotEmpty();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("enhanced gift ids index under their base, collapsing onto an existing base row")
    void rebuildFilters_WhenEnhancedGiftIds_IndexesBaseIdOnly() throws Exception {
        String content = """
                {"equipment":{},
                 "selectedGiftIds":["9154","19154","29154"],
                 "observationGiftIds":["19001"],
                 "comprehensiveGiftIds":[],
                 "floorSelections":[{"giftIds":["29002"],"themePackId":null}]}
                """;
        Planner planner = TestDataFactory.planner(owner)
                .published(true)
                .content(content)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());

        Set<Integer> gifts = entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.getId()))
                .filter(f -> f.getEntityType().name().equals("EGO_GIFT"))
                .map(f -> f.getEntityId())
                .collect(Collectors.toSet());

        // 9154/19154/29154 are one gift, so the three collapse to a single row.
        assertThat(gifts).containsExactlyInAnyOrder(9154, 9001, 9002);

        Set<String> oracle = extractor
                .extract(objectMapper.readTree(content), MDCategory.fromValue(planner.getCategory()))
                .stream()
                .filter(ref -> ref.type().name().equals("EGO_GIFT"))
                .map(ref -> String.valueOf(ref.id()))
                .collect(Collectors.toSet());
        assertThat(oracle).isEqualTo(gifts.stream().map(String::valueOf).collect(Collectors.toSet()));
    }

    private Set<String> indexedRows(Planner planner) {
        return entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.getId()))
                .map(f -> f.getEntityType().name() + ":" + f.getEntityId())
                .collect(Collectors.toSet());
    }

    private Set<String> oracleRows(Planner planner) throws Exception {
        return extractor
                .extract(objectMapper.readTree(planner.getContentJson()), MDCategory.fromValue(planner.getCategory()))
                .stream()
                .map(ref -> ref.type().name() + ":" + ref.id())
                .collect(Collectors.toSet());
    }

    @Test
    void rebuildFilters_WhenAFiveFloorPlannerStoresASixthFloor_SkipsItsThemePackAndGifts() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .category("5F")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());

        assertThat(indexedRows(planner))
                .doesNotContain("THEME_PACK:1006", "EGO_GIFT:9004")
                .contains("THEME_PACK:1001", "THEME_PACK:1002", "THEME_PACK:1003", "THEME_PACK:1004",
                        "THEME_PACK:1005", "EGO_GIFT:9002")
                .isEqualTo(oracleRows(planner));
    }

    @Test
    void rebuildFilters_WhenAFifteenFloorPlannerStoresTheSameSixthFloor_IndexesItsThemePackAndGifts() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .category("15F")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());

        assertThat(indexedRows(planner))
                .contains("THEME_PACK:1006", "EGO_GIFT:9004")
                .isEqualTo(oracleRows(planner));
    }

    @Test
    void rebuildFilters_WhenEachCategoryStoresFifteenFloors_IndexesExactlyTheJavaFloorCount() throws Exception {
        for (MDCategory category : MDCategory.values()) {
            Planner planner = TestDataFactory.planner(owner)
                    .category(category.getValue())
                    .content(FIFTEEN_FLOORS)
                    .published(true)
                    .save(plannerRepository);

            filterService.rebuildFilters(planner.getId());

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

    private void callProcedure(Planner planner, int floorCount) {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> entityFilterRepository.rebuildPlannerFilters(planner.getId(), floorCount));
    }

    private static Set<String> floorRows(int fromFloor, int toFloor) {
        return IntStream.range(fromFloor, toFloor)
                .boxed()
                .flatMap(floor -> Stream.of("THEME_PACK:" + (1001 + floor), "EGO_GIFT:" + (9001 + floor)))
                .collect(Collectors.toSet());
    }

    @Test
    void rebuildPlannerFilters_WhenCountIsTenOnAFifteenFloorPlanner_IndexesFloorsOneToTen() {
        Planner planner = TestDataFactory.planner(owner)
                .category("15F")
                .content(FIFTEEN_FLOORS)
                .published(true)
                .save(plannerRepository);

        callProcedure(planner, 10);

        assertThat(indexedRows(planner)).isEqualTo(floorRows(0, 10));
    }

    @Test
    void rebuildPlannerFilters_WhenCountExceedsTheStoredFloors_IndexesEveryStoredFloor() {
        Planner planner = TestDataFactory.planner(owner)
                .category("5F")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerRepository);

        callProcedure(planner, 15);

        assertThat(indexedRows(planner))
                .contains("THEME_PACK:1001", "THEME_PACK:1005", "THEME_PACK:1006", "EGO_GIFT:9002", "EGO_GIFT:9004");
    }

    @Test
    void rebuildPlannerFilters_WhenCountIsZero_IndexesNoFloorButKeepsEquipmentAndTopLevelGifts() {
        Planner planner = TestDataFactory.planner(owner)
                .category("15F")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerRepository);

        callProcedure(planner, 0);

        assertThat(indexedRows(planner))
                .contains("IDENTITY:10101", "EGO:20101", "EGO_GIFT:9001")
                .doesNotContain("EGO_GIFT:9002", "EGO_GIFT:9004")
                .noneMatch(row -> row.startsWith("THEME_PACK:"));
    }

    @Test
    void rebuildFilters_WhenARefractedRailwayPlannerIsPublished_KeepsItsNonFloorRows() {
        Planner planner = TestDataFactory.planner(owner)
                .plannerType(PlannerType.REFRACTED_RAILWAY)
                .category("RR_PLACEHOLDER")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());

        assertThat(indexedRows(planner))
                .contains("IDENTITY:10101", "EGO:20101", "EGO_GIFT:9001")
                .doesNotContain("EGO_GIFT:9002", "EGO_GIFT:9004")
                .noneMatch(row -> row.startsWith("THEME_PACK:"));
    }

    private void callRolloutProcedure(Planner planner) {
        new JdbcTemplate(dataSource).update("CALL rebuild_planner_filters(UUID_TO_BIN(?))", planner.getId().toString());
    }

    @Test
    void rebuildPlannerFilters_WhenThePreviousImageCallsTheOneArgumentProcedure_IndexesWhatTheScopedOneDoes()
            throws Exception {
        for (MDCategory category : MDCategory.values()) {
            Planner planner = TestDataFactory.planner(owner)
                    .category(category.getValue())
                    .content(FIFTEEN_FLOORS)
                    .published(true)
                    .save(plannerRepository);

            filterService.rebuildFilters(planner.getId());
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
        Planner planner = TestDataFactory.planner(owner)
                .plannerType(PlannerType.REFRACTED_RAILWAY)
                .category("RR_PLACEHOLDER")
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());
        Set<String> scoped = indexedRows(planner);
        callRolloutProcedure(planner);

        assertThat(indexedRows(planner))
                .isEqualTo(scoped)
                .contains("IDENTITY:10101", "EGO_GIFT:9001")
                .noneMatch(row -> row.startsWith("THEME_PACK:"));
    }

    @Test
    void rebuildFilters_WhenObjectFloorsFailTheBoundary_IndexesTheSalvagedFieldsTheExtractorIndexes() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .category("5F")
                .content(MALFORMED_OBJECT_FLOORS)
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());

        assertThat(indexedRows(planner))
                .filteredOn(row -> row.startsWith("THEME_PACK:") || row.startsWith("EGO_GIFT:"))
                .containsExactlyInAnyOrder("THEME_PACK:1001", "EGO_GIFT:9001", "THEME_PACK:1003", "EGO_GIFT:9003",
                        "THEME_PACK:1004", "EGO_GIFT:9002", "EGO_GIFT:9005", "EGO_GIFT:9004");
        assertThat(indexedRows(planner)).isEqualTo(oracleRows(planner));
    }

    @Test
    void floorScopeOf_WhenReadInsideARebuildTransaction_HoldsOffACategoryChangeUntilCommit() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .category("5F")
                .content(FIFTEEN_FLOORS)
                .published(true)
                .save(plannerRepository);

        new TransactionTemplate(transactionManager).executeWithoutResult(rebuild -> {
            assertThat(entityFilterRepository.floorScopeOf(planner.getId())).isPresent();

            assertThatThrownBy(() -> changeCategory(planner, "15F"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Lock wait timeout");
        });

        assertThat(changeCategory(planner, "15F")).isEqualTo(1);
    }

    private int changeCategory(Planner planner, String category) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement session = connection.createStatement();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE planner_content SET category = ? WHERE planner_id = UUID_TO_BIN(?)")) {
            session.execute("SET SESSION innodb_lock_wait_timeout = 1");
            try {
                update.setString(1, category);
                update.setString(2, planner.getId().toString());
                return update.executeUpdate();
            } finally {
                session.execute("SET SESSION innodb_lock_wait_timeout = DEFAULT");
            }
        }
    }
}
