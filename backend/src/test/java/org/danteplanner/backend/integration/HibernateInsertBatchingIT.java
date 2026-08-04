package org.danteplanner.backend.integration;

import jakarta.persistence.EntityManagerFactory;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.ImportPlannersRequest;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.repository.UserRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Write-path batching guard for {@link PlannerCommandService#importPlanners}.
 *
 * <p>Asserts that bulk-importing N planners issues a BOUNDED prepared-statement count
 * (~ceil(N / batch_size) INSERTs plus a small fixed overhead), NOT one prepared INSERT per
 * row. With no {@code hibernate.jdbc.batch_size} configured, Hibernate flushes one prepared
 * statement per row, so {@link Statistics#getPrepareStatementCount()} grows with N and the
 * bound is exceeded — this test is RED until write-insert batching is enabled globally.</p>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the measured service call runs in
 * its own transaction so the JDBC flush (and thus the real prepared-statement count) happens
 * inside the window between {@link Statistics#clear()} and the count read.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class HibernateInsertBatchingIT extends SharedMySqlContainerSupport {

    private static final int IMPORT_COUNT = 40;
    private static final int MD_CURRENT_VERSION = 7;
    private static final long BOUNDED_STATEMENT_THRESHOLD = 12;



    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        // Statistics is a per-context singleton and the count is a claim about every statement the
        // context issues, so this class needs a context no other class shares.
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "insert_batching");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerCommandService plannerCommandService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("importPlanners: prepared-statement count is bounded, not one INSERT per row")
    void importPlanners_WhenImportingManyPlanners_IssuesBoundedStatementCount() {
        Long userId = TestDataFactory
                .createTestUser(userRepository, UUID.randomUUID() + "@example.com")
                .getId();

        List<UpsertPlannerRequest> planners = new ArrayList<>();
        for (int i = 0; i < IMPORT_COUNT; i++) {
            planners.add(new UpsertPlannerRequest(
                    UUID.randomUUID().toString(),
                    "5F",
                    null,
                    null,
                    TestDataFactory.VALID_CONTENT,
                    MD_CURRENT_VERSION,
                    PlannerType.MIRROR_DUNGEON,
                    null,
                    null));
        }
        ImportPlannersRequest request = new ImportPlannersRequest(planners);

        statistics.clear();
        plannerCommandService.importPlanners(userId, request);
        long prepares = statistics.getPrepareStatementCount();

        assertThat(prepares)
                .as("importPlanners must batch INSERTs: %d planners should issue a bounded "
                        + "prepared-statement count, but observed %d (~one per row = unbatched)",
                        IMPORT_COUNT, prepares)
                .isLessThanOrEqualTo(BOUNDED_STATEMENT_THRESHOLD);
    }
}
