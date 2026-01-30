package org.danteplanner.backend.integration;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.dto.planner.UpsertPlannerRequest;
import org.danteplanner.backend.entity.ModerationAction;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.repository.ModerationActionRepository;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.ModerationService;
import org.danteplanner.backend.service.token.JwtTokenService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ban enforcement across the full stack.
 * Tests end-to-end flow: ban user → attempt restricted action → verify 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestConfig.class)
class BanEnforcementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private ModerationActionRepository moderationActionRepository;

    @Autowired
    private ModerationService moderationService;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User regularUser;
    private User adminUser;
    private String regularUserToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        moderationActionRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);

        regularUser = TestDataFactory.createTestUser(userRepository, "user@example.com");
        adminUser = TestDataFactory.createAdmin(userRepository, "admin@example.com");

        regularUserToken = TestDataFactory.generateAccessToken(jwtTokenService, regularUser);
        adminToken = TestDataFactory.generateAccessToken(jwtTokenService, adminUser);
    }

    private Cookie userCookie() {
        return new Cookie("accessToken", regularUserToken);
    }

    private Cookie adminCookie() {
        return new Cookie("accessToken", adminToken);
    }

    @Test
    @DisplayName("Banned user cannot upsert planner - returns 403")
    void bannedUser_cannotUpsertPlanner_returns403() throws Exception {
        // Arrange - ban the user
        moderationService.banUser(adminUser.getId(), regularUser.getId(), "Test ban");

        UpsertPlannerRequest request = new UpsertPlannerRequest();
        request.setTitle("Test Planner");
        request.setPlannerType(PlannerType.MIRROR_DUNGEON);
        request.setCategory("5F");
        request.setContent("{}");
        request.setContentVersion(1);

        UUID plannerId = UUID.randomUUID();

        String json = """
                {
                  "id": "%s",
                  "title": "Test Planner",
                  "plannerType": "MIRROR_DUNGEON",
                  "category": "5F",
                  "content": "{\\"selectedKeywords\\":[],\\"selectedBuffIds\\":[100],\\"selectedGiftKeyword\\":\\"Combustion\\",\\"selectedGiftIds\\":[\\"9001\\"],\\"equipment\\":{\\"01\\":{\\"identity\\":{\\"id\\":\\"10101\\",\\"uptie\\":4,\\"level\\":45},\\"egos\\":{\\"ZAYIN\\":{\\"id\\":\\"20101\\",\\"threadspin\\":4}}}},\\"deploymentOrder\\":[1],\\"skillEAState\\":{},\\"floorSelections\\":[],\\"observationGiftIds\\":[],\\"comprehensiveGiftIds\\":[],\\"sectionNotes\\":{}}",
                  "contentVersion": 6
                }
                """.formatted(plannerId);

        // Act & Assert
        mockMvc.perform(put("/api/planner/md/" + plannerId)
                        .cookie(userCookie())
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("USER_BANNED")));

        // Verify planner was not created
        assertEquals(0, plannerRepository.count());
    }

    @Test
    @DisplayName("Banned user cannot toggle publish - returns 403")
    void bannedUser_cannotTogglePublish_returns403() throws Exception {
        // Arrange - create planner first (before ban)
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, regularUser, false);
        UUID plannerId = planner.getId();

        // Ban the user
        moderationService.banUser(adminUser.getId(), regularUser.getId(), "Test ban");

        // Act & Assert
        mockMvc.perform(put("/api/planner/md/" + plannerId + "/publish")
                        .cookie(userCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("USER_BANNED")));

        // Verify planner was not published
        Planner updated = plannerRepository.findById(plannerId).orElseThrow();
        assertFalse(updated.getPublished());
    }

    @Test
    @DisplayName("Banned user cannot create comment - returns 403")
    void bannedUser_cannotCreateComment_returns403() throws Exception {
        // Arrange - create published planner
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, regularUser, true);

        // Ban the user
        moderationService.banUser(adminUser.getId(), regularUser.getId(), "Spam");

        String json = """
                {
                  "content": "Test comment"
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/planner/" + planner.getId() + "/comments")
                        .cookie(userCookie())
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("USER_BANNED")));
    }

    @Test
    @DisplayName("Unbanned user can resume operations - verified by audit trail")
    void unbannedUser_canResumeOperations() {
        // Arrange - ban then unban
        moderationService.banUser(adminUser.getId(), regularUser.getId(), "Test ban");
        moderationService.unbanUser(adminUser.getId(), regularUser.getId(), "Test unban");

        // Assert - user is no longer banned
        User refreshed = userRepository.findById(regularUser.getId()).orElseThrow();
        assertFalse(refreshed.isBanned());
        assertNull(refreshed.getBannedAt());

        // Verify both actions logged
        List<ModerationAction> actions = moderationActionRepository.findAll();
        assertTrue(actions.size() >= 2);
        assertTrue(actions.stream().anyMatch(a -> a.getActionType() == ModerationAction.ActionType.BAN));
        assertTrue(actions.stream().anyMatch(a -> a.getActionType() == ModerationAction.ActionType.UNBAN));
    }

    @Test
    @DisplayName("Ban action is logged to audit trail")
    void banUser_logsAuditAction() {
        // Arrange & Act
        moderationService.banUser(adminUser.getId(), regularUser.getId(), "Test reason");

        // Assert - verify audit log
        List<ModerationAction> actions = moderationActionRepository.findAll();
        assertEquals(1, actions.size());

        ModerationAction action = actions.get(0);
        assertEquals(ModerationAction.ActionType.BAN, action.getActionType());
        assertEquals(adminUser.getId(), action.getActorId());
        assertEquals(regularUser.getPublicId().toString(), action.getTargetUuid());
        assertEquals("Test reason", action.getReason());
        assertNotNull(action.getCreatedAt());
    }

    @Test
    @DisplayName("Unban action is logged to audit trail")
    void unbanUser_logsAuditAction() {
        // Arrange
        moderationService.banUser(adminUser.getId(), regularUser.getId(), "Test ban");

        // Act
        moderationService.unbanUser(adminUser.getId(), regularUser.getId(), "Test unban");

        // Assert - verify both BAN and UNBAN logged
        List<ModerationAction> actions = moderationActionRepository.findAll();
        assertEquals(2, actions.size());

        ModerationAction banAction = actions.stream()
                .filter(a -> a.getActionType() == ModerationAction.ActionType.BAN)
                .findFirst()
                .orElseThrow();
        assertEquals("Test ban", banAction.getReason());

        ModerationAction unbanAction = actions.stream()
                .filter(a -> a.getActionType() == ModerationAction.ActionType.UNBAN)
                .findFirst()
                .orElseThrow();
        assertNotNull(unbanAction.getCreatedAt());
    }

    @Test
    @DisplayName("Concurrent timeout and ban both block actions")
    void concurrentRestrictions_bothBlock() throws Exception {
        // Arrange - both timeout AND ban the user
        moderationService.timeoutUser(adminUser.getId(), regularUser.getId(), 60, "Test timeout");
        moderationService.banUser(adminUser.getId(), regularUser.getId(), "Also banned");

        UUID plannerId = UUID.randomUUID();

        String json = """
                {
                  "id": "%s",
                  "title": "Test Planner",
                  "plannerType": "MIRROR_DUNGEON",
                  "category": "5F",
                  "content": "{\\"selectedKeywords\\":[],\\"selectedBuffIds\\":[100],\\"selectedGiftKeyword\\":\\"Combustion\\",\\"selectedGiftIds\\":[\\"9001\\"],\\"equipment\\":{\\"01\\":{\\"identity\\":{\\"id\\":\\"10101\\",\\"uptie\\":4,\\"level\\":45},\\"egos\\":{\\"ZAYIN\\":{\\"id\\":\\"20101\\",\\"threadspin\\":4}}}},\\"deploymentOrder\\":[1],\\"skillEAState\\":{},\\"floorSelections\\":[],\\"observationGiftIds\\":[],\\"comprehensiveGiftIds\\":[],\\"sectionNotes\\":{}}",
                  "contentVersion": 6
                }
                """.formatted(plannerId);

        // Act & Assert - timeout is checked first, so USER_TIMED_OUT
        mockMvc.perform(put("/api/planner/md/" + plannerId)
                        .cookie(userCookie())
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("USER_TIMED_OUT")));
    }

    @Test
    @DisplayName("GET /me returns ban status for banned user")
    void getMe_bannedUser_returnsBanStatus() throws Exception {
        // Arrange
        moderationService.banUser(adminUser.getId(), regularUser.getId(), "Test reason");

        // Act & Assert
        mockMvc.perform(get("/api/auth/me")
                        .cookie(userCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isBanned", is(true)))
                .andExpect(jsonPath("$.bannedAt").exists())
                .andExpect(jsonPath("$.banReason", is("Test reason")));
    }

    @Test
    @DisplayName("GET /me returns timeout status for timed-out user")
    void getMe_timedOutUser_returnsTimeoutStatus() throws Exception {
        // Arrange
        moderationService.timeoutUser(adminUser.getId(), regularUser.getId(), 30, "Test timeout");

        // Act & Assert
        mockMvc.perform(get("/api/auth/me")
                        .cookie(userCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isTimedOut", is(true)))
                .andExpect(jsonPath("$.timeoutUntil").exists());
    }
}
