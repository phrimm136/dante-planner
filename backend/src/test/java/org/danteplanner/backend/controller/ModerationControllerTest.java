package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;

/**
 * Tier-2 wire-contract tests for {@link ModerationController}.
 *
 * <p>Pins the JSON request/response shape of every moderation endpoint (field names +
 * representative values) plus the authorization boundary (NORMAL user &rarr; 403,
 * unauthenticated &rarr; 401). These assertions must survive the DTO records migration
 * unmodified.</p>
 *
 * <p>Privacy convention (enforced here): moderation responses carrying user identity expose
 * {@code usernameSuffix} and never a raw numeric {@code userId}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class ModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository plannerCommentRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User regularUser;
    private User adminUser;
    private User moderatorUser;
    private Planner testPlanner;
    private String regularUserToken;
    private String adminToken;
    private String moderatorToken;

    @BeforeEach
    void setUp() {
        plannerCommentRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);

        regularUser = TestDataFactory.createTestUser(userRepository, "user@example.com");
        adminUser = TestDataFactory.createAdmin(userRepository, "admin@example.com");
        moderatorUser = TestDataFactory.createModerator(userRepository, "mod@example.com");

        testPlanner = TestDataFactory.createTestPlanner(plannerRepository, regularUser, true);

        regularUserToken = TestDataFactory.generateAccessToken(jwtTokenService, regularUser);
        adminToken = TestDataFactory.generateAccessToken(jwtTokenService, adminUser);
        moderatorToken = TestDataFactory.generateAccessToken(jwtTokenService, moderatorUser);
    }

    private Cookie adminCookie() {
        return new Cookie("accessToken", adminToken);
    }

    private Cookie moderatorCookie() {
        return new Cookie("accessToken", moderatorToken);
    }

    private Cookie regularUserCookie() {
        return new Cookie("accessToken", regularUserToken);
    }

    private void timeoutRegularUser() throws Exception {
        mockMvc.perform(post("/api/moderation/user/{suffix}/timeout", regularUser.getUsernameSuffix()).with(withCsrf())
                        .cookie(moderatorCookie())
                        .contentType(APPLICATION_JSON)
                        .content("{\"durationMinutes\":60,\"reason\":\"Spam behaviour in comments\"}"))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("POST /api/moderation/user/{suffix}/timeout")
    class TimeoutUserTests {

        @Test
        void timeoutUser_WhenModerator_Returns200WithTimeoutShape() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/timeout", regularUser.getUsernameSuffix()).with(withCsrf())
                            .cookie(moderatorCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"durationMinutes\":120,\"reason\":\"Repeated harassment reports\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usernameSuffix").value(regularUser.getUsernameSuffix()))
                    .andExpect(jsonPath("$.timeoutUntil").exists())
                    .andExpect(jsonPath("$.message").value("User timed out successfully"))
                    .andExpect(jsonPath("$.userId").doesNotExist());
        }

        @Test
        void timeoutUser_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/timeout", regularUser.getUsernameSuffix()).with(withCsrf())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"durationMinutes\":120,\"reason\":\"Repeated harassment reports\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void timeoutUser_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/timeout", regularUser.getUsernameSuffix()).with(withCsrf())
                            .contentType(APPLICATION_JSON)
                            .content("{\"durationMinutes\":120,\"reason\":\"Repeated harassment reports\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/moderation/user/{suffix}/clear-timeout")
    class RemoveTimeoutTests {

        @Test
        void removeTimeout_WhenModerator_Returns200WithTimeoutShape() throws Exception {
            timeoutRegularUser();

            mockMvc.perform(post("/api/moderation/user/{suffix}/clear-timeout", regularUser.getUsernameSuffix()).with(withCsrf())
                            .cookie(moderatorCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Timeout served, appeal accepted\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usernameSuffix").value(regularUser.getUsernameSuffix()))
                    .andExpect(jsonPath("$.message").value("Timeout removed successfully"))
                    .andExpect(jsonPath("$.userId").doesNotExist());
        }

        @Test
        void removeTimeout_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/clear-timeout", regularUser.getUsernameSuffix()).with(withCsrf())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Timeout served, appeal accepted\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void removeTimeout_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/clear-timeout", regularUser.getUsernameSuffix()).with(withCsrf())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Timeout served, appeal accepted\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/moderation/planner/{id}/unpublish")
    class UnpublishPlannerTests {

        @Test
        void unpublishPlanner_WhenModerator_Returns200WithPlannerShape() throws Exception {
            mockMvc.perform(put("/api/moderation/planner/{id}/unpublish", testPlanner.getId()).with(withCsrf())
                            .cookie(moderatorCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(testPlanner.getId().toString()))
                    .andExpect(jsonPath("$.published").value(false))
                    .andExpect(jsonPath("$.message").value("Planner unpublished successfully"));
        }

        @Test
        void unpublishPlanner_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(put("/api/moderation/planner/{id}/unpublish", testPlanner.getId()).with(withCsrf())
                            .cookie(regularUserCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unpublishPlanner_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(put("/api/moderation/planner/{id}/unpublish", testPlanner.getId()).with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/moderation/user/{suffix}/ban")
    class BanUserTests {

        @Test
        void banUser_WhenAdmin_Returns200WithBanShape() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/ban", regularUser.getUsernameSuffix()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Ban evasion and repeated violations\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.banned").value(true))
                    .andExpect(jsonPath("$.message").value("User banned successfully"));
        }

        @Test
        void banUser_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/ban", regularUser.getUsernameSuffix()).with(withCsrf())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Ban evasion and repeated violations\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void banUser_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/ban", regularUser.getUsernameSuffix()).with(withCsrf())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Ban evasion and repeated violations\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/moderation/user/{suffix}/unban")
    class UnbanUserTests {

        @Test
        void unbanUser_WhenAdmin_Returns200WithBanShape() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/unban", regularUser.getUsernameSuffix()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Appeal upheld, ban lifted\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.banned").value(false))
                    .andExpect(jsonPath("$.message").value("User unbanned successfully"));
        }

        @Test
        void unbanUser_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/unban", regularUser.getUsernameSuffix()).with(withCsrf())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Appeal upheld, ban lifted\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unbanUser_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/moderation/user/{suffix}/unban", regularUser.getUsernameSuffix()).with(withCsrf())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Appeal upheld, ban lifted\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/moderation/users")
    class GetAllUsersTests {

        @Test
        void getAllUsers_WhenModerator_Returns200WithUserShape() throws Exception {
            mockMvc.perform(get("/api/moderation/users")
                            .cookie(moderatorCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].usernameEpithet").exists())
                    .andExpect(jsonPath("$[0].usernameSuffix").exists())
                    .andExpect(jsonPath("$[0].role").exists())
                    .andExpect(jsonPath("$[0].isBanned").exists())
                    .andExpect(jsonPath("$[0].bannedAt").exists())
                    .andExpect(jsonPath("$[0].isTimedOut").exists())
                    .andExpect(jsonPath("$[0].timeoutUntil").exists())
                    .andExpect(jsonPath("$[0].userId").doesNotExist())
                    .andExpect(jsonPath("$[0].id").doesNotExist());
        }

        @Test
        void getAllUsers_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(get("/api/moderation/users")
                            .cookie(regularUserCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void getAllUsers_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/moderation/users"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/moderation/users/timed-out")
    class GetTimedOutUsersTests {

        @Test
        void getTimedOutUsers_WhenModerator_Returns200WithTimeoutShape() throws Exception {
            timeoutRegularUser();

            mockMvc.perform(get("/api/moderation/users/timed-out")
                            .cookie(moderatorCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].usernameSuffix").value(regularUser.getUsernameSuffix()))
                    .andExpect(jsonPath("$[0].timeoutUntil").exists())
                    .andExpect(jsonPath("$[0].userId").doesNotExist());
        }

        @Test
        void getTimedOutUsers_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(get("/api/moderation/users/timed-out")
                            .cookie(regularUserCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void getTimedOutUsers_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/moderation/users/timed-out"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/moderation/actions")
    class GetModerationActionsTests {

        @Test
        void getModerationActions_WhenModerator_Returns200WithActionShape() throws Exception {
            timeoutRegularUser();

            mockMvc.perform(get("/api/moderation/actions")
                            .cookie(moderatorCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].actionType").value("TIMEOUT"))
                    .andExpect(jsonPath("$[0].targetType").value("USER"))
                    .andExpect(jsonPath("$[0].targetUuid").exists())
                    .andExpect(jsonPath("$[0].reason").value("Spam behaviour in comments"))
                    .andExpect(jsonPath("$[0].durationMinutes").value(60))
                    .andExpect(jsonPath("$[0].createdAt").exists())
                    .andExpect(jsonPath("$[0].actorUsernameEpithet").value(moderatorUser.getUsernameEpithet()))
                    .andExpect(jsonPath("$[0].actorUsernameSuffix").value(moderatorUser.getUsernameSuffix()))
                    .andExpect(jsonPath("$[0].actorId").doesNotExist());
        }

        @Test
        void getModerationActions_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(get("/api/moderation/actions")
                            .cookie(regularUserCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void getModerationActions_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/moderation/actions"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/moderation/planner/{id}/takedown")
    class TakedownPlannerTests {

        @Test
        void takedownPlanner_WhenModerator_Returns200WithPlannerShape() throws Exception {
            mockMvc.perform(post("/api/moderation/planner/{id}/takedown", testPlanner.getId()).with(withCsrf())
                            .cookie(moderatorCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Content violates community guidelines\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(testPlanner.getId().toString()))
                    .andExpect(jsonPath("$.message").value("Planner taken down successfully"));
        }

        @Test
        void takedownPlanner_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(post("/api/moderation/planner/{id}/takedown", testPlanner.getId()).with(withCsrf())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Content violates community guidelines\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void takedownPlanner_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/moderation/planner/{id}/takedown", testPlanner.getId()).with(withCsrf())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Content violates community guidelines\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/moderation/comments/{publicId}/delete")
    class DeleteCommentTests {

        private PlannerComment createComment() {
            PlannerComment comment = new PlannerComment(
                    testPlanner.getId(), regularUser.getId(), "A comment to be moderated", null, 0);
            return plannerCommentRepository.save(comment);
        }

        @Test
        void deleteComment_WhenModerator_Returns204() throws Exception {
            PlannerComment comment = createComment();

            mockMvc.perform(post("/api/moderation/comments/{publicId}/delete", comment.getPublicId()).with(withCsrf())
                            .cookie(moderatorCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Off-topic and abusive comment\"}"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void deleteComment_WhenNormalUser_Returns403() throws Exception {
            PlannerComment comment = createComment();

            mockMvc.perform(post("/api/moderation/comments/{publicId}/delete", comment.getPublicId()).with(withCsrf())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Off-topic and abusive comment\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteComment_WhenUnauthenticated_Returns401() throws Exception {
            PlannerComment comment = createComment();

            mockMvc.perform(post("/api/moderation/comments/{publicId}/delete", comment.getPublicId()).with(withCsrf())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Off-topic and abusive comment\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
