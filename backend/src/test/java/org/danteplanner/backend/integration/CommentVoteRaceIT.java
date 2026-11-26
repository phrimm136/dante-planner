package org.danteplanner.backend.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.UpdateCommentRequest;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.comment.exception.CommentVoteAlreadyExistsException;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentEngagementService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.service.CommentModerationService;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.shared.dto.NotificationSetting;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class CommentVoteRaceIT extends SharedMySqlContainerSupport {

    private static final int ROUNDS = 20;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PlannerSeeds plannerSeeds;
    @Autowired private CommentCommandService commandService;
    @Autowired private CommentEngagementService engagementService;
    @Autowired private CommentQueryService queryService;
    @Autowired private CommentModerationService moderationService;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private User author;
    private User first;
    private User second;
    private User moderator;
    private StoredPlanner planner;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(transactionManager);
        author = TestDataFactory.createTestUser(userRepository, "vote-race-author@example.com");
        first = TestDataFactory.createTestUser(userRepository, "vote-race-first@example.com");
        second = TestDataFactory.createTestUser(userRepository, "vote-race-second@example.com");
        moderator = TestDataFactory.createModerator(userRepository, "vote-race-moderator@example.com");
        planner = TestDataFactory.createTestPlanner(plannerSeeds, author, true);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void toggleUpvote_WhenTwoUsersVoteAtOnce_StoresBothVotesAndCountsTwo() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID comment = comment();

            assertThat(atOnce(comment, this::upvoteByFirst, this::upvoteBySecond)).isEmpty();

            assertThat(voters(comment)).containsExactlyInAnyOrder(first.getId(), second.getId());
            assertThat(shown(comment, first).upvoteCount()).isEqualTo(2);
        }
    }

    @Test
    void toggleUpvote_WhenOneUserVotesTwiceAtOnce_StoresOneVoteAndRefusesTheOther() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID comment = comment();

            assertThat(atOnce(comment, this::upvoteByFirst, this::upvoteByFirst)).singleElement()
                    .isInstanceOf(CommentVoteAlreadyExistsException.class);

            assertThat(voters(comment)).containsExactly(first.getId());
            assertThat(shown(comment, first).upvoteCount()).isEqualTo(1);
        }
    }

    @Test
    void toggleUpvote_WhenOneUserVotesTwiceInSequence_AnswersTheVoteConflictAndLogsNothingUnexpected()
            throws Exception {
        UUID comment = comment();
        upvoteByFirst(comment);

        List<ILoggingEvent> logged = LogCapture.during(() -> {
            try {
                AuthCookies.performAuthed(mockMvc, post("/api/comments/{id}/upvote", comment).with(withCsrf()),
                                TestDataFactory.generateAccessToken(jwtTokenService, first))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("VOTE_ALREADY_EXISTS"))
                        .andExpect(jsonPath("$.detail").value("User " + first.getId()
                                + " has already voted on comment " + comment
                                + ". Votes are permanent and cannot be changed."));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }, Level.ERROR, ApiExceptionHandler.class);

        assertThat(logged).isEmpty();
        assertThat(voters(comment)).containsExactly(first.getId());
        assertThat(shown(comment, first).upvoteCount()).isEqualTo(1);
    }

    @Test
    void toggleUpvote_WhenTheThreadIsReadNext_ShowsTheVoteToItsCasterOnly() {
        UUID comment = comment();

        assertThat(engagementService.toggleUpvote(comment, first.getId()).upvoteCount()).isEqualTo(1);

        assertThat(shown(comment, first).hasUpvoted()).isTrue();
        assertThat(shown(comment, second).hasUpvoted()).isFalse();
    }

    @Test
    void toggleUpvote_WhenTheVoteIsStored_StampsItFromTheDatabaseClock() {
        UUID comment = comment();

        long before = databaseSecond();
        upvoteByFirst(comment);
        long after = databaseSecond();

        assertThat(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(v.created_at) FROM planner_comment_votes v "
                + "JOIN planner_comments c ON c.id = v.comment_id "
                + "WHERE c.public_id = UUID_TO_BIN(?) AND v.vote_type = 'UP'", Long.class, comment.toString()))
                .isBetween(before, after);
    }

    @Test
    void deleteComment_WhenAuthorAndModeratorDeleteAtOnce_LowersTheCommentCountOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID comment = comment();
            comment();
            int before = commentCount();

            assertThat(atOnce(comment, this::deleteByAuthor, this::deleteByModerator)).isEmpty();

            assertThat(commentCount()).isEqualTo(before - 1);
        }
    }

    @Test
    void deleteComment_WhenTheAuthorDeletes_EmptiesTheStoredContent() {
        UUID comment = comment();

        deleteByAuthor(comment);

        assertThat(storedContent(comment)).isEqualTo(CommentConstants.DELETED_CONTENT);
    }

    @Test
    void deleteComment_WhenAModeratorDeletes_EmptiesTheStoredContent() {
        UUID comment = comment();

        deleteByModerator(comment);

        assertThat(storedContent(comment)).isEqualTo(CommentConstants.DELETED_CONTENT);
    }

    @Test
    void deleteComment_WhenItWasReadBeforeAnotherDeleteCommitted_LowersTheCommentCountOnce() {
        UUID comment = comment();
        comment();
        int before = commentCount();

        afterReading(comment, this::deleteByModerator, this::deleteByAuthor);

        assertThat(commentCount()).isEqualTo(before - 1);
    }

    @Test
    void updateComment_WhenItWasReadBeforeADeleteCommitted_IsRefusedAndTheCommentStaysDeleted() {
        UUID root = comment();
        commandService.createReply(root, second.getId(), UUID.randomUUID(), "a live reply");

        assertThatThrownBy(() -> afterReading(root, this::deleteByAuthor, this::edit))
                .isInstanceOf(CommentForbiddenException.class)
                .hasMessage("Cannot edit a deleted comment");

        assertThat(shown(root, author).isDeleted()).isTrue();
        assertThat(shown(root, author).content()).isEmpty();
    }

    @Test
    void updateComment_WhenItWasReadBeforeAVoteCommitted_KeepsTheVoteCount() {
        UUID comment = comment();

        afterReading(comment, this::upvoteByFirst, this::edit);

        assertThat(shown(comment, author).upvoteCount()).isEqualTo(1);
        assertThat(shown(comment, author).content()).isEqualTo("edited");
    }

    @Test
    void toggleNotification_WhenItWasReadBeforeAVoteCommitted_KeepsTheVoteCount() {
        UUID comment = comment();

        afterReading(comment, this::upvoteByFirst, this::silence);

        assertThat(shown(comment, author).upvoteCount()).isEqualTo(1);
        assertThat(shown(comment, author).authorNotificationsEnabled()).isFalse();
    }

    @Test
    void toggleNotification_WhenItWasReadBeforeADeleteCommitted_StoresTheChoiceAndTheCommentStaysDeleted() {
        UUID root = comment();
        commandService.createReply(root, second.getId(), UUID.randomUUID(), "a live reply");

        afterReading(root, this::deleteByAuthor, this::silence);

        assertThat(shown(root, author).isDeleted()).isTrue();
        assertThat(shown(root, author).authorNotificationsEnabled()).isFalse();
    }

    @Test
    void createComment_WhenStored_KeepsItsTextOnlyInTheContentRow() {
        UUID comment = comment();
        UUID reply = commandService.createReply(comment, second.getId(), UUID.randomUUID(), "a reply").id();

        assertThat(jdbc.queryForList("SELECT content FROM planner_comments "
                + "WHERE public_id IN (UUID_TO_BIN(?), UUID_TO_BIN(?))", String.class,
                comment.toString(), reply.toString())).containsExactly(null, null);
        assertThat(shown(comment, author).content()).isEqualTo("original");
        assertThat(shown(comment, author).replies()).extracting(CommentTreeNode::content).containsExactly("a reply");
    }

    private List<Throwable> atOnce(UUID comment, CommentAction one, CommentAction other) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        List<Future<Throwable>> running = new ArrayList<>();
        for (CommentAction action : List.of(one, other)) {
            running.add(executor.submit(() -> {
                Hang.await(start, "both writers ready");
                return failureOf(() -> action.on(comment));
            }));
        }
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> writer : running) {
            Throwable failure = Hang.get(writer, "a writer finishing");
            if (failure != null) {
                failures.add(failure);
            }
        }
        return failures;
    }

    private void afterReading(UUID comment, CommentAction committedFirst, CommentAction late) {
        transaction.executeWithoutResult(status -> {
            queryService.requireByPublicId(comment);
            assertThat(failureElsewhere(comment, committedFirst)).isNull();
            late.on(comment);
        });
    }

    private Throwable failureElsewhere(UUID comment, CommentAction action) {
        try {
            return Hang.get(executor.submit(() -> failureOf(() -> action.on(comment))), "the first writer committing");
        } catch (Exception e) {
            return e;
        }
    }

    private static Throwable failureOf(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    @FunctionalInterface
    private interface CommentAction {

        void on(UUID comment);
    }

    private UUID comment() {
        return commandService.createComment(planner.id(), author.getId(), UUID.randomUUID(),
                new CreateCommentRequest("original", null)).id();
    }

    private void upvoteByFirst(UUID comment) {
        engagementService.toggleUpvote(comment, first.getId());
    }

    private void upvoteBySecond(UUID comment) {
        engagementService.toggleUpvote(comment, second.getId());
    }

    private void deleteByAuthor(UUID comment) {
        commandService.deleteComment(comment, author.getId());
    }

    private void deleteByModerator(UUID comment) {
        moderationService.deleteCommentByPublicId(moderator.getId(), comment, "race");
    }

    private void edit(UUID comment) {
        commandService.updateComment(comment, author.getId(), new UpdateCommentRequest("edited"));
    }

    private void silence(UUID comment) {
        engagementService.toggleNotification(comment, author.getId(), NotificationSetting.OFF);
    }

    private CommentTreeNode shown(UUID comment, User viewer) {
        return queryService.getCommentTree(planner.id(), new Viewer.Member(viewer.getId())).value().stream()
                .filter(node -> node.id().equals(comment))
                .findFirst()
                .orElseThrow();
    }

    private List<Long> voters(UUID comment) {
        return jdbc.queryForList("SELECT v.user_id FROM planner_comment_votes v JOIN planner_comments c "
                + "ON c.id = v.comment_id WHERE c.public_id = UUID_TO_BIN(?)", Long.class, comment.toString());
    }

    private long databaseSecond() {
        Long second = jdbc.queryForObject("SELECT UNIX_TIMESTAMP(NOW())", Long.class);
        return second != null ? second : -1;
    }

    private String storedContent(UUID comment) {
        return jdbc.queryForObject("SELECT t.content FROM planner_comment_content t JOIN planner_comments c "
                + "ON c.id = t.comment_id WHERE c.public_id = UUID_TO_BIN(?)", String.class, comment.toString());
    }

    private int commentCount() {
        Integer count = jdbc.queryForObject("SELECT comment_count FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, planner.id().toString());
        return count != null ? count : -1;
    }
}
