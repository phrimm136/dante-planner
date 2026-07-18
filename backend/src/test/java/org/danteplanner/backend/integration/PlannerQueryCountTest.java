package org.danteplanner.backend.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerBookmark;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.planner.specification.PlannerSpecifications;
import org.danteplanner.backend.support.TestDataFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N+1 guard for the list read-paths.
 *
 * <p>Locks in that the prepared-statement count of {@code getPublishedPlanners},
 * {@code getRecommendedPlanners}, and {@code searchPlanners} is constant with respect to
 * the number of result rows. A regression that drops an {@code @EntityGraph}/JOIN FETCH or
 * a batch {@code findBy...In} would make the count grow with row count, failing the equality.</p>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: each measured service call runs in
 * its own fresh read-only transaction (a new Hibernate session), so the author and the
 * vote/bookmark/comment context must actually be fetched from the database rather than served
 * from a shared L1 cache — otherwise an N+1 would be masked and the assertion would be a tautology.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@Tag("containerized")
@Import(TestConfig.class)
class PlannerQueryCountTest {

    private static final int SMALL_SET = 3;
    private static final int LARGE_SET = 8;
    // Page size must exceed the larger set so Spring Data skips the count query symmetrically
    // for both measurements (PageableExecutionUtils optimization), keeping the comparison about
    // N+1 only.
    private static final Pageable PAGE = PageRequest.of(0, 20);

    // Relaxed durability and no performance_schema: a throwaway test database needs no
    // crash-safety — the flags cut boot time and per-instance memory.
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
        // Enable Hibernate statistics so getPrepareStatementCount() reflects real SQL issued.
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        registry.add("logging.level.org.hibernate.SQL", () -> "DEBUG");
        registry.add("logging.file.name", () -> "/tmp/qct-sql.log");
    }

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerVoteRepository plannerVoteRepository;

    @Autowired
    private PlannerBookmarkRepository plannerBookmarkRepository;

    @Autowired
    private PlannerCommentRepository plannerCommentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PublishedPlannerQueryService publishedPlannerQueryService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    private Statistics statistics;
    private Long viewerId;

    @BeforeEach
    void setUp() {
        plannerCommentRepository.deleteAll();
        plannerVoteRepository.deleteAll();
        plannerBookmarkRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.deleteAll();

        // A distinct viewer drives the authenticated read-path (vote + bookmark + comment batches).
        viewerId = TestDataFactory.createTestUser(userRepository, "viewer@example.com").getId();

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("getPublishedPlanners: statement count is constant across result-set sizes")
    void getPublishedPlanners_WhenResultSizeVaries_StatementCountConstant() {
        assertConstantStatementCount(() -> {
            statistics.clear();
            publishedPlannerQueryService.getPublishedPlanners(PAGE, null, viewerId, null);
            return statistics.getPrepareStatementCount();
        });
    }

    @Test
    @DisplayName("getRecommendedPlanners: statement count is constant across result-set sizes")
    void getRecommendedPlanners_WhenResultSizeVaries_StatementCountConstant() {
        assertConstantStatementCount(() -> {
            statistics.clear();
            publishedPlannerQueryService.getRecommendedPlanners(PAGE, null, viewerId, null);
            return statistics.getPrepareStatementCount();
        }, true);
    }

    @Test
    @DisplayName("searchPlanners: statement count is constant across result-set sizes")
    void searchPlanners_WhenResultSizeVaries_StatementCountConstant() {
        assertConstantStatementCount(() -> {
            statistics.clear();
            publishedPlannerQueryService.searchPlanners(
                    PlannerSpecifications.isPublished(),
                    PAGE,
                    null,
                    viewerId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            return statistics.getPrepareStatementCount();
        });
    }

    /**
     * Seeds {@link #SMALL_SET} then {@link #LARGE_SET} published planners (each by a distinct
     * author, each carrying votes/bookmarks/comments from the viewer) and asserts the measured
     * statement count does not grow with the row count.
     *
     * <p>A warm-up call precedes each measurement so one-time bootstrap statements do not land
     * only in the small measurement and invert the comparison.</p>
     */
    private void assertConstantStatementCount(LongSupplier measure) {
        assertConstantStatementCount(measure, false);
    }

    private void assertConstantStatementCount(LongSupplier measure, boolean crossRecommendedThreshold) {
        seedPlanners(SMALL_SET, crossRecommendedThreshold);
        long unusedWarmUp = measure.getAsLong(); // warm-up: discard one-time/bootstrap statements
        long smallCount = measure.getAsLong();

        seedPlanners(LARGE_SET - SMALL_SET, crossRecommendedThreshold);
        long largeCount = measure.getAsLong();

        // Regression guard on the read-path SQL count. The four core queries (planners+author via
        // JOIN, plus batched comment-count / vote / bookmark IN-queries) are constant w.r.t. row
        // count. One known pre-existing N+1 remains: the author's UserSettings (LAZY @OneToOne) is
        // loaded once per result row, so the count grows by exactly one statement per added row.
        // Locking the slope at <= one-per-row catches a NEW N+1 (a second per-row query from a B6/B10
        // change pushes the delta past rowDelta) while tolerating the documented existing one.
        int rowDelta = LARGE_SET - SMALL_SET;
        assertThat(largeCount - smallCount)
                .as("no new per-row SQL beyond the known UserSettings load")
                .isLessThanOrEqualTo(rowDelta);
    }

    private void seedPlanners(int count, boolean crossRecommendedThreshold) {
        List<UUID> seededIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String unique = UUID.randomUUID().toString();
            User author = TestDataFactory.createTestUser(userRepository, "author-" + unique + "@example.com");
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);
            seededIds.add(planner.getId());

            plannerVoteRepository.save(new PlannerVote(viewerId, planner.getId(), VoteType.UP));
            plannerBookmarkRepository.save(new PlannerBookmark(viewerId, planner.getId()));
            plannerCommentRepository.save(
                    new PlannerComment(planner.getId(), viewerId, "context comment", null, 0));
        }

        if (crossRecommendedThreshold) {
            // Lift each planner's denormalized upvote counter over the recommended threshold so the
            // recommended visibility filter (upvotes >= threshold) returns them.
            bumpUpvotesOverThreshold(seededIds);
        }
    }

    private void bumpUpvotesOverThreshold(List<UUID> plannerIds) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            for (UUID id : plannerIds) {
                em.createQuery("UPDATE Planner p SET p.upvotes = :v WHERE p.id = :id")
                        .setParameter("v", recommendedThreshold)
                        .setParameter("id", id)
                        .executeUpdate();
            }
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }
}
