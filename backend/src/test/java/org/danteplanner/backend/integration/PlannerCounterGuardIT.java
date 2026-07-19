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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counter-guard seam: the denormalized {@code view_count} column is
 * {@code updatable=false}, so a stale entity flush cannot clobber a concurrent bulk
 * increment. Loaded at N, bulk-incremented to N+1, then flushed — the increment survives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Testcontainers
@Tag("containerized")
@Import(TestConfig.class)
@Transactional
class PlannerCounterGuardIT {

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
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private EntityManager entityManager;

    private User author;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        author = TestDataFactory.createTestUser(userRepository, "author@example.com");
    }

    @Test
    @DisplayName("entitySave_WhenBulkIncremented_PreservesCounters")
    void entitySave_WhenBulkIncremented_PreservesCounters() {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(author)
                .title("Counter Guard")
                .category("5F")
                .status(PlannerStatus.SAVED)
                .content("{}")
                .schemaVersion(2)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(true)
                .viewCount(5)
                .build();
        UUID id = plannerRepository.saveAndFlush(planner).getId();
        entityManager.clear();

        // Load the entity at N=5, then let a bulk increment commit N+1=6 (this also
        // detaches the loaded entity via clearAutomatically).
        Planner loaded = plannerRepository.findById(id).orElseThrow();
        assertThat(loaded.getViewCount()).isEqualTo(5);

        plannerRepository.incrementViewCount(id);

        // Flushing the stale entity must not write its N=5 view_count back over the increment.
        loaded.setTitle("touched after increment");
        plannerRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        Planner reloaded = plannerRepository.findById(id).orElseThrow();
        assertThat(reloaded.getViewCount()).isEqualTo(6);
    }
}
