package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Published-list projection seam: the public catalog excludes non-public planners
 * and every summary field is populated for a fully-populated published planner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PublishedPlannerListIT extends SharedMySqlContainerSupport {





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
    @DisplayName("list_WhenSeeded_ShowsOnlyPublished")
    void list_WhenSeeded_ShowsOnlyPublished() throws Exception {
        Planner p1 = plannerRepository.save(base("P1").published(true).build());
        catalogService.add(p1);

        Planner p2 = plannerRepository.save(base("P2").published(true).build());
        p2.takeDown();
        plannerRepository.save(p2);

        Planner p3 = base("P3").published(true).build();
        p3.softDelete();
        plannerRepository.save(p3);

        plannerRepository.save(base("P4").published(false).build());

        mockMvc.perform(get("/api/planner/md/published").param("q", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(p1.getId().toString()));
    }

    @Test
    @DisplayName("list_WhenSummaryProjected_MatchesEntityFields")
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
}
