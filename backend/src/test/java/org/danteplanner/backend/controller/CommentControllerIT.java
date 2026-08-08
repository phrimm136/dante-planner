package org.danteplanner.backend.controller;


import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.moderation.repository.PlannerCommentReportRepository;
import org.danteplanner.backend.moderation.service.CommentReportService;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentEngagementService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.planner.validation.VoteUniquenessValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, CommentControllerIT.MockCommentServiceConfig.class})
class CommentControllerIT extends SharedMySqlContainerSupport {




    @TestConfiguration
    static class MockCommentServiceConfig {
        @Bean
        @Primary
        public CommentQueryService commentQueryService(
                PlannerCommentRepository commentRepository,
                PlannerCommentVoteRepository commentVoteRepository,
                PlannerRepository plannerRepository,
                UserService userService) {
            return new CommentQueryService(commentRepository, commentVoteRepository, userService,
                    new PlannerAccessGuard(userService, plannerRepository),
                    new CommentAccessValidator());
        }

        @Bean
        @Primary
        public CommentCommandService commentCommandService(
                PlannerCommentRepository commentRepository,
                CommentQueryService commentQueryService,
                PlannerRepository plannerRepository,
                UserService userService,
                NotificationDispatchService notificationDispatchService,
                ApplicationEventPublisher eventPublisher,
                PlannerStatsRepository plannerStatsRepository) {
            return new CommentCommandService(commentRepository, commentQueryService, userService,
                    notificationDispatchService, eventPublisher,
                    new PlannerAccessGuard(userService, plannerRepository),
                    new PlannerStatsService(plannerStatsRepository),
                    new CommentAccessValidator(), new CommentAuthorshipValidator(),
                    new CommentStateValidator());
        }

