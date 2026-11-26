package org.danteplanner.backend.integration;

import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.StoredComments;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserSettingsService;
import org.danteplanner.backend.support.FilterRowsSync;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Instant;
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
class PlannerUserDeleteSweepIT extends ExclusiveDatabaseSupport {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

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
        emptyPlannerTables(jdbc);
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

    private String commentContent(Long commentId) {
        return jdbc.queryForObject(
                "SELECT content FROM planner_comment_content WHERE comment_id = ?", String.class, commentId);
    }

    private Long commentAuthorId(Long commentId) {
        return jdbc.queryForObject(
                "SELECT user_id FROM planner_comments WHERE id = ?", Long.class, commentId);
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
        Long commentId = jdbc.queryForObject(
                "SELECT id FROM planner_comments WHERE public_id = UUID_TO_BIN(?)",
                Long.class, publicId.toString());
        StoredComments.split(jdbc, commentId);
        return commentId;
    }

    /**
     * Make the account purge-eligible, then purge it. {@code performHardDelete} re-reads
     * eligibility under a lock, so a live account is skipped rather than deleted.
     */
    private void purgeOwner(Long ownerId) {
        transactionTemplate.executeWithoutResult(status ->
                userRepository.softDelete(ownerId, Instant.now(), Instant.now().minusSeconds(60)));
        assertThat(lifecycleService.performHardDelete(ownerId, Instant.now())).isTrue();
    }

    private void insertPlannerVote(Long userId, UUID plannerId) {
        jdbc.update("INSERT INTO planner_votes (user_id, planner_id, vote_type, created_at, version) "
                + "VALUES (?, UUID_TO_BIN(?), 'UP', NOW(), 0)", userId, plannerId.toString());
    }

    private void insertCommentVote(Long userId, Long commentId) {
        jdbc.update("INSERT INTO planner_comment_votes (comment_id, user_id, vote_type, created_at, version) "
                + "VALUES (?, ?, 'UP', NOW(), 0)", commentId, userId);
    }

