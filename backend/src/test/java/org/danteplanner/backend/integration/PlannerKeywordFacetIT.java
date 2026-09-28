package org.danteplanner.backend.integration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.config.TestConfig;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.scheduler.PlannerKeywordBackfill;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Faceted search over the inverted indexes and keyword normalization:
 * facets serve only matching planners, legacy keyword names are normalized
 * before storage and the filter table, and reading any persisted keyword row
 * is total.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerKeywordFacetIT {

    @DynamicPropertySource
    static void ownIndex(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "keyword_facet");
    }





    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

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
    private DataSource dataSource;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PlannerKeywordBackfill keywordBackfill;

    private User owner;

    @BeforeEach
    void setUp() {
        // First statement of the only @BeforeEach: JUnit does not order sibling
        // @BeforeEach methods, so a separate wipe method could run after setup.
        catalogRepository.deleteAll();
        entityFilterRepository.deleteAll();
        keywordFilterRepository.deleteAll();
        plannerRepository.deleteAll();
        statsRepository.deleteAll();
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "facet-owner@example.com");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
    }

    /**
     * Publish a planner (factory content: identities 10101.., gifts 9001/9002,
     * theme pack 1001) with its projections built the way the publish path does.
     */
    private Planner publishWithFilters(String title, Set<String> keywords) {
        Planner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(keywords)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        filterService.rebuildFilters(planner.getId());
        return planner;
    }

    @Test
    @DisplayName("entity-facet-filter: only planners whose entity index contains the entity are returned")
    void entityFacetFilter_WhenFiltered_ReturnsOnlyMatchingPlanners() throws Exception {
        publishWithFilters("With Entities", Set.of("Sinking"));
        // A planner with empty content indexes nothing
        Planner empty = TestDataFactory.planner(owner)
                .title("No Entities")
                .content("{\"equipment\":{},\"selectedGiftIds\":[],\"observationGiftIds\":[],"
                        + "\"comprehensiveGiftIds\":[],\"floorSelections\":[]}")
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(empty.getId()).build());
        catalogService.add(empty);
        filterService.rebuildFilters(empty.getId());

        mockMvc.perform(get("/api/planner/md/published").param("identity", "10101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("With Entities"));

        mockMvc.perform(get("/api/planner/md/published").param("themePack", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("With Entities"));

        mockMvc.perform(get("/api/planner/md/published").param("identity", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("keyword-facet-filter: only planners whose keyword index contains the keyword are returned")
    void keywordFacetFilter_WhenFiltered_ReturnsOnlyMatchingPlanners() throws Exception {
        publishWithFilters("Sinking Build", Set.of("Sinking"));
        publishWithFilters("Burn Build", Set.of("Combustion"));

        mockMvc.perform(get("/api/planner/md/published").param("keyword", "Sinking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Sinking Build"));

        mockMvc.perform(get("/api/planner/md/published").param("keyword", "DawnTeam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("keyword-rename-normalized-on-write: a legacy name in the content is stored, indexed, and filterable as the current id; the top-level copy is ignored")
    void keywordRenameNormalizedOnWrite_WhenContentSelectsALegacyName_StoredAsCurrentId() throws Exception {
        Planner planner = publishWithFilters("Legacy Keywords", Set.of("Sinking"));

        // A stale client syncs the pre-rename name plus an unknown keyword
        UpsertPlannerRequest req = new UpsertPlannerRequest(
                planner.getId().toString(), planner.getCategory(), null, null, contentWith(Set.of("AccelBullet")),
                planner.getContentVersion(), PlannerType.MIRROR_DUNGEON, planner.getSyncVersion(),
                Set.of("AccelBullet", "NotAKeyword"));
        commandService.upsertPlanner(owner.getId(), null, planner.getId(), req, false);

        // Stored JSON carries only the normalized current id
        String stored = new JdbcTemplate(dataSource).queryForObject(
                "SELECT selected_keywords FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                String.class, planner.getId().toString());
        assertThat(stored).isEqualTo("[\"9828\"]");

        // The keyword index carries the normalized slug, never the legacy name
        Set<String> indexed = keywordFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.getId()))
                .map(PlannerKeywordFilter::getKeyword)
                .collect(Collectors.toSet());
        assertThat(indexed).containsExactly("9828");

        // And the facet finds it under the current id
        mockMvc.perform(get("/api/planner/md/published").param("keyword", "9828"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Legacy Keywords"));
    }

    @Test
    @DisplayName("keyword-read-passthrough-total: legacy names at rest read back current; malformed storage reads as empty, never throws")
    void keywordReadPassthroughTotal_WhenLegacyOrMalformedStorage_ReadsWithoutError() throws Exception {
        Planner planner = publishWithFilters("At Rest", Set.of("Sinking"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Storage written by older code: a legacy name inside the array
        jdbc.update("UPDATE planner_content SET selected_keywords = ? WHERE planner_id = UUID_TO_BIN(?)",
                "[\"AccelBullet\",\"Sinking\"]", planner.getId().toString());
        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedKeywords",
                        org.hamcrest.Matchers.containsInAnyOrder("9828", "Sinking")));

        // Malformed storage must read as empty rather than failing the request.
        // (The JSON column type rejects raw garbage, so simulate the worst
        // storable case: a JSON value that is not a string array.)
        jdbc.update("UPDATE planner_content SET selected_keywords = ? WHERE planner_id = UUID_TO_BIN(?)",
                "{\"not\":\"an array\"}", planner.getId().toString());
        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedKeywords").isEmpty());
    }

    private static String contentWith(Set<String> keywords) {
        return TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT, keywords);
    }

    private String storedKeywords(Planner planner) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT selected_keywords FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                String.class, planner.getId().toString());
    }

    private double topLevelCount(String outcome) {
        Counter counter = meterRegistry.find("planner_keywords_toplevel_total").tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private UpsertPlannerRequest saving(Planner planner, String content, Set<String> topLevel) {
        return new UpsertPlannerRequest(
                planner.getId().toString(), planner.getCategory(), null, null, content,
                planner.getContentVersion(), PlannerType.MIRROR_DUNGEON, planner.getSyncVersion(), topLevel);
    }

    @Test
    void upsert_WhenTopLevelKeywordsDisagreeWithContent_StoresTheContentKeywordsAndCountsAMismatch() {
        Planner planner = TestDataFactory.planner(owner).title("Top-Level Copy")
                .selectedKeywords(Set.of("Sinking"))
                .save(plannerRepository);
        double mismatchesBefore = topLevelCount("mismatch");

        commandService.upsertPlanner(owner.getId(), null, planner.getId(),
                saving(planner, contentWith(Set.of("Sinking")), Set.of("Burst")), false);

        assertThat(storedKeywords(planner)).isEqualTo("[\"Sinking\"]");
        assertThat(topLevelCount("mismatch")).isEqualTo(mismatchesBefore + 1);
    }

    @Test
    void upsert_WhenTopLevelKeywordsAreAbsentOrMatch_CountsEachOutcome() {
        Planner planner = TestDataFactory.planner(owner).title("Top-Level Outcomes")
                .selectedKeywords(Set.of("Sinking"))
                .save(plannerRepository);
        double absentBefore = topLevelCount("absent");
        double matchesBefore = topLevelCount("match");

        commandService.upsertPlanner(owner.getId(), null, planner.getId(),
                saving(planner, contentWith(Set.of("Burst")), null), false);
        commandService.upsertPlanner(owner.getId(), null, planner.getId(),
                saving(planner, contentWith(Set.of("Combustion")), Set.of("Combustion")), true);

        assertThat(topLevelCount("absent")).isEqualTo(absentBefore + 1);
        assertThat(topLevelCount("match")).isEqualTo(matchesBefore + 1);
    }

    @Test
    void upsert_WhenADraftCarriesARenamedAndAnUnknownKeyword_StoresTheCurrentIdAndKeepsTheBlob() {
        Planner draft = TestDataFactory.planner(owner).title("Draft Rename").save(plannerRepository);

        commandService.upsertPlanner(owner.getId(), null, draft.getId(),
                saving(draft, contentWith(new java.util.LinkedHashSet<>(java.util.List.of("AccelBullet", "NotAKeyword"))),
                        null), false);

        assertThat(storedKeywords(draft)).isEqualTo("[\"9828\"]");
        String blobKeywords = new JdbcTemplate(dataSource).queryForObject(
                "SELECT JSON_EXTRACT(content, '$.selectedKeywords') FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                String.class, draft.getId().toString());
        assertThat(blobKeywords).contains("\"AccelBullet\"", "\"NotAKeyword\"");
    }

    @Test
    void upsert_WhenTheTopLevelCopyIsAbsent_DerivesTheColumnFromTheNewContent() {
        Planner planner = TestDataFactory.planner(owner).title("Absent Copy")
                .selectedKeywords(Set.of("Sinking"))
                .save(plannerRepository);

        commandService.upsertPlanner(owner.getId(), null, planner.getId(),
                saving(planner, contentWith(Set.of("Burst")), null), false);

        assertThat(storedKeywords(planner)).isEqualTo("[\"Burst\"]");
    }

    @Test
    void keywordFacetFilter_WhenQueriedByARenamedKeyword_ReturnsThePlannerStoredUnderTheCurrentId() throws Exception {
        publishWithFilters("Renamed Facet", Set.of("9828"));

        mockMvc.perform(get("/api/planner/md/published").param("keyword", "AccelBullet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Renamed Facet"));

        mockMvc.perform(get("/api/planner/md/published").param("q", "AccelBullet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Renamed Facet"));
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private Integer runBackfill() {
        return keywordBackfill.backfill();
    }

    private void clearColumns(UUID plannerId) {
        jdbc().update("UPDATE planner_content SET selected_keywords = NULL WHERE planner_id = UUID_TO_BIN(?)",
                plannerId.toString());
        jdbc().update("UPDATE planner_catalog SET selected_keywords = NULL WHERE planner_id = UUID_TO_BIN(?)",
                plannerId.toString());
    }

    private String column(String table, UUID plannerId) {
        return jdbc().queryForObject("SELECT selected_keywords FROM " + table + " WHERE planner_id = UUID_TO_BIN(?)",
                String.class, plannerId.toString());
    }

    private List<String> indexedKeywords(UUID plannerId) {
        return jdbc().queryForList("SELECT keyword FROM planner_keyword_filter WHERE planner_id = UUID_TO_BIN(?)",
                String.class, plannerId.toString());
    }

    private Planner publishedWithoutColumn(String title, List<String> contentKeywords) {
        Planner planner = TestDataFactory.planner(owner)
                .title(title)
                .content(TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT, contentKeywords))
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        clearColumns(planner.getId());
        filterService.rebuildFilters(planner.getId());
        return planner;
    }

    @Test
    void backfill_WhenAPublishedRowHasNoColumnButItsContentSelectsKeywords_DerivesColumnCatalogAndIndex()
            throws Exception {
        Planner planner = publishedWithoutColumn("Backfill Published", List.of("Burst"));
        assertThat(column("planner_content", planner.getId())).isNull();
        assertThat(indexedKeywords(planner.getId())).isEmpty();

        runBackfill();

        assertThat(column("planner_content", planner.getId())).isEqualTo("[\"Burst\"]");
        assertThat(column("planner_catalog", planner.getId())).isEqualTo("[\"Burst\"]");
        assertThat(indexedKeywords(planner.getId())).containsExactly("Burst");
    }

    @Test
    void backfill_WhenADraftSelectsARenamedAndAnUnknownKeyword_StoresTheCurrentIdAndKeepsTheBlob() throws Exception {
        Planner draft = TestDataFactory.planner(owner)
                .title("Backfill Draft")
                .content(TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT,
                        List.of("AccelBullet", "NotAKeyword")))
                .save(plannerRepository);
        clearColumns(draft.getId());

        runBackfill();

        assertThat(column("planner_content", draft.getId())).isEqualTo("[\"9828\"]");
        assertThat(jdbc().queryForObject("SELECT JSON_EXTRACT(content, '$.selectedKeywords') FROM planner_content "
                        + "WHERE planner_id = UUID_TO_BIN(?)", String.class, draft.getId().toString()))
                .contains("\"AccelBullet\"", "\"NotAKeyword\"");
        assertThat(indexedKeywords(draft.getId())).isEmpty();
    }

    @Test
    void backfill_WhenTheColumnCarriesKeywordsTheContentDoesNot_ClearsTheColumnCatalogAndIndex() {
        Planner planner = publishedWithoutColumn("Backfill Stale Column", List.of());
        jdbc().update("UPDATE planner_content SET selected_keywords = '[\"Sinking\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.getId().toString());
        jdbc().update("UPDATE planner_catalog SET selected_keywords = '[\"Sinking\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.getId().toString());
        filterService.rebuildFilters(planner.getId());
        assertThat(indexedKeywords(planner.getId())).containsExactly("Sinking");

        runBackfill();

        assertThat(column("planner_content", planner.getId())).isNull();
        assertThat(column("planner_catalog", planner.getId())).isNull();
        assertThat(indexedKeywords(planner.getId())).isEmpty();
    }

    @Test
    void backfill_WhenTheColumnStoresALegacyName_RewritesItToTheCurrentId() {
        Planner planner = publishedWithoutColumn("Backfill Legacy Column", List.of("ChargeLoad"));
        jdbc().update("UPDATE planner_content SET selected_keywords = '[\"ChargeLoad\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.getId().toString());
        filterService.rebuildFilters(planner.getId());
        assertThat(indexedKeywords(planner.getId())).containsExactly("ChargeLoad");

        runBackfill();

        assertThat(column("planner_content", planner.getId())).isEqualTo("[\"EmergencyChargeForceField\"]");
        assertThat(indexedKeywords(planner.getId())).containsExactly("EmergencyChargeForceField");
    }

    @Test
    void backfill_WhenACatalogCopyIsStaleNextToACorrectColumn_RepairsTheCatalog() {
        Planner planner = publishWithFilters("Backfill Stale Catalog", Set.of("Burst"));
        jdbc().update("UPDATE planner_catalog SET selected_keywords = '[\"Sinking\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.getId().toString());

        runBackfill();

        assertThat(column("planner_content", planner.getId())).isEqualTo("[\"Burst\"]");
        assertThat(column("planner_catalog", planner.getId())).isEqualTo("[\"Burst\"]");
        assertThat(indexedKeywords(planner.getId())).containsExactly("Burst");
    }

    @Test
    void backfill_WhenRunTwice_CorrectsOnceAndThenChangesNothing() {
        Planner planner = publishedWithoutColumn("Backfill Rerun", List.of("Burst"));
        publishWithFilters("Backfill Already Consistent", Set.of("Sinking"));

        assertThat(runBackfill()).isEqualTo(1);
        assertThat(runBackfill()).isZero();
        assertThat(column("planner_content", planner.getId())).isEqualTo("[\"Burst\"]");
    }

    @Test
    void backfill_WhenTheContentSelectionIsNotAnArray_ClearsTheColumn() {
        Planner planner = publishWithFilters("Backfill Not An Array", Set.of("Sinking"));
        jdbc().update("UPDATE planner_content SET content = JSON_SET(content, '$.selectedKeywords', 'none') "
                + "WHERE planner_id = UUID_TO_BIN(?)", planner.getId().toString());

        runBackfill();

        assertThat(column("planner_content", planner.getId())).isNull();
        assertThat(column("planner_catalog", planner.getId())).isNull();
        assertThat(indexedKeywords(planner.getId())).isEmpty();
    }

    @Test
    void backfill_WhenTheContentSelectionHoldsANonString_KeepsOnlyTheStringKeywords() {
        Planner planner = publishWithFilters("Backfill Non String", Set.of("Sinking"));
        jdbc().update("UPDATE planner_content SET content = JSON_SET(content, '$.selectedKeywords', "
                + "JSON_ARRAY(9828, 'Burst')) WHERE planner_id = UUID_TO_BIN(?)", planner.getId().toString());

        runBackfill();

        assertThat(column("planner_content", planner.getId())).isEqualTo("[\"Burst\"]");
        assertThat(indexedKeywords(planner.getId())).containsExactly("Burst");
    }

    @Test
    void keywordFacetFilter_WhenARenamedKeywordIsQueriedInAnotherCase_ReturnsThePlannerStoredUnderTheCurrentId()
            throws Exception {
        publishWithFilters("Renamed Facet Any Case", Set.of("9828"));

        mockMvc.perform(get("/api/planner/md/published").param("keyword", "ACCELBULLET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Renamed Facet Any Case"));

        mockMvc.perform(get("/api/planner/md/published").param("q", "accelbullet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Renamed Facet Any Case"));
    }
}
