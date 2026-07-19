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
 * Stats dual-write seam: a counter increment advances both the legacy planners column and the
 * planner_stats store, and the reconciliation checksum stays clean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Testcontainers
@Tag("containerized")
@Import(TestConfig.class)
class PlannerStatsDualWriteIT {

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

    private UUID plannerId;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        User owner = TestDataFactory.createTestUser(userRepository, "stats-owner@example.com");
        plannerId = TestDataFactory.createTestPlanner(plannerRepository, owner, true).getId();
        // A backfilled stats row, consistent with the legacy counters.
        jdbcTemplate.update(
                "INSERT INTO planner_stats (planner_id, view_count, upvotes) VALUES (UUID_TO_BIN(?), ?, ?)",
                plannerId.toString(), legacyViewCount(), 0);
    }

    private int legacyViewCount() {
        return plannerRepository.findById(plannerId).orElseThrow().getViewCount();
    }

    private int statsViewCount() {
        Integer v = jdbcTemplate.queryForObject(
                "SELECT view_count FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
        return v == null ? -1 : v;
    }

    private long checksumDiff() {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM planners p LEFT JOIN planner_stats s ON s.planner_id = p.id "
                        + "WHERE s.planner_id IS NULL OR p.view_count <> s.view_count OR p.upvotes <> s.upvotes",
                Long.class);
        return c == null ? -1 : c;
    }

    @Test
    @DisplayName("statsIncrement_WhenDualWrite_AdvancesBothStores")
    void statsIncrement_WhenDualWrite_AdvancesBothStores() {
        int legacyBefore = legacyViewCount();
        int statsBefore = statsViewCount();

        recorder.record(plannerId, "viewer-stats-1", LocalDate.of(2026, 2, 1));
        recorder.flush();

        assertThat(legacyViewCount() - legacyBefore).as("legacy counter advances").isEqualTo(1);
        assertThat(statsViewCount() - statsBefore).as("stats counter advances").isEqualTo(1);
        assertThat(checksumDiff()).as("reconciliation checksum stays clean").isZero();
    }
}
