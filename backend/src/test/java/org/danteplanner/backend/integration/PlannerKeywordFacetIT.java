package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.danteplanner.backend.support.TestDataCleanup;

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
class PlannerKeywordFacetIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "planner_keyword_facet_it");
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

    private User owner;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "facet-owner@example.com");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        entityFilterRepository.deleteAll();
        keywordFilterRepository.deleteAll();
        catalogRepository.deleteAll();
        statsRepository.deleteAll();
        plannerRepository.deleteAll();
        TestDataCleanup.deleteUsersExceptSentinel(userRepository);
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
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("With Entities"));

        mockMvc.perform(get("/api/planner/md/published").param("themePack", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("With Entities"));

        mockMvc.perform(get("/api/planner/md/published").param("identity", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("keyword-facet-filter: only planners whose keyword index contains the keyword are returned")
    void keywordFacetFilter_WhenFiltered_ReturnsOnlyMatchingPlanners() throws Exception {
        publishWithFilters("Sinking Build", Set.of("Sinking"));
        publishWithFilters("Burn Build", Set.of("Combustion"));

        mockMvc.perform(get("/api/planner/md/published").param("keyword", "Sinking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Sinking Build"));

        mockMvc.perform(get("/api/planner/md/published").param("keyword", "DawnTeam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("keyword-rename-normalized-on-write: a legacy client name is stored, indexed, and filterable as the current id; unknown keywords drop without failing the save")
    void keywordRenameNormalizedOnWrite_WhenLegacyNameSynced_StoredAsCurrentId() throws Exception {
        Planner planner = publishWithFilters("Legacy Keywords", Set.of("Sinking"));

        // A stale client syncs the pre-rename name plus an unknown keyword
        UpsertPlannerRequest req = new UpsertPlannerRequest(
                planner.getId().toString(), null, null, null, null, null,
                PlannerType.MIRROR_DUNGEON, planner.getSyncVersion(),
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
                .andExpect(jsonPath("$.totalElements").value(1))
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
}