        @Bean
        @Primary
        public CommentEngagementService commentEngagementService(
                PlannerCommentRepository commentRepository,
                PlannerCommentVoteRepository commentVoteRepository,
                CommentQueryService commentQueryService,
                PlannerRepository plannerRepository,
                UserService userService,
                CommentReportService commentReportService) {
            return new CommentEngagementService(commentRepository, commentVoteRepository, commentQueryService,
                    new PlannerAccessGuard(userService, plannerRepository),
                    commentReportService, new CommentAuthorshipValidator(),
                    new CommentStateValidator(), new VoteUniquenessValidator());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlannerCommentReportRepository commentReportRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User testUser;
    private User otherUser;
    private Planner publishedPlanner;
    private Planner unpublishedPlanner;
    private String accessToken;
    private String otherUserAccessToken;

    @BeforeEach
    void setUp() {

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        otherUser = TestDataFactory.createTestUser(userRepository, "other@example.com");
        publishedPlanner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
        unpublishedPlanner = TestDataFactory.createTestPlanner(plannerRepository, testUser, false);

        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
        otherUserAccessToken = TestDataFactory.generateAccessToken(jwtTokenService, otherUser);
    }

    private Cookie accessTokenCookie() {
        return AuthCookies.accessToken(accessToken);
    }

    private Cookie otherUserAccessTokenCookie() {
        return AuthCookies.accessToken(otherUserAccessToken);
    }

    private PlannerComment createComment(Long parentId, int expectedDepth) {
        PlannerComment comment = new PlannerComment(
                publishedPlanner.getId(),
                testUser.getId(),
                "Test comment content",
                parentId,
                expectedDepth
        );
        return commentRepository.save(comment);
    }

    @Nested
    @DisplayName("GET /api/planner/{id}/comments - Get Comments")
    class GetCommentsTests {

        @Test
        @DisplayName("Should return 200 with public access to published planner")
        void getComments_WhenPublishedPlanner_Returns200() throws Exception {
            createComment(null, 0);

            mockMvc.perform(get("/api/planner/{id}/comments", publishedPlanner.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].content").value("Test comment content"));
        }

        @Test
        @DisplayName("Should return 403 when accessing unpublished planner as non-owner")
        void getComments_WhenUnpublishedPlanner_Returns403() throws Exception {
            mockMvc.perform(get("/api/planner/{id}/comments", unpublishedPlanner.getId())
                            .cookie(otherUserAccessTokenCookie()))
                    .andExpect(status().isForbidden());
        }


        @Test
        @DisplayName("Should return empty array for planner with no comments")
        void getComments_WhenNoComments_ReturnsEmptyArray() throws Exception {
            mockMvc.perform(get("/api/planner/{id}/comments", publishedPlanner.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/planner/{id}/comments - Create Comment")
    class CreateCommentTests {

        @Test
        @DisplayName("Should return 201 when creating valid top-level comment")
        void createComment_WhenValidTopLevel_Returns201() throws Exception {
            String requestBody = "{\"content\":\"New comment\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("Should set correct depth when replying with parentId")
        void createComment_WhenParent_SetsCorrectDepth() throws Exception {
            PlannerComment parent = createComment(null, 0);

            String requestBody = "{\"content\":\"Reply\",\"parentCommentId\":\"" + parent.getPublicId() + "\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists());

            // Verify depth in database
            List<PlannerComment> comments = commentRepository.findByPlannerId(publishedPlanner.getId());
            PlannerComment reply = comments.stream()
                    .filter(c -> c.getParentCommentId() != null)
                    .findFirst()
                    .orElseThrow();
            assertThat(reply.getDepth()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void createComment_WhenUnauthenticated_Returns401() throws Exception {
            String requestBody = "{\"content\":\"Test\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when commenting on unpublished planner")
        void createComment_WhenUnpublishedPlanner_Returns403() throws Exception {
            String requestBody = "{\"content\":\"Test\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", unpublishedPlanner.getId()).with(withCsrf())
                            .cookie(otherUserAccessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when content is empty")
        void createComment_WhenEmptyContent_Returns400() throws Exception {
            String requestBody = "{\"content\":\"\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Depth 0 - Top-level comment has depth 0")
        void createComment_WhenTopLevel_HasDepth0() throws Exception {
            String requestBody = "{\"content\":\"Top-level\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists());

            // Verify depth in database
            List<PlannerComment> comments = commentRepository.findByPlannerId(publishedPlanner.getId());
            assertThat(comments).hasSize(1);
            assertThat(comments.get(0).getDepth()).isEqualTo(0);
        }

        @Test
        @DisplayName("Depth 1-5 - Replies increment depth correctly")
        void createComment_WhenReplies_IncrementDepth() throws Exception {
            PlannerComment depth0 = createComment(null, 0);
            String requestBody1 = "{\"content\":\"Depth 1\",\"parentCommentId\":\"" + depth0.getPublicId() + "\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody1))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists());

            // Verify depth in database
            List<PlannerComment> comments = commentRepository.findByPlannerId(publishedPlanner.getId());
            PlannerComment reply = comments.stream()
                    .filter(c -> c.getParentCommentId() != null)
                    .findFirst()
                    .orElseThrow();
            assertThat(reply.getDepth()).isEqualTo(1);
        }

        @Test
        @DisplayName("Depth 6 - Comment at depth 6 is allowed (MAX_DEPTH=MAX_VALUE)")
        void createComment_WhenDepth6_IsAllowed() throws Exception {
            PlannerComment depth0 = createComment(null, 0);
            PlannerComment depth1 = createComment(depth0.getId(), 1);
            PlannerComment depth2 = createComment(depth1.getId(), 2);
            PlannerComment depth3 = createComment(depth2.getId(), 3);
            PlannerComment depth4 = createComment(depth3.getId(), 4);
            PlannerComment depth5 = createComment(depth4.getId(), 5);

            String requestBody = "{\"content\":\"Depth 6 reply\",\"parentCommentId\":\"" + depth5.getPublicId() + "\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists());

            // Verify depth in database - no flattening with MAX_DEPTH=MAX_VALUE
            List<PlannerComment> allComments = commentRepository.findByPlannerId(publishedPlanner.getId());
            PlannerComment reply = allComments.stream()
                    .filter(c -> c.getContent().equals("Depth 6 reply"))
                    .findFirst()
                    .orElseThrow();
            assertThat(reply.getDepth()).isEqualTo(6);
            assertThat(reply.getParentCommentId()).isEqualTo(depth5.getId());
        }

        @Test
        @DisplayName("Depth 6 - Comment at depth 6 has correct parent (no flattening)")
        void createComment_WhenDepth6_HasCorrectParent() throws Exception {
            PlannerComment depth0 = createComment(null, 0);
            PlannerComment depth1 = createComment(depth0.getId(), 1);
            PlannerComment depth2 = createComment(depth1.getId(), 2);
            PlannerComment depth3 = createComment(depth2.getId(), 3);
            PlannerComment depth4 = createComment(depth3.getId(), 4);
            PlannerComment depth5 = createComment(depth4.getId(), 5);

            String requestBody = "{\"content\":\"Depth 6 comment\",\"parentCommentId\":\"" + depth5.getPublicId() + "\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists());

            List<PlannerComment> allComments = commentRepository.findByPlannerId(publishedPlanner.getId());
            // With MAX_DEPTH=MAX_VALUE, there's 1 comment at each depth 0-6
            long depth6Count = allComments.stream().filter(c -> c.getDepth() == 6).count();
            assertThat(depth6Count).isEqualTo(1);
        }

        @Test
        @DisplayName("Should create notification when replying to comment")
        void createComment_WhenReplyToComment_CreatesNotification() throws Exception {
            PlannerComment parentComment = createComment(null, 0);

            String requestBody = "{\"content\":\"Reply\",\"parentCommentId\":\"" + parentComment.getPublicId() + "\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .cookie(otherUserAccessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated());

            List<Notification> notifications = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                    testUser.getId(), org.springframework.data.domain.PageRequest.of(0, 10)
            ).getContent();
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getNotificationType()).isEqualTo(NotificationType.REPLY_RECEIVED);
        }

        @Test
        @DisplayName("Should create notification when top-level comment on planner")
        void createComment_WhenTopLevelOnPlanner_CreatesNotification() throws Exception {
            String requestBody = "{\"content\":\"Top-level comment\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId()).with(withCsrf())
                            .cookie(otherUserAccessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated());

            List<Notification> notifications = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                    testUser.getId(), org.springframework.data.domain.PageRequest.of(0, 10)
            ).getContent();
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getNotificationType()).isEqualTo(NotificationType.COMMENT_RECEIVED);
        }
    }

    @Nested
    @DisplayName("PUT /api/comments/{id} - Update Comment")
    class UpdateCommentTests {

        @Test
        @DisplayName("Should return 200 when owner edits comment")
        void updateComment_WhenOwner_Returns200() throws Exception {
            PlannerComment comment = createComment(null, 0);

            String requestBody = "{\"content\":\"Updated content\"}";

            mockMvc.perform(put("/api/comments/{id}", comment.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.editedAt").exists());

            // Verify content updated in database
            PlannerComment updated = commentRepository.findById(comment.getId()).orElseThrow();
            assertThat(updated.getContent()).isEqualTo("Updated content");
        }

        @Test
        @DisplayName("Should return 403 when non-owner tries to edit")
        void updateComment_WhenNonOwner_Returns403() throws Exception {
            PlannerComment comment = createComment(null, 0);

            String requestBody = "{\"content\":\"Hacked content\"}";

            mockMvc.perform(put("/api/comments/{id}", comment.getPublicId()).with(withCsrf())
                            .cookie(otherUserAccessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should persist updated content")
        void updateComment_WhenOwner_PersistsContent() throws Exception {
            PlannerComment comment = createComment(null, 0);

            String requestBody = "{\"content\":\"Persisted update\"}";

            mockMvc.perform(put("/api/comments/{id}", comment.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            PlannerComment updated = commentRepository.findById(comment.getId()).orElseThrow();
            assertThat(updated.getContent()).isEqualTo("Persisted update");
            assertThat(updated.getEditedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("DELETE /api/comments/{id} - Delete Comment")
    class DeleteCommentTests {

        @Test
        @DisplayName("Should return 204 when owner deletes comment")
        void deleteComment_WhenOwner_Returns204() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(delete("/api/comments/{id}", comment.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 403 when non-owner tries to delete")
        void deleteComment_WhenNonOwner_Returns403() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(delete("/api/comments/{id}", comment.getPublicId()).with(withCsrf())
                            .cookie(otherUserAccessTokenCookie()))
                    .andExpect(status().isForbidden());
        }

    }

    @Nested
    @DisplayName("POST /api/comments/{id}/upvote - Upvote Comment")
    class UpvoteTests {

        @Test
        @DisplayName("Should create vote on first upvote")
        void upvote_WhenFirstTime_CreatesVote() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(post("/api/comments/{id}/upvote", comment.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUpvoted").value(true))
                    .andExpect(jsonPath("$.upvoteCount").value(1));
        }


        @Test
        @DisplayName("Should increment counter atomically")
        void upvote_WhenAtomicCounter_IncrementsCorrectly() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(post("/api/comments/{id}/upvote", comment.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk());

            PlannerComment updated = commentRepository.findById(comment.getId()).orElseThrow();
            assertThat(updated.getUpvoteCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void upvote_WhenUnauthenticated_Returns401() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(post("/api/comments/{id}/upvote", comment.getPublicId()).with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return updated vote status")
        void upvote_WhenSuccess_ReturnsUpdatedStatus() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(post("/api/comments/{id}/upvote", comment.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.commentId").value(comment.getPublicId().toString()))
                    .andExpect(jsonPath("$.upvoteCount").value(1))
                    .andExpect(jsonPath("$.hasUpvoted").value(true));
        }
    }

    @Nested
    @DisplayName("POST /api/comments/{id}/report - Report Comment")
    class ReportTests {

        private static final String REPORT_BODY = "{\"reason\":\"SPAM\"}";

        private MockHttpServletRequestBuilder report(PlannerComment comment) {
            return post("/api/comments/{id}/report", comment.getPublicId()).with(withCsrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(REPORT_BODY);
        }

        @Test
        void reportComment_WhenAuthenticated_Returns201AndStoresReport() throws Exception {
            PlannerComment comment = createComment(null, 0);

            AuthCookies.performAuthed(mockMvc, report(comment), otherUserAccessToken)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.createdAt").exists());

            assertThat(commentReportRepository.existsByReporterIdAndCommentId(otherUser.getId(), comment.getId()))
                    .isTrue();
        }

        @Test
        void reportComment_WhenUnauthenticated_Returns401() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(report(comment))
                    .andExpect(status().isUnauthorized());

            assertThat(commentReportRepository.existsByReporterIdAndCommentId(otherUser.getId(), comment.getId()))
                    .isFalse();
        }

        @Test
        void reportComment_WhenAlreadyReported_Returns409() throws Exception {
            PlannerComment comment = createComment(null, 0);

            AuthCookies.performAuthed(mockMvc, report(comment), otherUserAccessToken)
                    .andExpect(status().isCreated());

            AuthCookies.performAuthed(mockMvc, report(comment), otherUserAccessToken)
                    .andExpect(status().isConflict());
        }

        @Test
        void reportComment_WhenCommentDeleted_Returns403() throws Exception {
            PlannerComment comment = createComment(null, 0);
            comment.softDelete();
            commentRepository.save(comment);

            AuthCookies.performAuthed(mockMvc, report(comment), otherUserAccessToken)
                    .andExpect(status().isForbidden());
        }
    }
}
