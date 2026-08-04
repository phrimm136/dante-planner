package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.support.AuthCookies;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class AdminModerationControllerIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerVoteRepository voteRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

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

        regularUser = TestDataFactory.createTestUser(userRepository, "user@example.com");
        adminUser = TestDataFactory.createAdmin(userRepository, "admin@example.com");
        moderatorUser = TestDataFactory.createModerator(userRepository, "mod@example.com");

        testPlanner = TestDataFactory.createTestPlanner(plannerRepository, regularUser, true);

        regularUserToken = TestDataFactory.generateAccessToken(jwtTokenService, regularUser);
        adminToken = TestDataFactory.generateAccessToken(jwtTokenService, adminUser);
        moderatorToken = TestDataFactory.generateAccessToken(jwtTokenService, moderatorUser);
    }

    private Cookie adminCookie() {
        return AuthCookies.accessToken(adminToken);
    }

    private Cookie moderatorCookie() {
        return AuthCookies.accessToken(moderatorToken);
    }

    private Cookie regularUserCookie() {
        return AuthCookies.accessToken(regularUserToken);
    }

    @Nested
    @DisplayName("POST /api/moderation/planner/{id}/hide-from-recommended")
    class HideFromRecommendedTests {

        @Test
        @DisplayName("Should return 200 when admin role hides planner")
        void hideFromRecommended_WhenAdminRole_Returns200() throws Exception {
            String hideRequest = """
                {
                    "reason": "Inappropriate content violates community guidelines"
                }
                """;

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isTrue();
        }

        @Test
        @DisplayName("Should allow a moderator to hide a planner")
        void hideFromRecommended_WhenModeratorRole_Returns200() throws Exception {
            String hideRequest = """
                {
                    "reason": "Contains misleading information that could harm users"
                }
                """;

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(moderatorCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 403 when regular user attempts to hide planner")
        void hideFromRecommended_WhenRegularUser_Returns403() throws Exception {
            String hideRequest = """
                {
                    "reason": "Test reason that should not work"
                }
                """;

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void hideFromRecommended_WhenUnauthenticated_Returns401() throws Exception {
            String hideRequest = """
                {
                    "reason": "Test reason without authentication"
                }
                """;

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 200 with short reason (no min length constraint)")
        void hideFromRecommended_WhenShortReason_Returns200() throws Exception {
            String hideRequest = """
                {
                    "reason": "Too short"
                }
                """;

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 400 when reason is too long")
        void hideFromRecommended_WhenLongReason_Returns400() throws Exception {
            String longReason = "x".repeat(501);
            String hideRequest = String.format("""
                {
                    "reason": "%s"
                }
                """, longReason);

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Reason must be at most 500 characters")));
        }

        @Test
        @DisplayName("Should return 400 when reason is null")
        void hideFromRecommended_WhenNullReason_Returns400() throws Exception {
            String hideRequest = """
                {
                    "reason": null
                }
                """;

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should set hiddenFromRecommended flag to true")
        void hideFromRecommended_WhenValidReason_SetsHiddenFlag() throws Exception {
            String hideRequest = """
                {
                    "reason": "This planner contains inappropriate content that violates community guidelines"
                }
                """;

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isTrue();
        }

        @Test
        @DisplayName("Should set hiddenAt timestamp")
        void hideFromRecommended_WhenValidReason_SetsHiddenAt() throws Exception {
            String hideRequest = """
                {
                    "reason": "This planner violates community standards and must be hidden"
                }
                """;

            Instant beforeHide = Instant.now();

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getModeration().getHiddenAt()).isNotNull();
            assertThat(updated.getModeration().getHiddenAt()).isAfterOrEqualTo(beforeHide);
        }

        @Test
        @DisplayName("Should set hiddenByModeratorId to moderator's ID")
        void hideFromRecommended_WhenValidReason_SetsModeratorId() throws Exception {
            String hideRequest = """
                {
                    "reason": "Content violates guidelines and requires moderation action"
                }
                """;

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getModeration().getHiddenByModeratorId()).isEqualTo(adminUser.getId());
        }

        @Test
        @DisplayName("Should set hideReason to provided reason")
        void hideFromRecommended_WhenValidReason_SetsHideReason() throws Exception {
            String reason = "This planner contains inappropriate content that violates community guidelines";
            String hideRequest = String.format("""
                {
                    "reason": "%s"
                }
                """, reason);

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getModeration().getHiddenReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("Should set all moderation metadata together")
        void hideFromRecommended_WhenValidReason_SetsAllMetadata() throws Exception {
            String reason = "Comprehensive metadata test - violations of community standards detected";
            String hideRequest = String.format("""
                {
                    "reason": "%s"
                }
                """, reason);

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isTrue();
            assertThat(updated.getModeration().getHiddenAt()).isNotNull();
            assertThat(updated.getModeration().getHiddenByModeratorId()).isEqualTo(adminUser.getId());
            assertThat(updated.getModeration().getHiddenReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("Should preserve existing votes when hiding planner")
        void hideFromRecommended_WhenVotes_PreservesVotes() throws Exception {
            PlannerVote vote1 = new PlannerVote(regularUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote1);

            PlannerVote vote2 = new PlannerVote(adminUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote2);

            statsRepository.save(PlannerStats.builder()
                    .plannerId(testPlanner.getId())
                    .upvotes(2)
                    .build());

            String hideRequest = """
                {
                    "reason": "This planner violates community guidelines but votes must be preserved"
                }
                """;

            mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            assertThat(voteRepository.findByUserIdAndPlannerId(regularUser.getId(), testPlanner.getId())).isPresent();
            assertThat(voteRepository.findByUserIdAndPlannerId(adminUser.getId(), testPlanner.getId())).isPresent();

            assertThat(statsRepository.findById(testPlanner.getId()).orElseThrow().getUpvotes()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("POST /api/moderation/planner/{id}/unhide-from-recommended")
    class UnhideFromRecommendedTests {

        @Test
        @DisplayName("Should return 200 when admin role unhides planner")
        void unhideFromRecommended_WhenAdminRole_Returns200() throws Exception {
            // Re-read first: with the test no longer wrapped in a transaction, AFTER_COMMIT
            // listeners run and bump this row's version, leaving the instance from @BeforeEach
            // stale.
            Planner hidden = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            hidden.hideFromRecommended(adminUser.getId(), "Test hide reason");
            plannerRepository.save(hidden);

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isFalse();
        }

        @Test
        @DisplayName("Should allow a moderator to unhide a planner")
        void unhideFromRecommended_WhenModeratorRole_Returns200() throws Exception {
            testPlanner.hideFromRecommended(moderatorUser.getId(), "Test hide reason");
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(moderatorCookie()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 403 when regular user attempts to unhide planner")
        void unhideFromRecommended_WhenRegularUser_Returns403() throws Exception {
            // Re-read first: with the test no longer wrapped in a transaction, AFTER_COMMIT
            // listeners run and bump this row's version, leaving the instance from @BeforeEach
            // stale.
            Planner hidden = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            hidden.hideFromRecommended(adminUser.getId(), "Test hide reason");
            plannerRepository.save(hidden);

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(regularUserCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should clear hiddenFromRecommended flag")
        void unhideFromRecommended_WhenHiddenPlanner_ClearsHiddenFlag() throws Exception {
            // Re-read first: with the test no longer wrapped in a transaction, AFTER_COMMIT
            // listeners run and bump this row's version, leaving the instance from @BeforeEach
            // stale.
            Planner hidden = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            hidden.hideFromRecommended(adminUser.getId(), "Test hide reason");
            plannerRepository.save(hidden);

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isFalse();
        }

        @Test
        @DisplayName("Should clear hiddenAt timestamp")
        void unhideFromRecommended_WhenHiddenPlanner_ClearsHiddenAt() throws Exception {
            // Re-read first: with the test no longer wrapped in a transaction, AFTER_COMMIT
            // listeners run and bump this row's version, leaving the instance from @BeforeEach
            // stale.
            Planner hidden = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            hidden.hideFromRecommended(adminUser.getId(), "Test hide reason");
            plannerRepository.save(hidden);

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getModeration().getHiddenAt()).isNull();
        }

        @Test
        @DisplayName("Should clear hiddenByModeratorId")
        void unhideFromRecommended_WhenHiddenPlanner_ClearsModeratorId() throws Exception {
            // Re-read first: with the test no longer wrapped in a transaction, AFTER_COMMIT
            // listeners run and bump this row's version, leaving the instance from @BeforeEach
            // stale.
            Planner hidden = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            hidden.hideFromRecommended(adminUser.getId(), "Test hide reason");
            plannerRepository.save(hidden);

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getModeration().getHiddenByModeratorId()).isNull();
        }

        @Test
        @DisplayName("Should clear hideReason")
        void unhideFromRecommended_WhenHiddenPlanner_ClearsHideReason() throws Exception {
            // Re-read first: with the test no longer wrapped in a transaction, AFTER_COMMIT
            // listeners run and bump this row's version, leaving the instance from @BeforeEach
            // stale.
            Planner hidden = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            hidden.hideFromRecommended(adminUser.getId(), "Test hide reason");
            plannerRepository.save(hidden);

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getModeration().getHiddenReason()).isNull();
        }

        @Test
        @DisplayName("Should clear all moderation metadata together")
        void unhideFromRecommended_WhenHiddenPlanner_ClearsAllMetadata() throws Exception {
            testPlanner.hideFromRecommended(adminUser.getId(), "Comprehensive metadata clearing test");
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isFalse();
            assertThat(updated.getModeration().getHiddenAt()).isNull();
            assertThat(updated.getModeration().getHiddenByModeratorId()).isNull();
            assertThat(updated.getModeration().getHiddenReason()).isNull();
        }

        @Test
        @DisplayName("Should be idempotent when unhiding already unhidden planner")
        void unhideFromRecommended_WhenAlreadyUnhidden_IsIdempotent() throws Exception {
            assertThat(testPlanner.getHiddenFromRecommended()).isFalse();

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isFalse();
        }

        @Test
        @DisplayName("Should preserve votes when unhiding planner")
        void unhideFromRecommended_WhenVotes_PreservesVotes() throws Exception {
            // Re-read first: with the test no longer wrapped in a transaction, AFTER_COMMIT
            // listeners run and bump this row's version, leaving the instance from @BeforeEach
            // stale.
            Planner hidden = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            hidden.hideFromRecommended(adminUser.getId(), "Test hide reason");
            plannerRepository.save(hidden);

            PlannerVote vote1 = new PlannerVote(regularUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote1);

            PlannerVote vote2 = new PlannerVote(moderatorUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote2);

            statsRepository.save(PlannerStats.builder()
                    .plannerId(testPlanner.getId())
                    .upvotes(2)
                    .build());

            mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", testPlanner.getId()).with(withCsrf())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            assertThat(voteRepository.findByUserIdAndPlannerId(regularUser.getId(), testPlanner.getId())).isPresent();
            assertThat(voteRepository.findByUserIdAndPlannerId(moderatorUser.getId(), testPlanner.getId())).isPresent();

            assertThat(statsRepository.findById(testPlanner.getId()).orElseThrow().getUpvotes()).isEqualTo(2);
        }
    }
}
