package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerVote;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.VoteType;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.PlannerVoteRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.token.JwtTokenService;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestConfig.class)
class AdminModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerVoteRepository voteRepository;

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
        voteRepository.deleteAll();
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

    @Nested
    @DisplayName("POST /api/admin/planner/{id}/hide-from-recommended")
    class HideFromRecommendedTests {

        @Test
        @DisplayName("Should return 200 when admin role hides planner")
        void hideFromRecommended_AdminRole_Returns200() throws Exception {
            String hideRequest = """
                {
                    "reason": "Inappropriate content violates community guidelines"
                }
                """;

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isTrue();
        }

        @Test
        @DisplayName("Should return 403 when moderator role attempts to hide planner")
        void hideFromRecommended_ModeratorRole_Returns403() throws Exception {
            String hideRequest = """
                {
                    "reason": "Contains misleading information that could harm users"
                }
                """;

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(moderatorCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when regular user attempts to hide planner")
        void hideFromRecommended_RegularUser_Returns403() throws Exception {
            String hideRequest = """
                {
                    "reason": "Test reason that should not work"
                }
                """;

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void hideFromRecommended_Unauthenticated_Returns401() throws Exception {
            String hideRequest = """
                {
                    "reason": "Test reason without authentication"
                }
                """;

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 200 with short reason (no min length constraint)")
        void hideFromRecommended_ShortReason_Returns200() throws Exception {
            String hideRequest = """
                {
                    "reason": "Too short"
                }
                """;

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 400 when reason is too long")
        void hideFromRecommended_LongReason_Returns400() throws Exception {
            String longReason = "x".repeat(501);
            String hideRequest = String.format("""
                {
                    "reason": "%s"
                }
                """, longReason);

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Reason must be at most 500 characters")));
        }

        @Test
        @DisplayName("Should return 400 when reason is null")
        void hideFromRecommended_NullReason_Returns400() throws Exception {
            String hideRequest = """
                {
                    "reason": null
                }
                """;

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should set hiddenFromRecommended flag to true")
        void hideFromRecommended_ValidReason_SetsHiddenFlag() throws Exception {
            String hideRequest = """
                {
                    "reason": "This planner contains inappropriate content that violates community guidelines"
                }
                """;

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isTrue();
        }

        @Test
        @DisplayName("Should set hiddenAt timestamp")
        void hideFromRecommended_ValidReason_SetsHiddenAt() throws Exception {
            String hideRequest = """
                {
                    "reason": "This planner violates community standards and must be hidden"
                }
                """;

            Instant beforeHide = Instant.now();

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenAt()).isNotNull();
            assertThat(updated.getHiddenAt()).isAfterOrEqualTo(beforeHide);
        }

        @Test
        @DisplayName("Should set hiddenByModeratorId to moderator's ID")
        void hideFromRecommended_ValidReason_SetsModeratorId() throws Exception {
            String hideRequest = """
                {
                    "reason": "Content violates guidelines and requires moderation action"
                }
                """;

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenByModeratorId()).isEqualTo(adminUser.getId());
        }

        @Test
        @DisplayName("Should set hideReason to provided reason")
        void hideFromRecommended_ValidReason_SetsHideReason() throws Exception {
            String reason = "This planner contains inappropriate content that violates community guidelines";
            String hideRequest = String.format("""
                {
                    "reason": "%s"
                }
                """, reason);

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("Should set all moderation metadata together")
        void hideFromRecommended_ValidReason_SetsAllMetadata() throws Exception {
            String reason = "Comprehensive metadata test - violations of community standards detected";
            String hideRequest = String.format("""
                {
                    "reason": "%s"
                }
                """, reason);

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isTrue();
            assertThat(updated.getHiddenAt()).isNotNull();
            assertThat(updated.getHiddenByModeratorId()).isEqualTo(adminUser.getId());
            assertThat(updated.getHiddenReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("Should preserve existing votes when hiding planner")
        void hideFromRecommended_WithVotes_PreservesVotes() throws Exception {
            PlannerVote vote1 = new PlannerVote(regularUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote1);

            PlannerVote vote2 = new PlannerVote(adminUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote2);

            testPlanner.setUpvotes(2);
            plannerRepository.save(testPlanner);

            String hideRequest = """
                {
                    "reason": "This planner violates community guidelines but votes must be preserved"
                }
                """;

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content(hideRequest))
                    .andExpect(status().isOk());

            assertThat(voteRepository.findByUserIdAndPlannerId(regularUser.getId(), testPlanner.getId())).isPresent();
            assertThat(voteRepository.findByUserIdAndPlannerId(adminUser.getId(), testPlanner.getId())).isPresent();

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getUpvotes()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("POST /api/admin/planner/{id}/unhide-from-recommended")
    class UnhideFromRecommendedTests {

        @Test
        @DisplayName("Should return 200 when admin role unhides planner")
        void unhideFromRecommended_AdminRole_Returns200() throws Exception {
            testPlanner.setHiddenFromRecommended(true);
            testPlanner.setHiddenAt(Instant.now());
            testPlanner.setHiddenByModeratorId(adminUser.getId());
            testPlanner.setHiddenReason("Test hide reason");
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isFalse();
        }

        @Test
        @DisplayName("Should return 403 when moderator role attempts to unhide planner")
        void unhideFromRecommended_ModeratorRole_Returns403() throws Exception {
            testPlanner.setHiddenFromRecommended(true);
            testPlanner.setHiddenAt(Instant.now());
            testPlanner.setHiddenByModeratorId(moderatorUser.getId());
            testPlanner.setHiddenReason("Test hide reason");
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(moderatorCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when regular user attempts to unhide planner")
        void unhideFromRecommended_RegularUser_Returns403() throws Exception {
            testPlanner.setHiddenFromRecommended(true);
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(regularUserCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should clear hiddenFromRecommended flag")
        void unhideFromRecommended_HiddenPlanner_ClearsHiddenFlag() throws Exception {
            testPlanner.setHiddenFromRecommended(true);
            testPlanner.setHiddenAt(Instant.now());
            testPlanner.setHiddenByModeratorId(adminUser.getId());
            testPlanner.setHiddenReason("Test hide reason");
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isFalse();
        }

        @Test
        @DisplayName("Should clear hiddenAt timestamp")
        void unhideFromRecommended_HiddenPlanner_ClearsHiddenAt() throws Exception {
            testPlanner.setHiddenFromRecommended(true);
            testPlanner.setHiddenAt(Instant.now());
            testPlanner.setHiddenByModeratorId(adminUser.getId());
            testPlanner.setHiddenReason("Test hide reason");
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenAt()).isNull();
        }

        @Test
        @DisplayName("Should clear hiddenByModeratorId")
        void unhideFromRecommended_HiddenPlanner_ClearsModeratorId() throws Exception {
            testPlanner.setHiddenFromRecommended(true);
            testPlanner.setHiddenAt(Instant.now());
            testPlanner.setHiddenByModeratorId(adminUser.getId());
            testPlanner.setHiddenReason("Test hide reason");
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenByModeratorId()).isNull();
        }

        @Test
        @DisplayName("Should clear hideReason")
        void unhideFromRecommended_HiddenPlanner_ClearsHideReason() throws Exception {
            testPlanner.setHiddenFromRecommended(true);
            testPlanner.setHiddenAt(Instant.now());
            testPlanner.setHiddenByModeratorId(adminUser.getId());
            testPlanner.setHiddenReason("Test hide reason");
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenReason()).isNull();
        }

        @Test
        @DisplayName("Should clear all moderation metadata together")
        void unhideFromRecommended_HiddenPlanner_ClearsAllMetadata() throws Exception {
            testPlanner.setHiddenFromRecommended(true);
            testPlanner.setHiddenAt(Instant.now());
            testPlanner.setHiddenByModeratorId(adminUser.getId());
            testPlanner.setHiddenReason("Comprehensive metadata clearing test");
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isFalse();
            assertThat(updated.getHiddenAt()).isNull();
            assertThat(updated.getHiddenByModeratorId()).isNull();
            assertThat(updated.getHiddenReason()).isNull();
        }

        @Test
        @DisplayName("Should be idempotent when unhiding already unhidden planner")
        void unhideFromRecommended_AlreadyUnhidden_IsIdempotent() throws Exception {
            assertThat(testPlanner.getHiddenFromRecommended()).isFalse();

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getHiddenFromRecommended()).isFalse();
        }

        @Test
        @DisplayName("Should preserve votes when unhiding planner")
        void unhideFromRecommended_WithVotes_PreservesVotes() throws Exception {
            testPlanner.setHiddenFromRecommended(true);
            testPlanner.setHiddenAt(Instant.now());
            testPlanner.setHiddenByModeratorId(adminUser.getId());
            testPlanner.setHiddenReason("Test hide reason");
            plannerRepository.save(testPlanner);

            PlannerVote vote1 = new PlannerVote(regularUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote1);

            PlannerVote vote2 = new PlannerVote(moderatorUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote2);

            testPlanner.setUpvotes(2);
            plannerRepository.save(testPlanner);

            mockMvc.perform(post("/api/admin/planner/{id}/unhide-from-recommended", testPlanner.getId())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());

            assertThat(voteRepository.findByUserIdAndPlannerId(regularUser.getId(), testPlanner.getId())).isPresent();
            assertThat(voteRepository.findByUserIdAndPlannerId(moderatorUser.getId(), testPlanner.getId())).isPresent();

            Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(updated.getUpvotes()).isEqualTo(2);
        }
    }
}
