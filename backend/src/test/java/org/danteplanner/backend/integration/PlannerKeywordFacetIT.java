package org.danteplanner.backend.integration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.scheduler.PlannerKeywordBackfill;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.support.FilterRowsSync;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.planner.sync.SyncVersionValidator.WriteMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

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
class PlannerKeywordFacetIT extends ExclusiveDatabaseSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Autowired
    private PlannerCommandService commandService;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

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
        emptyPlannerTables(new JdbcTemplate(dataSource));
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
    private StoredPlanner publishWithFilters(String title, Set<String> keywords) {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(keywords)
                .published(true)
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());
        TestDataFactory.addToCatalog(catalogService, planner);
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        return planner;
    }

    // entity-facet-filter
    @Test
    void entityFacetFilter_WhenFiltered_ReturnsOnlyMatchingPlanners() throws Exception {
        publishWithFilters("With Entities", Set.of("Sinking"));
        // A planner with empty content indexes nothing
        StoredPlanner empty = TestDataFactory.planner(owner)
                .title("No Entities")
                .content("{\"equipment\":{},\"selectedGiftIds\":[],\"observationGiftIds\":[],"
                        + "\"comprehensiveGiftIds\":[],\"floorSelections\":[]}")
                .published(true)
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder().plannerId(empty.id()).build());
        TestDataFactory.addToCatalog(catalogService, empty);
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, empty.id());

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

    // keyword-facet-filter
    @Test
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

    // keyword-rename-normalized-on-write
    @Test
    void keywordRenameNormalizedOnWrite_WhenContentSelectsALegacyName_StoredAsCurrentId() throws Exception {
        StoredPlanner planner = publishWithFilters("Legacy Keywords", Set.of("Sinking"));

        // A stale client syncs the pre-rename name plus an unknown keyword
        UpsertPlannerRequest req = UpsertRequests.resending(planner)
                .title(null)
                .content(contentWith(Set.of("AccelBullet")))
                .selectedKeywords(Set.of("AccelBullet", "NotAKeyword"))
                .build();
        commandService.upsertPlanner(owner.getId(), UUID.randomUUID(), planner.id(), req, WriteMode.CHECKED);

        // Stored JSON carries only the normalized current id
        String stored = new JdbcTemplate(dataSource).queryForObject(
                "SELECT selected_keywords FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                String.class, planner.id().toString());
        assertThat(stored).isEqualTo("[\"9828\"]");

        // The keyword index carries the normalized slug, never the legacy name
        Set<String> indexed = keywordFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.id()))
                .map(PlannerKeywordFilter::getKeyword)
                .collect(Collectors.toSet());
        assertThat(indexed).containsExactly("9828");

        // And the facet finds it under the current id
        mockMvc.perform(get("/api/planner/md/published").param("keyword", "9828"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Legacy Keywords"));
    }

    // keyword-read-passthrough-total
    @Test
    void keywordReadPassthroughTotal_WhenLegacyOrMalformedStorage_ReadsWithoutError() throws Exception {
        StoredPlanner planner = publishWithFilters("At Rest", Set.of("Sinking"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Storage written by older code: a legacy name inside the array
        jdbc.update("UPDATE planner_content SET selected_keywords = ? WHERE planner_id = UUID_TO_BIN(?)",
                "[\"AccelBullet\",\"Sinking\"]", planner.id().toString());
        mockMvc.perform(get("/api/planner/md/published/{id}", planner.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedKeywords",
                        org.hamcrest.Matchers.containsInAnyOrder("9828", "Sinking")));

        // Malformed storage must read as empty rather than failing the request.
        // (The JSON column type rejects raw garbage, so simulate the worst
        // storable case: a JSON value that is not a string array.)
        jdbc.update("UPDATE planner_content SET selected_keywords = ? WHERE planner_id = UUID_TO_BIN(?)",
                "{\"not\":\"an array\"}", planner.id().toString());
        mockMvc.perform(get("/api/planner/md/published/{id}", planner.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedKeywords").isEmpty());
    }

    private static String contentWith(Set<String> keywords) {
        return TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT, keywords);
    }

    private String storedKeywords(StoredPlanner planner) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT selected_keywords FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                String.class, planner.id().toString());
    }

    private double topLevelCount(String outcome) {
        Counter counter = meterRegistry.find("planner_keywords_toplevel_total").tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void upsert_WhenTopLevelKeywordsDisagreeWithContent_StoresTheContentKeywordsAndCountsAMismatch() {
        StoredPlanner planner = TestDataFactory.planner(owner).title("Top-Level Copy")
                .selectedKeywords(Set.of("Sinking"))
                .save(plannerSeeds);
        double mismatchesBefore = topLevelCount("mismatch");

        commandService.upsertPlanner(owner.getId(), UUID.randomUUID(), planner.id(),
                UpsertRequests.resending(planner)
                        .title(null)
                        .content(contentWith(Set.of("Sinking")))
                        .selectedKeywords(Set.of("Burst"))
                        .build(), WriteMode.CHECKED);

        assertThat(storedKeywords(planner)).isEqualTo("[\"Sinking\"]");
        assertThat(topLevelCount("mismatch")).isEqualTo(mismatchesBefore + 1);
    }

    @Test
    void upsert_WhenTopLevelKeywordsAreAbsentOrMatch_CountsEachOutcome() {
        StoredPlanner planner = TestDataFactory.planner(owner).title("Top-Level Outcomes")
                .selectedKeywords(Set.of("Sinking"))
                .save(plannerSeeds);
        double absentBefore = topLevelCount("absent");
        double matchesBefore = topLevelCount("match");

        commandService.upsertPlanner(owner.getId(), UUID.randomUUID(), planner.id(),
                UpsertRequests.resending(planner).title(null).content(contentWith(Set.of("Burst"))).build(), WriteMode.CHECKED);
        commandService.upsertPlanner(owner.getId(), UUID.randomUUID(), planner.id(),
                UpsertRequests.resending(planner)
                        .title(null)
                        .content(contentWith(Set.of("Combustion")))
                        .selectedKeywords(Set.of("Combustion"))
                        .build(), WriteMode.FORCED);

        assertThat(topLevelCount("absent")).isEqualTo(absentBefore + 1);
        assertThat(topLevelCount("match")).isEqualTo(matchesBefore + 1);
    }

    @Test
    void upsert_WhenADraftCarriesARenamedAndAnUnknownKeyword_StoresTheCurrentIdAndKeepsTheBlob() {
        StoredPlanner draft = TestDataFactory.planner(owner).title("Draft Rename").save(plannerSeeds);

        commandService.upsertPlanner(owner.getId(), UUID.randomUUID(), draft.id(),
                UpsertRequests.resending(draft)
                        .title(null)
                        .content(contentWith(new java.util.LinkedHashSet<>(java.util.List.of("AccelBullet", "NotAKeyword"))))
                        .build(), WriteMode.CHECKED);

        assertThat(storedKeywords(draft)).isEqualTo("[\"9828\"]");
        String blobKeywords = new JdbcTemplate(dataSource).queryForObject(
                "SELECT JSON_EXTRACT(content, '$.selectedKeywords') FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                String.class, draft.id().toString());
        assertThat(blobKeywords).contains("\"AccelBullet\"", "\"NotAKeyword\"");
    }

    @Test
    void upsert_WhenTheTopLevelCopyIsAbsent_DerivesTheColumnFromTheNewContent() {
        StoredPlanner planner = TestDataFactory.planner(owner).title("Absent Copy")
                .selectedKeywords(Set.of("Sinking"))
                .save(plannerSeeds);

        commandService.upsertPlanner(owner.getId(), UUID.randomUUID(), planner.id(),
                UpsertRequests.resending(planner).title(null).content(contentWith(Set.of("Burst"))).build(), WriteMode.CHECKED);

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

    private StoredPlanner publishedWithoutColumn(String title, List<String> contentKeywords) {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .title(title)
                .content(TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT, contentKeywords))
                .published(true)
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());
        TestDataFactory.addToCatalog(catalogService, planner);
        clearColumns(planner.id());
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        return planner;
    }

    @Test
    void backfill_WhenAPublishedRowHasNoColumnButItsContentSelectsKeywords_DerivesColumnCatalogAndIndex()
            throws Exception {
        StoredPlanner planner = publishedWithoutColumn("Backfill Published", List.of("Burst"));
        assertThat(column("planner_content", planner.id())).isNull();
        assertThat(indexedKeywords(planner.id())).isEmpty();

        runBackfill();

        assertThat(column("planner_content", planner.id())).isEqualTo("[\"Burst\"]");
        assertThat(column("planner_catalog", planner.id())).isEqualTo("[\"Burst\"]");
        assertThat(indexedKeywords(planner.id())).containsExactly("Burst");
    }

    @Test
    void backfill_WhenTheOwnerDeletedTheirAccount_CorrectsTheColumnAndLeavesNoFilterRows() {
        StoredPlanner planner = publishedWithoutColumn("Backfill Deleted Owner", List.of("Burst"));
        lifecycleService.deleteAccount(owner.getId());
        jdbc().update("INSERT INTO planner_keyword_filter (keyword, planner_id) VALUES ('Burst', UUID_TO_BIN(?))",
                planner.id().toString());

        runBackfill();

        assertThat(column("planner_content", planner.id())).isEqualTo("[\"Burst\"]");
        assertThat(indexedKeywords(planner.id()))
                .as("the owner's planners are invisible, so the correction clears their rows instead of rebuilding")
                .isEmpty();
    }

    @Test
    void backfill_WhenADraftSelectsARenamedAndAnUnknownKeyword_StoresTheCurrentIdAndKeepsTheBlob() throws Exception {
        StoredPlanner draft = TestDataFactory.planner(owner)
                .title("Backfill Draft")
                .content(TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT,
                        List.of("AccelBullet", "NotAKeyword")))
                .save(plannerSeeds);
        clearColumns(draft.id());

        runBackfill();

        assertThat(column("planner_content", draft.id())).isEqualTo("[\"9828\"]");
        assertThat(jdbc().queryForObject("SELECT JSON_EXTRACT(content, '$.selectedKeywords') FROM planner_content "
                        + "WHERE planner_id = UUID_TO_BIN(?)", String.class, draft.id().toString()))
                .contains("\"AccelBullet\"", "\"NotAKeyword\"");
        assertThat(indexedKeywords(draft.id())).isEmpty();
    }

    @Test
    void backfill_WhenTheColumnCarriesKeywordsTheContentDoesNot_ClearsTheColumnCatalogAndIndex() {
        StoredPlanner planner = publishedWithoutColumn("Backfill Stale Column", List.of());
        jdbc().update("UPDATE planner_content SET selected_keywords = '[\"Sinking\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.id().toString());
        jdbc().update("UPDATE planner_catalog SET selected_keywords = '[\"Sinking\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.id().toString());
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        assertThat(indexedKeywords(planner.id())).containsExactly("Sinking");

        runBackfill();

        assertThat(column("planner_content", planner.id())).isNull();
        assertThat(column("planner_catalog", planner.id())).isNull();
        assertThat(indexedKeywords(planner.id())).isEmpty();
    }

    @Test
    void backfill_WhenTheColumnStoresALegacyName_RewritesItToTheCurrentId() {
        StoredPlanner planner = publishedWithoutColumn("Backfill Legacy Column", List.of("ChargeLoad"));
        jdbc().update("UPDATE planner_content SET selected_keywords = '[\"ChargeLoad\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.id().toString());
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        assertThat(indexedKeywords(planner.id())).containsExactly("ChargeLoad");

        runBackfill();

        assertThat(column("planner_content", planner.id())).isEqualTo("[\"EmergencyChargeForceField\"]");
        assertThat(indexedKeywords(planner.id())).containsExactly("EmergencyChargeForceField");
    }

    @Test
    void backfill_WhenACatalogCopyIsStaleNextToACorrectColumn_RepairsTheCatalog() {
        StoredPlanner planner = publishWithFilters("Backfill Stale Catalog", Set.of("Burst"));
        jdbc().update("UPDATE planner_catalog SET selected_keywords = '[\"Sinking\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.id().toString());

        runBackfill();

        assertThat(column("planner_content", planner.id())).isEqualTo("[\"Burst\"]");
        assertThat(column("planner_catalog", planner.id())).isEqualTo("[\"Burst\"]");
        assertThat(indexedKeywords(planner.id())).containsExactly("Burst");
    }

    @Test
    void backfill_WhenRunTwice_CorrectsOnceAndThenChangesNothing() {
        StoredPlanner planner = publishedWithoutColumn("Backfill Rerun", List.of("Burst"));
        publishWithFilters("Backfill Already Consistent", Set.of("Sinking"));

        assertThat(runBackfill()).isEqualTo(1);
        assertThat(runBackfill()).isZero();
        assertThat(column("planner_content", planner.id())).isEqualTo("[\"Burst\"]");
    }

    @Test
    void backfill_WhenTheContentSelectionIsNotAnArray_ClearsTheColumn() {
        StoredPlanner planner = publishWithFilters("Backfill Not An Array", Set.of("Sinking"));
        jdbc().update("UPDATE planner_content SET content = JSON_SET(content, '$.selectedKeywords', 'none') "
                + "WHERE planner_id = UUID_TO_BIN(?)", planner.id().toString());

        runBackfill();

        assertThat(column("planner_content", planner.id())).isNull();
        assertThat(column("planner_catalog", planner.id())).isNull();
        assertThat(indexedKeywords(planner.id())).isEmpty();
    }

    @Test
    void backfill_WhenTheContentSelectionHoldsANonString_KeepsOnlyTheStringKeywords() {
        StoredPlanner planner = publishWithFilters("Backfill Non String", Set.of("Sinking"));
        jdbc().update("UPDATE planner_content SET content = JSON_SET(content, '$.selectedKeywords', "
                + "JSON_ARRAY(9828, 'Burst')) WHERE planner_id = UUID_TO_BIN(?)", planner.id().toString());

        runBackfill();

        assertThat(column("planner_content", planner.id())).isEqualTo("[\"Burst\"]");
        assertThat(indexedKeywords(planner.id())).containsExactly("Burst");
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
