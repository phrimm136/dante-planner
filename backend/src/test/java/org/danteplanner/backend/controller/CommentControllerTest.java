package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.config.TestDataInitializer;
import org.danteplanner.backend.entity.Notification;
import org.danteplanner.backend.entity.NotificationType;
import org.danteplanner.backend.entity.PlannerComment;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.RateLimitExceededException;
import org.danteplanner.backend.repository.NotificationRepository;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.CommentService;
import org.danteplanner.backend.service.token.JwtTokenService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestConfig.class, CommentControllerTest.MockCommentServiceConfig.class})
@Transactional
class CommentControllerTest {

    @TestConfiguration
    static class MockCommentServiceConfig {
        @Bean
        @Primary
        public CommentService commentService(
                PlannerCommentRepository commentRepository,
                org.danteplanner.backend.repository.PlannerCommentVoteRepository commentVoteRepository,
                PlannerRepository plannerRepository,
                UserRepository userRepository,
                org.danteplanner.backend.service.NotificationService notificationService) {
            return new CommentService(commentRepository, commentVoteRepository, plannerRepository, userRepository, notificationService);
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
    private JwtTokenService jwtTokenService;

    private User testUser;
    private User otherUser;
    private Planner publishedPlanner;
    private Planner unpublishedPlanner;
    private String accessToken;
    private String otherUserAccessToken;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        notificationRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        otherUser = TestDataFactory.createTestUser(userRepository, "other@example.com");
        publishedPlanner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
        publishedPlanner.setPublished(true);
        plannerRepository.save(publishedPlanner);
        unpublishedPlanner = TestDataFactory.createTestPlanner(plannerRepository, testUser, false);

        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
        otherUserAccessToken = TestDataFactory.generateAccessToken(jwtTokenService, otherUser);
    }

    private Cookie accessTokenCookie() {
        return new Cookie("accessToken", accessToken);
    }

    private Cookie otherUserAccessTokenCookie() {
        return new Cookie("accessToken", otherUserAccessToken);
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
        void getComments_PublishedPlanner_Returns200() throws Exception {
            createComment(null, 0);

            mockMvc.perform(get("/api/planner/{id}/comments", publishedPlanner.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].content").value("Test comment content"));
        }

        @Test
        @DisplayName("Should return 403 when accessing unpublished planner as non-owner")
        void getComments_UnpublishedPlanner_Returns403() throws Exception {
            mockMvc.perform(get("/api/planner/{id}/comments", unpublishedPlanner.getId())
                            .cookie(otherUserAccessTokenCookie()))
                    .andExpect(status().isForbidden());
        }


        @Test
        @DisplayName("Should return empty array for planner with no comments")
        void getComments_NoComments_ReturnsEmptyArray() throws Exception {
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
        void createComment_ValidTopLevel_Returns201() throws Exception {
            String requestBody = "{\"content\":\"New comment\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.content").value("New comment"))
                    .andExpect(jsonPath("$.depth").value(0))
                    .andExpect(jsonPath("$.parentCommentId").doesNotExist());
        }

        @Test
        @DisplayName("Should set correct depth when replying with parentId")
        void createComment_WithParent_SetsCorrectDepth() throws Exception {
            PlannerComment parent = createComment(null, 0);

            String requestBody = "{\"content\":\"Reply\",\"parentCommentId\":" + parent.getId() + "}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.depth").value(1))
                    .andExpect(jsonPath("$.parentCommentId").value(parent.getId()));
        }

