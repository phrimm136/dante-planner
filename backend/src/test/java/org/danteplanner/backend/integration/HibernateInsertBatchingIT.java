package org.danteplanner.backend.integration;

import jakarta.persistence.EntityManagerFactory;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.ImportPlannersRequest;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.repository.UserRepository;
import com.redis.testcontainers.RedisContainer;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

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
@Testcontainers
@Tag("containerized")
@Import(TestConfig.class)
class HibernateInsertBatchingIT {

    private static final int IMPORT_COUNT = 40;
    private static final int MD_CURRENT_VERSION = 7;
    private static final long BOUNDED_STATEMENT_THRESHOLD = 12;

    private static final String VALID_CONTENT = """
        {
            "selectedKeywords":[],
            "selectedBuffIds":[100,201],
            "selectedGiftKeyword":"Combustion",
            "selectedGiftIds":["9001"],
            "equipment":{
                "01":{"identity":{"id":"10101","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20101","threadspin":4}}},
                "02":{"identity":{"id":"10201","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20201","threadspin":4}}},
                "03":{"identity":{"id":"10301","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20301","threadspin":4}}},
                "04":{"identity":{"id":"10401","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20401","threadspin":4}}},
                "05":{"identity":{"id":"10501","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20501","threadspin":4}}},
                "06":{"identity":{"id":"10601","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20601","threadspin":4}}},
                "07":{"identity":{"id":"10701","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20701","threadspin":4}}},
                "08":{"identity":{"id":"10801","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20801","threadspin":4}}},
                "09":{"identity":{"id":"10901","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20901","threadspin":4}}},
                "10":{"identity":{"id":"11001","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21001","threadspin":4}}},
                "11":{"identity":{"id":"11101","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21101","threadspin":4}}},
                "12":{"identity":{"id":"11201","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21201","threadspin":4}}}
            },
            "deploymentOrder":[0,1,2,3,4,5],
            "floorSelections":[{"themePackId":"1001","difficulty":0,"giftIds":["9002"]}],
            "sectionNotes":{}
        }
        """.trim().replace("\n", "").replace(" ", "");

    @Container
    static MySQLContainer mysqlContainer = new MySQLContainer("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RedisContainer redisContainer = new RedisContainer("redis:7-alpine");

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.flyway.user", mysqlContainer::getUsername);
        registry.add("spring.flyway.password", mysqlContainer::getPassword);
        registry.add("redis.auth.host", redisContainer::getRedisHost);
        registry.add("redis.auth.port", redisContainer::getRedisPort);
        registry.add("redis.rate-limit.host", redisContainer::getRedisHost);
        registry.add("redis.rate-limit.port", redisContainer::getRedisPort);
        registry.add("redis.sse-local.host", redisContainer::getRedisHost);
        registry.add("redis.sse-local.port", redisContainer::getRedisPort);
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
        userRepository.deleteAll();
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
                    VALID_CONTENT,
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
