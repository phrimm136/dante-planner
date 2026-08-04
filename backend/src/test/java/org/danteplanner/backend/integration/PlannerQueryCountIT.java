package org.danteplanner.backend.integration;

import jakarta.persistence.EntityManagerFactory;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.CatalogQuery;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerBookmark;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N+1 guard for the list read-paths.
 *
 * <p>Locks in the prepared-statement count of the three filter shapes the published and
 * recommended endpoints compose — plain browse, recommended browse, and title search — both as an
 * absolute budget for a one-row page and as a slope against the number of result rows. A
 * regression that drops an {@code @EntityGraph}/JOIN FETCH or a batch {@code findBy...In} would
 * make the count grow with row count, failing the slope assertion.</p>
 *
 * <p>The database is this class's own: the measured calls are list reads over every published
 * planner, so a neighbour publishing between the two measurements adds rows to the same page and
 * with them the per-row {@code UserSettings} load, which is the slope the assertion bounds.</p>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: each measured service call runs in
 * its own fresh read-only transaction (a new Hibernate session), so the author and the
 * vote/bookmark/comment context must actually be fetched from the database rather than served
 * from a shared L1 cache — otherwise an N+1 would be masked and the assertion would be a tautology.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerQueryCountIT {

    private static final int SMALL_SET = 3;
    private static final int LARGE_SET = 8;
    // Page size must exceed the larger set so Spring Data skips the count query symmetrically
    // for both measurements (PageableExecutionUtils optimization), keeping the comparison about
    // N+1 only.
    private static final Pageable PAGE = PageRequest.of(0, 20);

    // A one-row page keeps the absolute counts independent of how many rows the class has
    // accumulated: the row-proportional part of the count is pinned at one, and the page is
    // always full so Spring Data always issues the count query.
    private static final Pageable ONE_ROW_PAGE = PageRequest.of(0, 1);

    // Seeded titles all carry this token so the title-search shape returns rows in this class's
    // own database.
    private static final String SEARCH_MARKER = "querycountmarker";

    // Page + count + core info + stats + votes + bookmarks.
    private static final long PUBLISHED_LIST_STATEMENTS = 6;
    private static final long RECOMMENDED_LIST_STATEMENTS = 6;
    private static final long SEARCH_LIST_STATEMENTS = 6;

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "query_count");
        // Enable Hibernate statistics so getPrepareStatementCount() reflects real SQL issued.
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
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
    private PlannerStatsRepository plannerStatsRepository;

    @Autowired
    private PlannerCatalogRepository plannerCatalogRepository;

    @Autowired
    private PlannerCatalogService plannerCatalogService;

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

        // A distinct viewer drives the authenticated read-path (vote + bookmark + comment batches).
        viewerId = TestDataFactory.createTestUser(userRepository, "viewer@example.com").getId();

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    /** The filter set the {@code /published} endpoint composes for a plain browse. */
    private static CatalogQuery publishedBrowse() {
        return new CatalogQuery(false, null, null, null, null);
    }

    /** The filter set the {@code /recommended} endpoint composes for a plain browse. */
    private static CatalogQuery recommendedBrowse() {
        return new CatalogQuery(true, null, null, null, null);
    }

    /** The filter set either endpoint composes for a title search. */
    private static CatalogQuery titleSearch() {
        return new CatalogQuery(false, null, SEARCH_MARKER, null, null);
    }

    @Test
    @DisplayName("published listing: statement count is constant across result-set sizes")
    void publishedListing_WhenResultSizeVaries_StatementCountConstant() {
        assertConstantStatementCount(() -> measure(publishedBrowse(), PAGE));
    }

    @Test
    @DisplayName("recommended listing: statement count is constant across result-set sizes")
    void recommendedListing_WhenResultSizeVaries_StatementCountConstant() {
        assertConstantStatementCount(() -> measure(recommendedBrowse(), PAGE), true);
    }

    @Test
    @DisplayName("search listing: statement count is constant across result-set sizes")
    void searchListing_WhenResultSizeVaries_StatementCountConstant() {
        assertConstantStatementCount(() -> measure(titleSearch(), PAGE));
    }

    @Test
    @DisplayName("published listing: a one-row page costs a fixed number of statements")
    void publishedListing_WhenOneRowPage_IssuesFixedStatementCount() {
        seedPlanners(SMALL_SET, false);
        assertFixedStatementCount(PlannerQueryCountIT::publishedBrowse, PUBLISHED_LIST_STATEMENTS);
    }

    @Test
    @DisplayName("recommended listing: a one-row page costs a fixed number of statements")
    void recommendedListing_WhenOneRowPage_IssuesFixedStatementCount() {
        seedPlanners(SMALL_SET, true);
        assertFixedStatementCount(PlannerQueryCountIT::recommendedBrowse, RECOMMENDED_LIST_STATEMENTS);
    }

    @Test
    @DisplayName("search listing: a one-row page costs a fixed number of statements")
    void searchListing_WhenOneRowPage_IssuesFixedStatementCount() {
        seedPlanners(SMALL_SET, false);
        assertFixedStatementCount(PlannerQueryCountIT::titleSearch, SEARCH_LIST_STATEMENTS);
    }

    private long measure(CatalogQuery catalogQuery, Pageable pageable) {
        statistics.clear();
        publishedPlannerQueryService.searchPlanners(catalogQuery, pageable, viewerId);
        return statistics.getPrepareStatementCount();
    }

    /**
     * Runs the listing once to discard one-time bootstrap statements, then asserts the second
     * run's statement count against the recorded budget.
     *
     * <p>The measured page must carry a row: an empty page skips the per-page batch loads, which
     * would make the budget describe a shape no endpoint serves.</p>
     */
    private void assertFixedStatementCount(Supplier<CatalogQuery> catalogQuery, long expected) {
        assertThat(publishedPlannerQueryService
                .searchPlanners(catalogQuery.get(), ONE_ROW_PAGE, viewerId).getContent())
                .as("the measured page carries a row")
                .isNotEmpty();
        long count = measure(catalogQuery.get(), ONE_ROW_PAGE);
        assertThat(count)
                .as("statement budget for a one-row page")
                .isEqualTo(expected);
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
            Planner planner = TestDataFactory.planner(author)
                    .title(SEARCH_MARKER + " planner")
                    .published(true)
                    .save(plannerRepository);
            plannerCatalogService.add(planner);
            seededIds.add(planner.getId());

            plannerVoteRepository.save(new PlannerVote(viewerId, planner.getId(), VoteType.UP));
            plannerBookmarkRepository.save(new PlannerBookmark(viewerId, planner.getId()));
            plannerCommentRepository.save(
                    new PlannerComment(planner.getId(), viewerId, "context comment", null, 0));
        }

        if (crossRecommendedThreshold) {
            // Lift each planner's upvote counter over the recommended threshold and refresh
            // the derived catalog flag so the recommended listing returns them.
            bumpUpvotesOverThreshold(seededIds);
        }
    }

    private void bumpUpvotesOverThreshold(List<UUID> plannerIds) {
        for (UUID id : plannerIds) {
            plannerStatsRepository.save(PlannerStats.builder()
                    .plannerId(id)
                    .upvotes(recommendedThreshold)
                    .build());
            plannerCatalogService.refreshRecommended(id);
        }
    }
}
