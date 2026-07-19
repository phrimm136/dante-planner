package org.danteplanner.backend.integration;

import jakarta.persistence.EntityManager;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sort seam: the public catalog no longer honours a {@code popular} (view-count) sort —
 * both {@code popular} and any unknown value collapse to {@code recent} (createdAt desc).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Transactional
@Import(TestConfig.class)
class PublishedPlannerSortIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "published_planner_sort_it");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private EntityManager entityManager;

    private User author;
    private UUID olderId;
    private UUID newerId;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        author = TestDataFactory.createTestUser(userRepository, "author@example.com");

        // createdAt order is inverse to viewCount order: the older planner has the higher
        // view count, so a genuine popular sort would surface it first, while recent must not.
        // Planner.onCreate() stamps created_at at persist, so a pre-persist setter is futile
        // and Thread.sleep is banned — override created_at with a native UPDATE post-persist.
        olderId = plannerRepository.save(planner("Older", 100)).getId();
        newerId = plannerRepository.save(planner("Newer", 1)).getId();
        entityManager.flush();
        overrideCreatedAt(olderId, Instant.now().minusSeconds(60));
        overrideCreatedAt(newerId, Instant.now());
        entityManager.clear();
    }

    private void overrideCreatedAt(UUID id, Instant value) {
        entityManager.createNativeQuery(
                        "UPDATE planners SET created_at = ? WHERE id = UUID_TO_BIN(?)")
                .setParameter(1, Timestamp.from(value))
                .setParameter(2, id.toString())
                .executeUpdate();
    }

    private Planner planner(String title, int viewCount) {
        return Planner.builder()
                .id(UUID.randomUUID())
                .user(author)
                .title(title)
                .category("5F")
                .status(PlannerStatus.SAVED)
                .content("{}")
                .schemaVersion(2)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(true)
                .viewCount(viewCount)
                .build();
    }

    @Test
    @DisplayName("sort_WhenPopularParam_FallsBackToRecent")
    void sort_WhenPopularParam_FallsBackToRecent() throws Exception {
        mockMvc.perform(get("/api/planner/md/published").param("sort", "popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(newerId.toString()))
                .andExpect(jsonPath("$.content[1].id").value(olderId.toString()));
    }

    @Test
    @DisplayName("sort_WhenUnknownParam_FallsBackToRecent")
    void sort_WhenUnknownParam_FallsBackToRecent() throws Exception {
        mockMvc.perform(get("/api/planner/md/published").param("sort", "bogus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(newerId.toString()))
                .andExpect(jsonPath("$.content[1].id").value(olderId.toString()));
    }
}
