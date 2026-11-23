package org.danteplanner.backend.integration;

import com.jayway.jsonpath.JsonPath;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Published-list projection seam: the public catalog excludes non-public planners
 * and every summary field is populated for a fully-populated published planner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PublishedPlannerListIT extends SharedMySqlContainerSupport {

    private static final List<String> LIST_PATHS =
            List.of("/api/planner/md/published", "/api/planner/md/recommended");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository plannerStatsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User author;

    @BeforeEach
    void setUp() {
        author = TestDataFactory.createTestUser(userRepository, "author@example.com");
    }

    /**
     * Titles carry a per-test marker so a list query can be narrowed to rows this test created.
     * The endpoint answers with every published planner, so an unnarrowed count is a claim about
     * the table, true only while the test runs alone.
     */
    private final String marker = "m" + UUID.randomUUID().toString().replace("-", "");

    /** The stored title, as the fixture composes it. */
    private String titled(String title) {
        return marker + " " + title;
    }

    private TestDataFactory.PlannerBuilder base(String title) {
        return TestDataFactory.planner(author)
                .title(titled(title))
                .category("5F")
                .status(PlannerStatus.SAVED)
                .content("{}")
                .schemaVersion(2)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON);
    }

    @Test
    void list_WhenSeeded_ShowsOnlyPublished() throws Exception {
        Planner p1 = plannerRepository.save(base("P1").published(true).build());
        catalogService.add(p1);

        Planner p2 = plannerRepository.save(base("P2").published(true).build());
        p2.takeDown(Instant.now());
        plannerRepository.save(p2);

        Planner p3 = base("P3").published(true).build();
        p3.softDelete(Instant.now());
        plannerRepository.save(p3);

        plannerRepository.save(base("P4").published(false).build());

        mockMvc.perform(get("/api/planner/md/published").param("q", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(p1.getId().toString()));
    }

    @Test
    void list_WhenSummaryProjected_MatchesEntityFields() throws Exception {
        Planner p = base("Field Parity")
                .published(true)
                .selectedKeywords(Set.of("Burst", "Sinking"))
                .build();
        p = plannerRepository.save(p);
        plannerStatsRepository.save(PlannerStats.builder()
                .plannerId(p.getId())
                .upvotes(7)
                .viewCount(42)
                .build());
        catalogService.add(p);

        mockMvc.perform(get("/api/planner/md/published").param("q", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(p.getId().toString()))
                .andExpect(jsonPath("$.content[0].title").value(titled("Field Parity")))
                .andExpect(jsonPath("$.content[0].category").value("5F"))
                .andExpect(jsonPath("$.content[0].plannerType").value("MIRROR_DUNGEON"))
                .andExpect(jsonPath("$.content[0].selectedKeywords").isNotEmpty())
                .andExpect(jsonPath("$.content[0].authorUsernameEpithet").isNotEmpty())
                .andExpect(jsonPath("$.content[0].authorUsernameSuffix").isNotEmpty())
                .andExpect(jsonPath("$.content[0].upvotes").value(7))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].viewCount").value(42))
                .andExpect(jsonPath("$.content[0].firstPublishedAt").isNotEmpty());
    }

    private Planner listed(String title, String category, Set<String> keywords) {
        Planner planner = plannerRepository.save(
                base(title).category(category).selectedKeywords(keywords).published(true).build());
        catalogService.add(planner);
        return planner;
    }

    private Planner listedWithKeywords(String title, Set<String> keywords) {
        Planner planner = listed(title, "5F", keywords);
        transactionTemplate.executeWithoutResult(status -> catalogService.syncFilterRows(planner.getId()));
        return planner;
    }

    private List<String> listedIds(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.content[*].id");
    }

    private MockHttpServletRequestBuilder publishedMarked() {
        return get("/api/planner/md/published").param("q", marker);
    }

    @Test
    void list_WhenTheCategoryIsEmpty_FiltersByTheEmptyCategory() throws Exception {
        listed("Listed", "5F", Set.of("Burst"));

        assertThat(listedIds(publishedMarked().param("category", ""))).isEmpty();
    }

    @Test
    void list_WhenTheSearchTermIsBlank_DoesNotFilterByIt() throws Exception {
        String category = UUID.randomUUID().toString();
        Planner listed = listed("Listed", category, Set.of("Burst"));

        assertThat(listedIds(get("/api/planner/md/published").param("q", "  ").param("category", category)))
                .containsExactly(listed.getId().toString());
    }

    @Test
    void list_WhenTheCategoryIsRepeated_FiltersByTheJoinedText() throws Exception {
        listed("Five", "5F", Set.of("Burst"));
        listed("Ten", "10F", Set.of("Burst"));
        Planner joined = listed("Joined", "5F,10F", Set.of("Burst"));

        assertThat(listedIds(publishedMarked().param("category", "5F", "10F")))
                .containsExactly(joined.getId().toString());
    }

    @Test
    void list_WhenTheKeywordIsRepeated_RequiresEveryKeyword() throws Exception {
        listedWithKeywords("One", Set.of("Burst"));
        Planner both = listedWithKeywords("Both", Set.of("Burst", "Sinking"));

        assertThat(listedIds(publishedMarked().param("keyword", "Burst", "Sinking")))
                .containsExactly(both.getId().toString());
    }

    @Test
    void list_WhenTheKeywordIsEmptyOrBlank_DoesNotFilterByIt() throws Exception {
        Planner listed = listed("Listed", "5F", Set.of("Burst"));

        for (String keyword : List.of("", "  ", ",", " , ")) {
            assertThat(listedIds(publishedMarked().param("keyword", keyword)))
                    .as("keyword '%s'", keyword)
                    .containsExactly(listed.getId().toString());
        }
    }

    @Test
    void list_WhenEntityFiltersAreBlank_DoesNotFilterByThem() throws Exception {
        Planner listed = listed("Listed", "5F", Set.of("Burst"));

        assertThat(listedIds(publishedMarked()
                .param("identity", "").param("ego", "  ").param("gift", ",").param("themePack", "")))
                .containsExactly(listed.getId().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"identity", "ego", "gift", "themePack"})
    void list_WhenAnEntityFilterNamesAnUnusedId_ListsNothing(String filter) throws Exception {
        listed("Listed", "5F", Set.of("Burst"));

        assertThat(listedIds(publishedMarked().param(filter, "99999999"))).isEmpty();
    }

    @Test
    void list_WhenAHeaderCarriesAParametersName_DoesNotReadIt() throws Exception {
        Planner listed = listed("Listed", "5F", Set.of("Burst"));

        assertThat(listedIds(publishedMarked()
                .header("Category", "10F").header("Keyword", "Sinking").header("Identity", "99999999")
                .header("Page", "7").header("Size", "1")))
                .containsExactly(listed.getId().toString());
    }

    @ParameterizedTest
    @CsvSource(nullValues = "ABSENT", value = {
            "ABSENT, ABSENT, 0, 20",
            "'', '', 0, 20",
            "0, 1000, 0, 100",
            "3, 100, 3, 100",
            "' 2 ', ' 7 ', 2, 7",
    })
    void list_WhenPageOrSizeIsAbsentEmptyOrLarge_AppliesTheDefaultsAndTheCap(
            String page, String size, int expectedPage, int expectedSize) throws Exception {
        for (String path : LIST_PATHS) {
            MockHttpServletRequestBuilder request = get(path).param("q", marker);
            if (page != null) {
                request.param("page", page);
            }
            if (size != null) {
                request.param("size", size);
            }
            mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.number").value(expectedPage))
                    .andExpect(jsonPath("$.page.size").value(expectedSize));
        }
    }

    @Test
    void list_WhenPageAndSizeAreRepeated_ReadsTheFirstValue() throws Exception {
        for (String path : LIST_PATHS) {
            mockMvc.perform(get(path).param("q", marker).param("page", "1", "2").param("size", "3", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.number").value(1))
                    .andExpect(jsonPath("$.page.size").value(3));
        }
    }

    @ParameterizedTest
    @CsvSource({"page, x", "size, x", "page, '  '", "size, '  '", "page, 1.5", "size, 99999999999",
            "page, -1", "size, 0", "size, -1"})
    void list_WhenPageOrSizeIsNotInRange_Answers400(String parameter, String value) throws Exception {
        for (String path : LIST_PATHS) {
            mockMvc.perform(get(path).param(parameter, value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.detail").value("Invalid parameter format"));
        }
    }

    @Test
    void list_WhenTheFirstOfRepeatedPagesOrSizesIsEmpty_Answers400() throws Exception {
        for (String path : LIST_PATHS) {
            for (String parameter : List.of("page", "size")) {
                mockMvc.perform(get(path).param(parameter, "", "2"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.detail").value("Invalid parameter format"));
            }
        }
    }
}
