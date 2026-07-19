package org.danteplanner.backend.integration;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

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
@Testcontainers
@Tag("containerized")
@Transactional
@Import(TestConfig.class)
class PublishedPlannerListIT {

    @Container
    static MySQLContainer mysqlContainer = new MySQLContainer("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                    "--innodb-flush-log-at-trx-commit=0",
                    "--sync-binlog=0",
                    "--performance-schema=OFF",
                    "--skip-name-resolve");

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.flyway.user", mysqlContainer::getUsername);
        registry.add("spring.flyway.password", mysqlContainer::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    private User author;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        author = TestDataFactory.createTestUser(userRepository, "author@example.com");
    }

    private Planner.PlannerBuilder base(String title) {
        return Planner.builder()
                .id(UUID.randomUUID())
                .user(author)
                .title(title)
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

        Planner p2 = plannerRepository.save(base("P2").published(true).build());
        p2.takeDown();
        plannerRepository.save(p2);

        Planner p3 = base("P3").published(true).build();
        p3.softDelete();
        plannerRepository.save(p3);

        plannerRepository.save(base("P4").published(false).build());

        mockMvc.perform(get("/api/planner/md/published"))
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
                .upvotes(7)
                .viewCount(42)
                .build();
        p = plannerRepository.save(p);

        mockMvc.perform(get("/api/planner/md/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(p.getId().toString()))
                .andExpect(jsonPath("$.content[0].title").value("Field Parity"))
                .andExpect(jsonPath("$.content[0].category").value("5F"))
                .andExpect(jsonPath("$.content[0].contentVersion").value(6))
                .andExpect(jsonPath("$.content[0].plannerType").value("MIRROR_DUNGEON"))
                .andExpect(jsonPath("$.content[0].selectedKeywords").isNotEmpty())
                .andExpect(jsonPath("$.content[0].authorUsernameEpithet").isNotEmpty())
                .andExpect(jsonPath("$.content[0].authorUsernameSuffix").isNotEmpty())
                .andExpect(jsonPath("$.content[0].upvotes").value(7))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].viewCount").value(42))
                .andExpect(jsonPath("$.content[0].lastModifiedAt").isNotEmpty());
    }
}
