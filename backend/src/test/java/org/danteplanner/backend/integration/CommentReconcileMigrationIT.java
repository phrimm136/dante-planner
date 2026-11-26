package org.danteplanner.backend.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.ToIntFunction;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.StoredComments;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.danteplanner.backend.user.service.UserAccountLifecycleService.SENTINEL_USER_ID;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentReconcileMigrationIT extends ExclusiveDatabaseSupport {

    private static final String DELETED_AT = "2026-01-03 04:05:06";
    private static final String BEFORE_NOTHING = "";
    private static final String COUNTS_CALL = "CALL reconcile_comment_counts()";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PlannerSeeds plannerSeeds;
    @Autowired private CommentCommandService commandService;
    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc;
    private User author;
    private StoredPlanner planner;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        emptyPlannerTables(jdbc);
        author = TestDataFactory.createTestUser(userRepository, "reconcile-author@example.com");
        planner = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
    }

    @AfterEach
    void restoreSchema() {
        applyMigration();
    }

    @Test
    void migration_WhenTheColumnIsNotNull_MakesItNullableAndKeepsItsTypeCharsetAndCollation() {
        jdbc.execute("ALTER TABLE planner_comments MODIFY COLUMN content TEXT NOT NULL");
        Map<String, Object> before = contentColumn();

        applyMigration();

        assertThat(before).containsEntry("IS_NULLABLE", "NO");
        assertThat(contentColumn()).containsEntry("IS_NULLABLE", "YES")
                .containsEntry("COLUMN_TYPE", before.get("COLUMN_TYPE"))
                .containsEntry("CHARACTER_SET_NAME", before.get("CHARACTER_SET_NAME"))
                .containsEntry("COLLATION_NAME", before.get("COLLATION_NAME"))
                .containsEntry("ORDINAL_POSITION", before.get("ORDINAL_POSITION"));
    }

    @Test
    void migration_WhenAnOldServerInsertedAParentAndItsReply_CopiesBothAndTheThreadShowsThem() throws Exception {
        Long parent = oldShapeRow(author.getId(), "parent", null);
        Long reply = oldShapeRow(author.getId(), "reply", parent);
        jdbc.update("UPDATE planner_comments SET edited_at = '2026-01-02 03:04:05', upvote_count = 4, "
                + "author_notifications_enabled = FALSE WHERE id = ?", reply);

        applyMigration();

        assertThat(newShape()).isEqualTo(jdbc.queryForList("SELECT id AS comment_id, content, edited_at, deleted_at, "
                + "author_notifications_enabled, upvote_count FROM planner_comments ORDER BY id")).hasSize(2);
        mockMvc.perform(get("/api/planner/{id}/comments", planner.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("parent"))
                .andExpect(jsonPath("$[0].replies[0].content").value("reply"))
                .andExpect(jsonPath("$[0].replies[0].upvoteCount").value(4));
    }

    @Test
    void migration_WhenAnUnsplitRowHasNoText_CopiesAnEmptyOne() {
        Long id = oldShapeRow(author.getId(), null, null);

        applyMigration();

        assertThat(newText(id)).isEmpty();
        assertThat(oldText(id)).isNull();
    }

    @Test
    void migration_WhenAnOldServerDeletedASplitRow_CopiesTheDeletionAndBlanksBothTexts() {
        Long id = splitRow(author.getId(), "text");
        jdbc.update("UPDATE planner_comments SET deleted_at = ?, content = '' WHERE id = ?", DELETED_AT, id);
        Long keptByTheOldStatement = splitRow(author.getId(), "text");
        jdbc.update("UPDATE planner_comments SET deleted_at = ? WHERE id = ?", DELETED_AT, keptByTheOldStatement);

        applyMigration();

        for (Long deleted : List.of(id, keptByTheOldStatement)) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planner_comments c "
                    + "JOIN planner_comment_content t ON t.comment_id = c.id "
                    + "WHERE c.id = ? AND t.deleted_at = c.deleted_at", Integer.class, deleted)).isEqualTo(1);
            assertThat(newText(deleted)).isEmpty();
            assertThat(oldText(deleted)).isEmpty();
        }
    }

    @Test
    void migration_WhenTheNewCodeWithdrewAComment_BlanksTheTextItLeftInThePlaceRow() {
        for (String text : List.of("text", " ", "​")) {
            Long id = splitRow(author.getId(), text);
            commandService.deleteComment(publicIdOf(id), author.getId());
            List<Map<String, Object>> deletion = deletionOf(id);
            assertThat(oldText(id)).isEqualTo(text);

            applyMigration();

            assertThat(oldText(id)).isEmpty();
            assertThat(newText(id)).isEmpty();
            assertThat(deletionOf(id)).isEqualTo(deletion);
        }
    }

    @Test
    void migration_WhenAnErasedAccountsCommentHoldsText_BlanksItAndLeavesALiveUsersComment() {
        Long erased = splitRow(SENTINEL_USER_ID, "erased");
        Long unsplit = oldShapeRow(SENTINEL_USER_ID, "erased by an old server", null);
        Long live = splitRow(author.getId(), "live");

        applyMigration();

        assertThat(newText(erased)).isEmpty();
        assertThat(oldText(erased)).isEmpty();
        assertThat(newText(unsplit)).isEmpty();
        assertThat(oldText(unsplit)).isEmpty();
        assertThat(newText(live)).isEqualTo("live");
        assertThat(oldText(live)).isEqualTo("live");
    }

    @Test
    void migration_WhenCommentCountsDrifted_RecountsThemAndWritesNoCorrectRow() {
        StoredPlanner high = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
        StoredPlanner empty = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
        StoredPlanner correct = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
        StoredPlanner correctlyZero = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
        StoredPlanner withoutStats = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
        splitRow(author.getId(), "low");
        commentOn(high);
        Long deleted = commentOn(high);
        jdbc.update("UPDATE planner_comment_content SET content = '', deleted_at = ? WHERE comment_id = ?",
                DELETED_AT, deleted);
        jdbc.update("UPDATE planner_comments SET content = '' WHERE id = ?", deleted);
        commentOn(correct);
        commentOn(withoutStats);
        storeCount(planner, 0);
        storeCount(high, 9);
        storeCount(empty, 3);
        storeCount(correct, 1);
        storeCount(correctlyZero, 0);
        jdbc.update("DELETE FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)", withoutStats.id().toString());

        assertThat(applyMigration()).isEqualTo(3);

        assertThat(List.of(storedCounts(planner), storedCounts(high), storedCounts(empty), storedCounts(correct),
                storedCounts(correctlyZero), storedCounts(withoutStats)))
                .containsExactly(List.of(1), List.of(1), List.of(0), List.of(1), List.of(0), List.of());
        assertThat(applyMigration()).isZero();
    }

    @Test
    void migration_WhenACommentArrivesBetweenTheReadAndTheCorrection_SkipsThatPlannerAndCorrectsTheOthers() {
        StoredPlanner other = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
        splitRow(author.getId(), "text");
        commentOn(other);
        storeCount(planner, 5);
        storeCount(other, 5);

        run(COUNTS_CALL, () -> commandService.createComment(planner.id(), author.getId(),
                UUID.randomUUID(), new CreateCommentRequest("arrived", null)));

        assertThat(storedCounts(planner)).containsExactly(6);
        assertThat(storedCounts(other)).containsExactly(1);
    }

    @Test
    void migration_WhenItEnds_LeavesTheSessionsIsolationAndNoTemporaryTable() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            JdbcTemplate session = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            session.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ");

            statements().forEach(session::update);
            statements().forEach(session::update);

            assertThat(session.queryForObject("SELECT @@SESSION.transaction_isolation", String.class))
                    .isEqualTo("REPEATABLE-READ");
            return null;
        });
    }

    @Test
    void migration_WhenARowIsSplitAndEditedAfterItWasSelected_KeepsTheEditAndTheVotes() {
        Long id = oldShapeRow(author.getId(), "before", null);

        run(call("content_row"), () -> {
            StoredComments.split(jdbc, id);
            jdbc.update("UPDATE planner_comment_content SET content = 'after' WHERE comment_id = ?", id);
            jdbc.update("UPDATE planner_comment_stats SET upvote_count = 5 WHERE comment_id = ?", id);
        });

        assertThat(newShape()).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("content", "after").containsEntry("upvote_count", 5));
    }

    @Test
    void migration_WhenACommentIsWithdrawnAfterItsOldDeletionWasSelected_KeepsTheWithdrawalsTime() {
        Long id = splitRow(author.getId(), "text");
        jdbc.update("UPDATE planner_comments SET deleted_at = ? WHERE id = ?", DELETED_AT, id);

        run(call("deletion"), () -> jdbc.update("UPDATE planner_comment_content SET content = '', "
                + "deleted_at = '2026-02-03 04:05:06' WHERE comment_id = ?", id));

        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(deleted_at, '%Y-%m-%d') FROM planner_comment_content "
                + "WHERE comment_id = ?", String.class, id)).isEqualTo("2026-02-03");
    }

    @Test
    void migration_WhenASessionHoldsRowsItHasNoReasonToChange_FinishesWithoutWaiting() {
        Long held = splitRow(author.getId(), "live");
        Long erased = splitRow(SENTINEL_USER_ID, "erased");
        StoredPlanner drifted = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
        storeCount(planner, 2);
        storeCount(drifted, 4);

        for (String locked : List.of("SELECT id FROM planner_comments WHERE id = ?",
                "SELECT comment_id FROM planner_comment_content WHERE comment_id = ?",
                "SELECT comment_id FROM planner_comment_stats WHERE comment_id = ?",
                "SELECT planner_id FROM planner_stats WHERE comment_count = 2 AND planner_id = "
                        + "(SELECT planner_id FROM planner_comments WHERE id = ?)")) {
            whileAnotherSessionHolds(locked, this::runDataPartRefusingToWait, held);
        }

        assertThat(newText(erased)).isEmpty();
        assertThat(oldText(erased)).isEmpty();
        assertThat(storedCounts(drifted)).containsExactly(0);
    }

    @Test
    void migration_WhenALaterRowIsHeld_HasCommittedTheEarlierOnesAndTheNextRunFinishes() {
        StoredPlanner other = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
        storeCount(planner, 5);
        storeCount(other, 5);
        List<UUID> inMigrationOrder = jdbc.queryForList("SELECT BIN_TO_UUID(planner_id) FROM planner_stats "
                + "ORDER BY planner_id", String.class).stream().map(UUID::fromString).toList();
        Long first = splitRow(SENTINEL_USER_ID, "first");
        Long second = splitRow(SENTINEL_USER_ID, "second");
        storeCount(planner, 7);

        whileAnotherSessionHolds("SELECT id FROM planner_comments WHERE id = ?",
                () -> assertThatThrownBy(this::runDataPartRefusingToWait)
                        .isInstanceOf(PessimisticLockingFailureException.class), second);
        assertThat(oldText(first)).isEmpty();
        assertThat(oldText(second)).isEqualTo("second");

        whileAnotherSessionHolds("SELECT planner_id FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                () -> assertThatThrownBy(this::runDataPartRefusingToWait)
                        .isInstanceOf(PessimisticLockingFailureException.class), inMigrationOrder.get(1).toString());
        assertThat(oldText(second)).isEmpty();
        assertThat(countsByPlanner(inMigrationOrder.get(0))).isLessThan(5);
        assertThat(countsByPlanner(inMigrationOrder.get(1))).isGreaterThanOrEqualTo(5);

        assertThat(applyMigration()).isEqualTo(1);
        assertThat(storedCounts(planner)).containsExactly(2);
        assertThat(storedCounts(other)).containsExactly(0);
    }

    @Test
    void migration_WhenAFailedRunLeftItsProcedures_ReplacesAndDropsThem() {
        jdbc.execute("CREATE PROCEDURE reconcile_comment_rows() SELECT 1");
        jdbc.execute("CREATE PROCEDURE reconcile_comment_counts() SELECT 1");
        Long erased = splitRow(SENTINEL_USER_ID, "erased");

        applyMigration();

        assertThat(newText(erased)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.routines "
                + "WHERE routine_schema = DATABASE() AND routine_name LIKE 'reconcile_comment%'", Integer.class))
                .isZero();
    }

    @Test
    void migration_WhenRunTwice_SelectsNoRowTheSecondTime() {
        oldShapeRow(author.getId(), "unsplit", null);
        splitRow(SENTINEL_USER_ID, "erased");
        Long deleted = splitRow(author.getId(), "deleted");
        jdbc.update("UPDATE planner_comments SET deleted_at = ? WHERE id = ?", DELETED_AT, deleted);
        Long withdrawnAfterwards = newShapeRow("withdrawn");
        commandService.deleteComment(publicIdOf(withdrawnAfterwards), author.getId());
        storeCount(planner, 9);
        assertThat(applyMigration()).isPositive();
        List<Map<String, Object>> written = newShape();

        assertThat(applyMigration()).isZero();

        assertThat(newShape()).isEqualTo(written).hasSize(4);
        assertThat(oldText(withdrawnAfterwards)).isNull();
    }

    @Test
    void migration_WhenTheNewTablesHoldEditsAndVotes_KeepsThem() {
        Long id = splitRow(author.getId(), "before");
        jdbc.update("UPDATE planner_comment_content SET content = 'after', edited_at = '2026-01-02 03:04:05', "
                + "author_notifications_enabled = FALSE WHERE comment_id = ?", id);
        jdbc.update("UPDATE planner_comment_stats SET upvote_count = 5 WHERE comment_id = ?", id);
        List<Map<String, Object>> written = newShape();

        applyMigration();

        assertThat(newShape()).isEqualTo(written).hasSize(1);
        assertThat(oldText(id)).isEqualTo("before");
    }

    @Test
    void insert_WhenItComesFromAServerThatFillsTheOldColumn_IsStoredAndShown() throws Exception {
        applyMigration();

        Long id = splitRow(author.getId(), "from the previous server");
        newShapeRow("from this server");

        assertThat(oldText(id)).isEqualTo("from the previous server");
        mockMvc.perform(get("/api/planner/{id}/comments", planner.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].content").value(containsInAnyOrder(
                        "from the previous server", "from this server")));
    }

    private Long splitRow(Long userId, String content) {
        Long id = oldShapeRow(userId, content, null);
        StoredComments.split(jdbc, id);
        return id;
    }

    private Long commentOn(StoredPlanner target) {
        Long id = insertRow(target, author.getId(), "text", null);
        StoredComments.split(jdbc, id);
        return id;
    }

    private Long oldShapeRow(Long userId, @Nullable String content, @Nullable Long parentId) {
        return insertRow(planner, userId, content, parentId);
    }

    private Long insertRow(StoredPlanner target, Long userId, @Nullable String content, @Nullable Long parentId) {
        String publicId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO planner_comments (public_id, planner_id, user_id, parent_comment_id, content, depth, "
                + "created_at) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, NOW())",
                publicId, target.id().toString(), userId, parentId, content, parentId == null ? 0 : 1);
        return idOf(UUID.fromString(publicId));
    }

    private Long newShapeRow(String content) {
        Long id = insertRow(planner, author.getId(), null, null);
        jdbc.update("INSERT INTO planner_comment_content (comment_id, content) VALUES (?, ?)", id, content);
        jdbc.update("INSERT INTO planner_comment_stats (comment_id) VALUES (?)", id);
        return id;
    }

    private Long idOf(UUID publicId) {
        return jdbc.queryForObject("SELECT id FROM planner_comments WHERE public_id = UUID_TO_BIN(?)",
                Long.class, publicId.toString());
    }

    private UUID publicIdOf(Long id) {
        return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(public_id) FROM planner_comments WHERE id = ?",
                String.class, id));
    }

    private @Nullable String oldText(Long id) {
        return jdbc.queryForObject("SELECT content FROM planner_comments WHERE id = ?", String.class, id);
    }

    private String newText(Long id) {
        return jdbc.queryForObject("SELECT content FROM planner_comment_content WHERE comment_id = ?",
                String.class, id);
    }

    private List<Map<String, Object>> deletionOf(Long id) {
        return jdbc.queryForList("SELECT deleted_at FROM planner_comment_content "
                + "WHERE comment_id = ? AND deleted_at IS NOT NULL", id);
    }

    private void storeCount(StoredPlanner target, int count) {
        jdbc.update("INSERT INTO planner_stats (planner_id, comment_count) VALUES (UUID_TO_BIN(?), ?) "
                + "ON DUPLICATE KEY UPDATE comment_count = VALUES(comment_count)", target.id().toString(), count);
    }

    private List<Integer> storedCounts(StoredPlanner target) {
        return jdbc.queryForList("SELECT comment_count FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, target.id().toString());
    }

    private int countsByPlanner(UUID plannerId) {
        return jdbc.queryForObject("SELECT comment_count FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
    }

    private Map<String, Object> contentColumn() {
        return jdbc.queryForMap("SELECT IS_NULLABLE, COLUMN_TYPE, CHARACTER_SET_NAME, COLLATION_NAME, "
                + "ORDINAL_POSITION FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'planner_comments' AND column_name = 'content'");
    }

    private List<Map<String, Object>> newShape() {
        return jdbc.queryForList("SELECT t.comment_id, t.content, t.edited_at, t.deleted_at, "
                + "t.author_notifications_enabled, s.upvote_count FROM planner_comment_content t "
                + "JOIN planner_comment_stats s ON s.comment_id = t.comment_id ORDER BY t.comment_id");
    }

    private int applyMigration() {
        return run(BEFORE_NOTHING, () -> { });
    }

    private int run(String beforeTheCall, Runnable meanwhile) {
        return inMigrationSession(session -> {
            int selected = 0;
            for (String statement : statements()) {
                if (statement.equals(beforeTheCall)) {
                    meanwhile.run();
                }
                int affected = session.update(statement);
                selected += statement.startsWith("CREATE TEMPORARY TABLE") ? affected : 0;
            }
            return selected;
        });
    }

    private void runDataPartRefusingToWait() {
        inMigrationSession(session -> {
            session.execute("SET SESSION innodb_lock_wait_timeout = 1");
            return statements().stream()
                    .filter(statement -> !statement.startsWith("ALTER"))
                    .mapToInt(session::update)
                    .sum();
        });
    }

    private int inMigrationSession(ToIntFunction<JdbcTemplate> work) {
        return jdbc.execute((ConnectionCallback<Integer>) connection -> {
            JdbcTemplate session = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            try {
                return work.applyAsInt(session);
            } finally {
                session.execute("DROP TEMPORARY TABLE IF EXISTS reconcile_comment, reconcile_count");
                session.execute("SET SESSION transaction_isolation = DEFAULT");
                session.execute("SET SESSION innodb_lock_wait_timeout = DEFAULT");
            }
        });
    }

    private void whileAnotherSessionHolds(String lockingRead, Runnable work, Object... parameters) {
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
            jdbc.queryForList(lockingRead + " FOR UPDATE", parameters);
            try {
                Hang.get(CompletableFuture.runAsync(work), "the migration beside a held lock");
            } catch (ExecutionException e) {
                throw new AssertionError(e.getCause());
            }
        });
    }

    private static String call(String step) {
        return "CALL reconcile_comment_rows('" + step + "')";
    }

    private static List<String> statements() {
        try {
            return Arrays.stream(new ClassPathResource("db/migration/V069__reconcile_comment_decomposition.sql")
                            .getContentAsString(StandardCharsets.UTF_8).split(";\\s*\\n\\n(?=\\S)"))
                    .map(String::strip)
                    .map(statement -> statement.replaceAll(";$", ""))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
