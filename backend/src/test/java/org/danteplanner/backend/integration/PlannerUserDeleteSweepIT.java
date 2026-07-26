package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerFilterService;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.ZoneOffset;

/**
 * User hard-delete sweep: the FK-less satellite, projection, and filter rows
 * are removed app-side by planner id, the core cascade takes the FK-bearing
 * children, and nothing planner-related survives the account.
 *
 * <p>The rows the deleted account cast elsewhere run the other way: votes are
 * reassigned to the sentinel account so they outlive the {@code ON DELETE CASCADE}
 * that the users row would otherwise carry them off with.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerUserDeleteSweepIT {

    /**
     * Its own database: the sweep hard-deletes by criteria over the whole users table, so in the
     * shared one it removes rows belonging to every concurrently running class.
     */
    @DynamicPropertySource
    static void ownDatabase(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "user_delete_sweep");
    }


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerFilterService filterService;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "sweep-owner@example.com");
        other = TestDataFactory.createTestUser(userRepository, "sweep-other@example.com");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        for (String table : List.of(
                "planner_reports", "planner_subscriptions", "planner_bookmarks",
                "planner_comment_votes", "planner_comments", "planner_views", "planner_votes",
                "planner_entity_filter", "planner_keyword_filter", "planner_catalog",
                "planner_stats", "planner_moderation", "planner_publication",
                "planner_content", "planner")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private int rowsFor(String table, UUID plannerId) {
        String idColumn = "planner".equals(table) ? "id" : "planner_id";
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
        return count != null ? count : -1;
    }

    private List<Long> plannerVoterIds(UUID plannerId) {
        return jdbc.queryForList(
                "SELECT user_id FROM planner_votes WHERE planner_id = UUID_TO_BIN(?)",
                Long.class, plannerId.toString());
    }

    private List<Long> commentVoterIds(Long commentId) {
        return jdbc.queryForList(
                "SELECT user_id FROM planner_comment_votes WHERE comment_id = ?",
                Long.class, commentId);
    }

    private Long insertComment(UUID plannerId, Long authorId) {
        UUID publicId = UUID.randomUUID();
        jdbc.update("INSERT INTO planner_comments (public_id, planner_id, user_id, content, depth, created_at) "
                + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, 'a comment', 0, NOW())",
                publicId.toString(), plannerId.toString(), authorId);
        return jdbc.queryForObject(
                "SELECT id FROM planner_comments WHERE public_id = UUID_TO_BIN(?)",
                Long.class, publicId.toString());
    }

    private void insertPlannerVote(Long userId, UUID plannerId) {
        jdbc.update("INSERT INTO planner_votes (user_id, planner_id, vote_type, created_at, version) "
                + "VALUES (?, UUID_TO_BIN(?), 'UP', NOW(), 0)", userId, plannerId.toString());
    }

    private void insertCommentVote(Long userId, Long commentId) {
        jdbc.update("INSERT INTO planner_comment_votes (comment_id, user_id, vote_type, created_at, version) "
                + "VALUES (?, ?, 'UP', NOW(), 0)", commentId, userId);
    }

    @Test
    @DisplayName("user-delete-sweeps-aggregate: hard delete removes every planner row across aggregate, projection, filter, and child tables, then the user")
    void userDeleteSweepsAggregate_WhenHardDeleted_NoOrphansRemain() {
        // Published planner with the full projection set and third-party engagement
        Planner published = TestDataFactory.planner(owner)
                .title("Sweep Published")
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder()
                .plannerId(published.getId()).viewCount(3).upvotes(1).build());
        catalogService.add(published);
        filterService.rebuildFilters(published.getId());
        insertPlannerVote(other.getId(), published.getId());
        jdbc.update("INSERT INTO planner_views (planner_id, viewer_hash, view_date, created_at) "
                + "VALUES (UUID_TO_BIN(?), SHA2('viewer', 256), ?, NOW())",
                published.getId().toString(), LocalDate.now(ZoneOffset.UTC));
        insertComment(published.getId(), other.getId());
        jdbc.update("INSERT INTO planner_bookmarks (user_id, planner_id, created_at) "
                + "VALUES (?, UUID_TO_BIN(?), NOW())", other.getId(), published.getId().toString());
        jdbc.update("INSERT INTO planner_subscriptions (user_id, planner_id, enabled, created_at) "
                + "VALUES (?, UUID_TO_BIN(?), TRUE, NOW())", other.getId(), published.getId().toString());
        jdbc.update("INSERT INTO planner_reports (user_id, planner_id, created_at) "
                + "VALUES (?, UUID_TO_BIN(?), NOW())", other.getId(), published.getId().toString());

        // Draft planner with only aggregate rows
        Planner draft = TestDataFactory.planner(owner)
                .title("Sweep Draft")
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(draft.getId()).build());

        lifecycleService.performHardDelete(userRepository.findById(owner.getId()).orElseThrow());

        assertThat(userRepository.findById(owner.getId())).as("the user row is gone").isEmpty();
        for (UUID plannerId : List.of(published.getId(), draft.getId())) {
            for (String table : List.of(
                    "planner", "planner_content", "planner_publication", "planner_moderation",
                    "planner_stats", "planner_catalog", "planner_entity_filter",
                    "planner_keyword_filter", "planner_votes", "planner_views",
                    "planner_comments", "planner_bookmarks", "planner_subscriptions",
                    "planner_reports")) {
                assertThat(rowsFor(table, plannerId))
                        .as("%s carries no rows for deleted planner %s", table, plannerId)
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("vote-outlives-voter: a hard-deleted user's planner vote survives the users cascade, carried by the live sentinel account")
    void a_reassigned_planner_vote_points_at_the_live_sentinel_account() {
        Planner thirdPartyPlanner = TestDataFactory.planner(other)
                .title("Sweep Voted")
                .published(true)
                .save(plannerRepository);
        insertPlannerVote(owner.getId(), thirdPartyPlanner.getId());

        lifecycleService.performHardDelete(userRepository.findById(owner.getId()).orElseThrow());

        assertThat(userRepository.findById(owner.getId())).as("the deleted account row is gone").isEmpty();
        assertThat(plannerVoterIds(thirdPartyPlanner.getId()))
                .as("the vote outlives the voter, now cast by the sentinel")
                .containsExactly(UserAccountLifecycleService.SENTINEL_USER_ID);
        assertThat(userRepository.findById(UserAccountLifecycleService.SENTINEL_USER_ID))
                .as("the sentinel the vote was handed to is a real account row, not a dangling id")
                .isPresent();
    }

    @Test
    @DisplayName("vote-reassignment-spans-both-tables: neither planner nor comment votes keep a row for the deleted account, and third-party votes are untouched")
    void no_vote_row_in_either_table_survives_pointing_at_the_deleted_account() {
        Planner thirdPartyPlanner = TestDataFactory.planner(other)
                .title("Sweep Both Vote Tables")
                .published(true)
                .save(plannerRepository);
        Long comment = insertComment(thirdPartyPlanner.getId(), other.getId());
        insertPlannerVote(owner.getId(), thirdPartyPlanner.getId());
        insertPlannerVote(other.getId(), thirdPartyPlanner.getId());
        insertCommentVote(owner.getId(), comment);
        insertCommentVote(other.getId(), comment);

        lifecycleService.performHardDelete(userRepository.findById(owner.getId()).orElseThrow());

        assertThat(plannerVoterIds(thirdPartyPlanner.getId()))
                .as("the planner vote moved to the sentinel and the third party's own vote stayed put")
                .containsExactlyInAnyOrder(UserAccountLifecycleService.SENTINEL_USER_ID, other.getId());
        assertThat(commentVoterIds(comment))
                .as("the comment vote moved to the sentinel and the third party's own vote stayed put")
                .containsExactlyInAnyOrder(UserAccountLifecycleService.SENTINEL_USER_ID, other.getId());
    }

    @Test
    @DisplayName("empty-sweep-still-removes-account: an account owning nothing and having voted nowhere is removed anyway")
    void an_account_with_nothing_to_reassign_is_still_removed() {
        lifecycleService.performHardDelete(userRepository.findById(owner.getId()).orElseThrow());

        assertThat(userRepository.findById(owner.getId())).as("the account row is gone").isEmpty();
        assertThat(userRepository.findById(other.getId())).as("the untouched third party survives").isPresent();
    }
}
