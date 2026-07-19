package org.danteplanner.backend.integration;

import java.time.LocalDate;
import java.util.UUID;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch flush seam: draining many buffered views persists one row per distinct (planner, viewer,
 * day) and advances each planner's counters by exactly its new-view count, across planners and
 * with in-batch duplicates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Testcontainers
@Tag("containerized")
@Import(TestConfig.class)
class PlannerViewBatchFlushIT {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 1);

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
    private PlannerViewRecorder recorder;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        owner = TestDataFactory.createTestUser(userRepository, "batch-owner@example.com");
    }

    private UUID newPlanner() {
        return TestDataFactory.createTestPlanner(plannerRepository, owner, true).getId();
    }

    private int viewCount(UUID plannerId) {
        return plannerRepository.findById(plannerId).orElseThrow().getViewCount();
    }

    private int statsViewCount(UUID plannerId) {
        Integer v = jdbcTemplate.queryForObject(
                "SELECT view_count FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
        return v == null ? 0 : v;
    }

    private int viewRows(UUID plannerId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM planner_views WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
        return c == null ? -1 : c;
    }

    @Test
    @DisplayName("flush_WhenDistinctViewersSamePlanner_AdvancesByCount")
    void flush_WhenDistinctViewersSamePlanner_AdvancesByCount() {
        UUID plannerId = newPlanner();

        recorder.record(plannerId, "viewer-a", DAY);
        recorder.record(plannerId, "viewer-b", DAY);
        recorder.record(plannerId, "viewer-c", DAY);
        recorder.flush();

        assertThat(viewRows(plannerId)).as("one row per distinct viewer").isEqualTo(3);
        assertThat(viewCount(plannerId)).as("legacy counter advances by the batch").isEqualTo(3);
        assertThat(statsViewCount(plannerId)).as("stats counter advances by the batch").isEqualTo(3);
    }

    @Test
    @DisplayName("flush_WhenViewsAcrossPlanners_AdvancesEachIndependently")
    void flush_WhenViewsAcrossPlanners_AdvancesEachIndependently() {
        UUID first = newPlanner();
        UUID second = newPlanner();

        recorder.record(first, "viewer-a", DAY);
        recorder.record(second, "viewer-a", DAY);
        recorder.record(second, "viewer-b", DAY);
        recorder.flush();

        assertThat(viewCount(first)).isEqualTo(1);
        assertThat(viewCount(second)).isEqualTo(2);
        assertThat(statsViewCount(first)).isEqualTo(1);
        assertThat(statsViewCount(second)).isEqualTo(2);
    }

    @Test
    @DisplayName("flush_WhenDuplicateInBatch_CountsDistinctOnly")
    void flush_WhenDuplicateInBatch_CountsDistinctOnly() {
        UUID plannerId = newPlanner();

        recorder.record(plannerId, "viewer-a", DAY);
        recorder.record(plannerId, "viewer-a", DAY);
        recorder.record(plannerId, "viewer-b", DAY);
        recorder.flush();

        assertThat(viewRows(plannerId)).isEqualTo(2);
        assertThat(viewCount(plannerId)).isEqualTo(2);
        assertThat(statsViewCount(plannerId)).isEqualTo(2);
    }
}
