package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCatalogSyncService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Free-text search over the catalog: ngram FULLTEXT on titles (substrings of
 * two or more characters, CJK included, order-independent) OR exact membership
 * in the keyword index; boolean-mode operators in the input are inert.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerTitleSearchIT {

    @DynamicPropertySource
    static void ownIndex(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "title_search");
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
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerCatalogSyncService catalogSyncService;

    @Autowired
    private DataSource dataSource;

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
        owner = TestDataFactory.createTestUser(userRepository, "search-owner@example.com");
        publish("Bleed Team", null);
        publish("출혈 덱 공략", null);
        publish("Kit Collection", Set.of("Sinking"));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
    }

    private void publish(String title, Set<String> keywords) {
        Planner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(keywords)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        catalogSyncService.syncPlanner(planner.getId(), List.of());
    }

    // title-search-ngram
    @Test
    void titleSearchNgram_WhenSubstringQueried_MatchesLatinAndCjk() throws Exception {
        mockMvc.perform(get("/api/planner/md/published").param("q", "eed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Bleed Team"));

        mockMvc.perform(get("/api/planner/md/published").param("q", "출혈"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("출혈 덱 공략"));
    }

    // title-search-ngram
    @Test
    void titleSearchNgram_WhenWordsReordered_StillMatches() throws Exception {
        mockMvc.perform(get("/api/planner/md/published").param("q", "team bleed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Bleed Team"));
    }

    // title-search-ngram
    @Test
    void titleSearchNgram_WhenSingleChar_MatchesNothing() throws Exception {
        mockMvc.perform(get("/api/planner/md/published").param("q", "e"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // title-search-ngram
    @Test
    void titleSearchNgram_WhenOperatorsInInput_TreatedAsText() throws Exception {
        // A dangling double quote is a boolean-mode syntax error when unescaped
        mockMvc.perform(get("/api/planner/md/published").param("q", "\"eed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Bleed Team"));

        // Without escaping, -team would EXCLUDE the Bleed Team row
        mockMvc.perform(get("/api/planner/md/published").param("q", "eed -team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Bleed Team"));
    }

    // q-matches-title-or-keyword
    @Test
    void qMatchesTitleOrKeyword_WhenKeywordOnlyMatch_Returned() throws Exception {
        // "Kit Collection" has no 'sinking' in its title — only the keyword
        mockMvc.perform(get("/api/planner/md/published").param("q", "Sinking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Kit Collection"));
    }

    // list-recency-sort
    @Test
    void listRecencySort_WhenBrowsing_NewestFirstFilesortFree() throws Exception {
        catalogRepository.deleteAll();
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            Planner planner = TestDataFactory.planner(owner)
                    .title("Recency " + i)
                    .published(true)
                    .firstPublishedAt(now.minus(3 - i, ChronoUnit.HOURS))
                    .save(plannerRepository);
            statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
            catalogService.add(planner);
        }

        mockMvc.perform(get("/api/planner/md/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Recency 2", "Recency 1", "Recency 0")));

        String plan = new JdbcTemplate(dataSource).queryForList(
                        "EXPLAIN SELECT planner_id FROM planner_catalog "
                                + "ORDER BY first_published_at DESC LIMIT 20")
                .toString();
        assertThat(plan)
                .as("recency browse rides idx_catalog_recent without a filesort: %s", plan)
                .doesNotContain("Using filesort");
    }
}