    // user-delete-sweeps-aggregate
    @Test
    void userDeleteSweepsAggregate_WhenHardDeleted_NoOrphansRemain() {
        // Published planner with the full projection set and third-party engagement
        StoredPlanner published = TestDataFactory.planner(owner)
                .title("Sweep Published")
                .published(true)
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder()
                .plannerId(published.id()).viewCount(3).upvotes(1).build());
        TestDataFactory.addToCatalog(catalogService, published);
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, published.id());
        insertPlannerVote(other.getId(), published.id());
        jdbc.update("INSERT INTO planner_views (planner_id, viewer_hash, view_date, created_at) "
                + "VALUES (UUID_TO_BIN(?), SHA2('viewer', 256), ?, NOW())",
                published.id().toString(), LocalDate.now(ZoneOffset.UTC));
        Long sweptComment = insertComment(published.id(), other.getId());
        jdbc.update("INSERT INTO planner_bookmarks (user_id, planner_id, created_at) "
                + "VALUES (?, UUID_TO_BIN(?), NOW())", other.getId(), published.id().toString());
        jdbc.update("INSERT INTO planner_subscriptions (user_id, planner_id, enabled, created_at) "
                + "VALUES (?, UUID_TO_BIN(?), TRUE, NOW())", other.getId(), published.id().toString());
        jdbc.update("INSERT INTO planner_reports (user_id, planner_id, created_at) "
                + "VALUES (?, UUID_TO_BIN(?), NOW())", other.getId(), published.id().toString());

        // Draft planner with only aggregate rows
        StoredPlanner draft = TestDataFactory.planner(owner)
                .title("Sweep Draft")
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder().plannerId(draft.id()).build());

        purgeOwner(owner.getId());

        assertThat(userRepository.findById(owner.getId())).as("the user row is gone").isEmpty();
        for (String table : List.of("planner_comment_content", "planner_comment_stats")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE comment_id = ?",
                    Integer.class, sweptComment)).as("%s rows of the swept comment", table).isZero();
        }
        for (UUID plannerId : List.of(published.id(), draft.id())) {
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
    void commentVotes_WhenTheSentinelAlreadyVotedOnTheSameComment_KeepOneSentinelRowAndTheCounter() {
        StoredPlanner thirdPartyPlanner = TestDataFactory.planner(other)
                .title("Sweep Vote Collision")
                .published(true)
                .save(plannerSeeds);
        Long collided = insertComment(thirdPartyPlanner.id(), other.getId());
        Long lone = insertComment(thirdPartyPlanner.id(), other.getId());
        insertCommentVote(UserAccountLifecycleService.SENTINEL_USER_ID, collided);
        insertCommentVote(owner.getId(), collided);
        insertCommentVote(owner.getId(), lone);
        jdbc.update("UPDATE planner_comment_stats SET upvote_count = 2 WHERE comment_id = ?", collided);

        purgeOwner(owner.getId());

        assertThat(commentVoterIds(collided)).containsExactly(UserAccountLifecycleService.SENTINEL_USER_ID);
        assertThat(commentVoterIds(lone)).containsExactly(UserAccountLifecycleService.SENTINEL_USER_ID);
        assertThat(jdbc.queryForObject("SELECT upvote_count FROM planner_comment_stats WHERE comment_id = ?",
                Integer.class, collided)).isEqualTo(2);
    }

    @Test
    void reassignedPlannerVote_WhenSwept_PointsAtLiveSentinelAccount() {
        StoredPlanner thirdPartyPlanner = TestDataFactory.planner(other)
                .title("Sweep Voted")
                .published(true)
                .save(plannerSeeds);
        insertPlannerVote(owner.getId(), thirdPartyPlanner.id());

        purgeOwner(owner.getId());

        assertThat(userRepository.findById(owner.getId())).as("the deleted account row is gone").isEmpty();
        assertThat(plannerVoterIds(thirdPartyPlanner.id()))
                .as("the vote outlives the voter, now cast by the sentinel")
                .containsExactly(UserAccountLifecycleService.SENTINEL_USER_ID);
        assertThat(userRepository.findById(UserAccountLifecycleService.SENTINEL_USER_ID))
                .as("the sentinel the vote was handed to is a real account row, not a dangling id")
                .isPresent();
    }

    @Test
    void commentContent_WhenAuthorHardDeleted_IsEmptiedWhileTheRowSurvives() {
        StoredPlanner thirdPartyPlanner = TestDataFactory.planner(other)
                .title("Sweep Comment Content")
                .published(true)
                .save(plannerSeeds);
        Long ownComment = insertComment(thirdPartyPlanner.id(), owner.getId());
        Long thirdPartyComment = insertComment(thirdPartyPlanner.id(), other.getId());

        purgeOwner(owner.getId());

        assertThat(commentAuthorId(ownComment))
                .as("the row survives so replies keep a parent, now owned by the sentinel")
                .isEqualTo(UserAccountLifecycleService.SENTINEL_USER_ID);
        assertThat(jdbc.queryForObject("SELECT content FROM planner_comments WHERE id = ?",
                String.class, ownComment)).as("nor in the row an old server reads").isEmpty();
        assertThat(commentContent(ownComment))
                .as("the text does not survive the account that wrote it")
                .isEmpty();
        assertThat(commentContent(thirdPartyComment))
                .as("a third party's comment on the same planner is untouched")
                .isEqualTo("a comment");
    }

    @Test
    void voteRow_WhenAccountDeleted_SurvivesInNeitherTable() {
        StoredPlanner thirdPartyPlanner = TestDataFactory.planner(other)
                .title("Sweep Both Vote Tables")
                .published(true)
                .save(plannerSeeds);
        Long comment = insertComment(thirdPartyPlanner.id(), other.getId());
        insertPlannerVote(owner.getId(), thirdPartyPlanner.id());
        insertPlannerVote(other.getId(), thirdPartyPlanner.id());
        insertCommentVote(owner.getId(), comment);
        insertCommentVote(other.getId(), comment);

        purgeOwner(owner.getId());

        assertThat(plannerVoterIds(thirdPartyPlanner.id()))
                .as("the planner vote moved to the sentinel and the third party's own vote stayed put")
                .containsExactlyInAnyOrder(UserAccountLifecycleService.SENTINEL_USER_ID, other.getId());
        assertThat(commentVoterIds(comment))
                .as("the comment vote moved to the sentinel and the third party's own vote stayed put")
                .containsExactlyInAnyOrder(UserAccountLifecycleService.SENTINEL_USER_ID, other.getId());
    }

    @Test
    void settingsRow_WhenUserHardDeleted_IsRemoved() {
        userSettingsService.getOrCreateEntity(owner.getId());
        assertThat(userSettingsRepository.findByUserId(owner.getId())).as("the settings row exists").isPresent();

        purgeOwner(owner.getId());

        assertThat(userRepository.findById(owner.getId())).as("the user row is gone").isEmpty();
        assertThat(userSettingsRepository.findByUserId(owner.getId())).as("the settings row is gone").isEmpty();
    }

    @Test
    void account_WhenNothingToReassign_IsStillRemoved() {
        purgeOwner(owner.getId());

        assertThat(userRepository.findById(owner.getId())).as("the account row is gone").isEmpty();
        assertThat(userRepository.findById(other.getId())).as("the untouched third party survives").isPresent();
    }
}
