package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.danteplanner.backend.support.TestDataCleanup;

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
class PlannerTitleSearchIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "planner_title_search_it");
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
    private PlannerFilterService filterService;

    private User owner;

    @BeforeEach
    void setUp() {
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
        entityFilterRepository.deleteAll();
        keywordFilterRepository.deleteAll();
        catalogRepository.deleteAll();
        statsRepository.deleteAll();
        plannerRepository.deleteAll();
        TestDataCleanup.deleteUsersExceptSentinel(userRepository);
    }

    private void publish(String title, Set<String> keywords) {
        Planner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(keywords)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        filterService.rebuildFilters(planner.getId());
    }

    @Test
    @DisplayName("title-search-ngram: substrings of two or more characters match, Latin and CJK")
    void titleSearchNgram_WhenSubstringQueried_MatchesLatinAndCjk() throws Exception {
        mockMvc.perform(get("/api/planner/md/published").param("q", "eed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Bleed Team"));

        mockMvc.perform(get("/api/planner/md/published").param("q", "출혈"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("출혈 덱 공략"));
    }

    @Test
    @DisplayName("title-search-ngram: multi-word input matches regardless of word order")
    void titleSearchNgram_WhenWordsReordered_StillMatches() throws Exception {
        mockMvc.perform(get("/api/planner/md/published").param("q", "team bleed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Bleed Team"));
    }

    @Test
    @DisplayName("title-search-ngram: input shorter than the ngram token size matches nothing")
    void titleSearchNgram_WhenSingleChar_MatchesNothing() throws Exception {
        mockMvc.perform(get("/api/planner/md/published").param("q", "e"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("title-search-ngram: boolean-mode operators in the input are inert, not syntax")
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

    @Test
    @DisplayName("q-matches-title-or-keyword: a keyword-only match is returned alongside title search")
    void qMatchesTitleOrKeyword_WhenKeywordOnlyMatch_Returned() throws Exception {
        // "Kit Collection" has no 'sinking' in its title — only the keyword
        mockMvc.perform(get("/api/planner/md/published").param("q", "Sinking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Kit Collection"));
    }
}