        @Test
        @DisplayName("Should return 403 when unauthenticated")
        void createComment_Unauthenticated_Returns403() throws Exception {
            String requestBody = "{\"content\":\"Test\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when commenting on unpublished planner")
        void createComment_UnpublishedPlanner_Returns403() throws Exception {
            String requestBody = "{\"content\":\"Test\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", unpublishedPlanner.getId())
                            .cookie(otherUserAccessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when content is empty")
        void createComment_EmptyContent_Returns400() throws Exception {
            String requestBody = "{\"content\":\"\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Depth 0 - Top-level comment has depth 0")
        void createComment_TopLevel_HasDepth0() throws Exception {
            String requestBody = "{\"content\":\"Top-level\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.depth").value(0));
        }

        @Test
        @DisplayName("Depth 1-5 - Replies increment depth correctly")
        void createComment_Replies_IncrementDepth() throws Exception {
            PlannerComment depth0 = createComment(null, 0);
            String requestBody1 = "{\"content\":\"Depth 1\",\"parentCommentId\":" + depth0.getId() + "}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody1))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.depth").value(1))
                    .andExpect(jsonPath("$.parentCommentId").value(depth0.getId()));
        }

        @Test
        @DisplayName("Depth 6 - Comment at depth 6 becomes sibling of parent (flattening)")
        void createComment_Depth6Flattening_BecomesSiblingOfParent() throws Exception {
            PlannerComment depth0 = createComment(null, 0);
            PlannerComment depth1 = createComment(depth0.getId(), 1);
            PlannerComment depth2 = createComment(depth1.getId(), 2);
            PlannerComment depth3 = createComment(depth2.getId(), 3);
            PlannerComment depth4 = createComment(depth3.getId(), 4);
            PlannerComment depth5 = createComment(depth4.getId(), 5);

            String requestBody = "{\"content\":\"Depth 6 reply\",\"parentCommentId\":" + depth5.getId() + "}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.depth").value(5))
                    .andExpect(jsonPath("$.parentCommentId").value(depth4.getId()));
        }

        @Test
        @DisplayName("Depth 6 - Verify comment with depth 5 parent gets flattened")
        void createComment_Depth6_FlattensToDepth5() throws Exception {
            PlannerComment depth0 = createComment(null, 0);
            PlannerComment depth1 = createComment(depth0.getId(), 1);
            PlannerComment depth2 = createComment(depth1.getId(), 2);
            PlannerComment depth3 = createComment(depth2.getId(), 3);
            PlannerComment depth4 = createComment(depth3.getId(), 4);
            PlannerComment depth5 = createComment(depth4.getId(), 5);

            String requestBody = "{\"content\":\"Should flatten\",\"parentCommentId\":" + depth5.getId() + "}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.depth").value(5));

            List<PlannerComment> allComments = commentRepository.findByPlannerId(publishedPlanner.getId());
            long depth5Count = allComments.stream().filter(c -> c.getDepth() == 5).count();
            assertThat(depth5Count).isEqualTo(2);
        }

        @Test
        @DisplayName("Should create notification when replying to comment")
        void createComment_ReplyToComment_CreatesNotification() throws Exception {
            PlannerComment parentComment = createComment(null, 0);

            String requestBody = "{\"content\":\"Reply\",\"parentCommentId\":" + parentComment.getId() + "}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
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
        void createComment_TopLevelOnPlanner_CreatesNotification() throws Exception {
            String requestBody = "{\"content\":\"Top-level comment\"}";

            mockMvc.perform(post("/api/planner/{id}/comments", publishedPlanner.getId())
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
        void updateComment_Owner_Returns200() throws Exception {
            PlannerComment comment = createComment(null, 0);

            String requestBody = "{\"content\":\"Updated content\"}";

            mockMvc.perform(put("/api/comments/{id}", comment.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("Updated content"))
                    .andExpect(jsonPath("$.editedAt").exists());
        }

        @Test
        @DisplayName("Should return 403 when non-owner tries to edit")
        void updateComment_NonOwner_Returns403() throws Exception {
            PlannerComment comment = createComment(null, 0);

            String requestBody = "{\"content\":\"Hacked content\"}";

            mockMvc.perform(put("/api/comments/{id}", comment.getId())
                            .cookie(otherUserAccessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should persist updated content")
        void updateComment_Owner_PersistsContent() throws Exception {
            PlannerComment comment = createComment(null, 0);

            String requestBody = "{\"content\":\"Persisted update\"}";

            mockMvc.perform(put("/api/comments/{id}", comment.getId())
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
        void deleteComment_Owner_Returns204() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(delete("/api/comments/{id}", comment.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 403 when non-owner tries to delete")
        void deleteComment_NonOwner_Returns403() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(delete("/api/comments/{id}", comment.getId())
                            .cookie(otherUserAccessTokenCookie()))
                    .andExpect(status().isForbidden());
        }

    }

    @Nested
    @DisplayName("POST /api/comments/{id}/upvote - Upvote Comment")
    class UpvoteTests {

        @Test
        @DisplayName("Should create vote on first upvote")
        void upvote_FirstTime_CreatesVote() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(post("/api/comments/{id}/upvote", comment.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUpvoted").value(true))
                    .andExpect(jsonPath("$.upvoteCount").value(1));
        }


        @Test
        @DisplayName("Should increment counter atomically")
        void upvote_AtomicCounter_IncrementsCorrectly() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(post("/api/comments/{id}/upvote", comment.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk());

            PlannerComment updated = commentRepository.findById(comment.getId()).orElseThrow();
            assertThat(updated.getUpvoteCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 403 when unauthenticated")
        void upvote_Unauthenticated_Returns403() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(post("/api/comments/{id}/upvote", comment.getId()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return updated vote status")
        void upvote_Success_ReturnsUpdatedStatus() throws Exception {
            PlannerComment comment = createComment(null, 0);

            mockMvc.perform(post("/api/comments/{id}/upvote", comment.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.commentId").value(comment.getId()))
                    .andExpect(jsonPath("$.upvoteCount").value(1))
                    .andExpect(jsonPath("$.hasUpvoted").value(true));
        }
    }
}
