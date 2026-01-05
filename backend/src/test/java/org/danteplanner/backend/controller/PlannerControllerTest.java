package org.danteplanner.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.dto.planner.CreatePlannerRequest;
import org.danteplanner.backend.dto.planner.ImportPlannersRequest;
import org.danteplanner.backend.dto.planner.UpdatePlannerRequest;
import org.danteplanner.backend.dto.planner.VoteRequest;
import org.danteplanner.backend.entity.VoteType;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.token.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.danteplanner.backend.config.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for PlannerController.
 *
 * <p>Tests all REST API endpoints including authentication,
 * validation, error handling, and business logic.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class PlannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private org.danteplanner.backend.repository.PlannerVoteRepository plannerVoteRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User testUser;
    private User otherUser;
    private String accessToken;
    private String otherUserAccessToken;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        // Clean up existing data
        plannerRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        testUser = User.builder()
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .usernameKeyword("W_CORP")
                .usernameSuffix("test1")
                .build();
        testUser = userRepository.save(testUser);

        otherUser = User.builder()
                .email("other@example.com")
                .provider("google")
                .providerId("google-456")
                .usernameKeyword("W_CORP")
                .usernameSuffix("test2")
                .build();
        otherUser = userRepository.save(otherUser);

        // Generate JWT tokens
        accessToken = jwtTokenService.generateAccessToken(testUser.getId(), testUser.getEmail());
        otherUserAccessToken = jwtTokenService.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        // Generate device ID
        deviceId = UUID.randomUUID();
    }

    private Cookie accessTokenCookie() {
        return new Cookie("accessToken", accessToken);
    }

    private Cookie deviceIdCookie() {
        return new Cookie("deviceId", deviceId.toString());
    }

    /**
     * Minimal valid planner content that passes PlannerContentValidator.
     * Equipment: 12 sinners (01-12), each with identity + ZAYIN EGO.
     * DeploymentOrder: 0-indexed (0-11).
     * selectedBuffIds: valid buff IDs from game data.
     * selectedGiftKeyword + selectedGiftIds: valid gift selection from Combustion pool.
     */
    private static final String VALID_CONTENT = """
        {
            "title":"Test",
            "category":"5F",
            "selectedKeywords":[],
            "selectedBuffIds":[100,201],
            "selectedGiftKeyword":"Combustion",
            "selectedGiftIds":["9001"],
            "equipment":{
                "01":{"identity":{"id":"10101","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20101","threadspin":4}}},
                "02":{"identity":{"id":"10201","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20201","threadspin":4}}},
                "03":{"identity":{"id":"10301","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20301","threadspin":4}}},
                "04":{"identity":{"id":"10401","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20401","threadspin":4}}},
                "05":{"identity":{"id":"10501","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20501","threadspin":4}}},
                "06":{"identity":{"id":"10601","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20601","threadspin":4}}},
                "07":{"identity":{"id":"10701","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20701","threadspin":4}}},
                "08":{"identity":{"id":"10801","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20801","threadspin":4}}},
                "09":{"identity":{"id":"10901","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20901","threadspin":4}}},
                "10":{"identity":{"id":"11001","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21001","threadspin":4}}},
                "11":{"identity":{"id":"11101","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21101","threadspin":4}}},
                "12":{"identity":{"id":"11201","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21201","threadspin":4}}}
            },
            "deploymentOrder":[0,1,2,3,4,5],
            "floorSelections":[{"themePackId":"1001","difficulty":0,"giftIds":["9002"]}],
            "sectionNotes":{}
        }
        """.trim().replace("\n", "").replace(" ", "");

    private CreatePlannerRequest createValidPlannerRequest() {
        CreatePlannerRequest request = new CreatePlannerRequest();
        request.setCategory("5F");
        request.setTitle("Test Planner");
        request.setStatus("draft");
        request.setContent(VALID_CONTENT);
        request.setContentVersion(6);
        request.setPlannerType(PlannerType.MIRROR_DUNGEON);
        return request;
    }

    private Planner createTestPlanner(User user) {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Test Planner")
                .category("5F")
                .status("draft")
                .content(VALID_CONTENT)
                .syncVersion(1L)
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .savedAt(Instant.now())
                .build();
        return plannerRepository.save(planner);
    }

    @Nested
    @DisplayName("POST /api/planner/md - Create Planner")
    class CreatePlannerTests {

        @Test
        @DisplayName("Should return 201 when creating planner with valid data")
        void createPlanner_ValidData_Returns201() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.title").value("Test Planner"))
                    .andExpect(jsonPath("$.category").value("5F"))
                    .andExpect(jsonPath("$.status").value("draft"))
                    .andExpect(jsonPath("$.syncVersion").value(1))
                    .andExpect(jsonPath("$.userId").value(testUser.getId()))
                    .andExpect(jsonPath("$.schemaVersion").value(1))
                    .andExpect(jsonPath("$.contentVersion").value(6))
                    .andExpect(jsonPath("$.plannerType").value("MIRROR_DUNGEON"));
        }

        @Test
        @DisplayName("Should return 403 without JWT cookie")
        void createPlanner_NoAuth_Returns403() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(post("/api/planner/md")
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 400 when category is missing")
        void createPlanner_MissingCategory_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            request.setCategory(null);

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when content is missing")
        void createPlanner_MissingContent_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            request.setContent(null);

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when content exceeds 50KB")
        void createPlanner_ContentTooLarge_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            // Create valid content structure that exceeds 50KB
            String largeTitle = "x".repeat(52000);
            String largeContent = String.format(
                "{\"title\":\"%s\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{}}",
                largeTitle
            );
            request.setContent(largeContent);

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SIZE_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 400 when note exceeds 1KB")
        void createPlanner_NoteTooLarge_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            // Create valid content structure with a note larger than 1KB
            String largeNote = "x".repeat(1100);
            String contentWithLargeNote = String.format(
                "{\"title\":\"Test\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{\"section1\":{\"content\":{\"type\":\"doc\",\"text\":\"%s\"}}}}",
                largeNote
            );
            request.setContent(contentWithLargeNote);

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when contentVersion is missing")
        void createPlanner_MissingContentVersion_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            request.setContentVersion(null);

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when plannerType is missing")
        void createPlanner_MissingPlannerType_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            request.setPlannerType(null);

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when contentVersion is not positive")
        void createPlanner_NonPositiveContentVersion_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            request.setContentVersion(0);

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 409 when user exceeds 100 planner limit")
        void createPlanner_ExceedsLimit_Returns409() throws Exception {
            // Create 100 planners for the test user
            for (int i = 0; i < 100; i++) {
                createTestPlanner(testUser);
            }

            CreatePlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 400 when category is invalid for planner type")
        void createPlanner_InvalidCategoryForType_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            request.setCategory("INVALID_CATEGORY");

            // Note: INVALID_CATEGORY is mapped to generic VALIDATION_ERROR in GlobalExceptionHandler
            // to prevent schema probing attacks
            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when MD category used with RR planner type")
        void createPlanner_MdCategoryWithRrType_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            request.setPlannerType(PlannerType.REFRACTED_RAILWAY);
            request.setCategory("5F"); // MD category, invalid for RR

            // Note: INVALID_CATEGORY is mapped to generic VALIDATION_ERROR in GlobalExceptionHandler
            // to prevent schema probing attacks
            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md - List Planners")
    class ListPlannersTests {

        @Test
        @DisplayName("Should return only current user's non-deleted planners")
        void getPlanners_ReturnsOnlyUsersPlanners() throws Exception {
            // Create planners for test user
            createTestPlanner(testUser);
            createTestPlanner(testUser);

            // Create planner for other user (should not be returned)
            createTestPlanner(otherUser);

            mockMvc.perform(get("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("Should not return soft-deleted planners")
        void getPlanners_ExcludesDeletedPlanners() throws Exception {
            Planner planner1 = createTestPlanner(testUser);
            Planner planner2 = createTestPlanner(testUser);

            // Soft delete one planner
            planner2.softDelete();
            plannerRepository.save(planner2);

            mockMvc.perform(get("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(planner1.getId().toString()));
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void getPlanners_NoAuth_Returns403() throws Exception {
            mockMvc.perform(get("/api/planner/md"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should support pagination")
        void getPlanners_SupportsPagination() throws Exception {
            // Create 5 planners
            for (int i = 0; i < 5; i++) {
                createTestPlanner(testUser);
            }

            mockMvc.perform(get("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/{id} - Get Single Planner")
    class GetPlannerTests {

        @Test
        @DisplayName("Should return planner when owned by user")
        void getPlanner_OwnedByUser_ReturnsPlanner() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.title").value(planner.getTitle()));
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void getPlanner_NotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/planner/md/{id}", nonExistentId)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for planner owned by another user")
        void getPlanner_OwnedByOtherUser_Returns404() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            mockMvc.perform(get("/api/planner/md/{id}", otherUserPlanner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for soft-deleted planner")
        void getPlanner_SoftDeleted_Returns404() throws Exception {
            Planner planner = createTestPlanner(testUser);
            planner.softDelete();
            plannerRepository.save(planner);

            mockMvc.perform(get("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void getPlanner_NoAuth_Returns403() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planner/md/{id}", planner.getId()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/planner/md/{id} - Update Planner")
    class UpdatePlannerTests {

        @Test
        @DisplayName("Should increment syncVersion on successful update")
        void updatePlanner_Success_IncrementsSyncVersion() throws Exception {
            Planner planner = createTestPlanner(testUser);
            Long initialSyncVersion = planner.getSyncVersion();

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setTitle("Updated Title");
            request.setSyncVersion(initialSyncVersion);

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.syncVersion").value(initialSyncVersion + 1))
                    .andExpect(jsonPath("$.title").value("Updated Title"));
        }

        @Test
        @DisplayName("Should return 409 on syncVersion mismatch")
        void updatePlanner_VersionMismatch_Returns409() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setTitle("Updated Title");
            request.setSyncVersion(999L); // Wrong version

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"))
                    .andExpect(jsonPath("$.serverVersion").value(planner.getSyncVersion()));
        }

        @Test
        @DisplayName("Should return 404 for planner owned by another user")
        void updatePlanner_OwnedByOtherUser_Returns404() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setTitle("Updated Title");
            request.setSyncVersion(1L);

            mockMvc.perform(put("/api/planner/md/{id}", otherUserPlanner.getId())
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 400 when content exceeds 50KB")
        void updatePlanner_ContentTooLarge_Returns400() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setSyncVersion(planner.getSyncVersion());
            String largeTitle = "x".repeat(52000);
            request.setContent(String.format(
                "{\"title\":\"%s\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{}}",
                largeTitle
            ));

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SIZE_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 400 when syncVersion is missing")
        void updatePlanner_MissingSyncVersion_Returns400() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setTitle("Updated Title");
            // syncVersion is null

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void updatePlanner_NoAuth_Returns403() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setTitle("Updated Title");
            request.setSyncVersion(1L);

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/planner/md/{id} - Delete Planner")
    class DeletePlannerTests {

        @Test
        @DisplayName("Should soft delete planner (set deleted_at)")
        void deletePlanner_Success_SoftDeletes() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(delete("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie()))
                    .andExpect(status().isNoContent());

            // Verify soft delete
            Planner deletedPlanner = plannerRepository.findById(planner.getId()).orElseThrow();
            assertNotNull(deletedPlanner.getDeletedAt());
            assertTrue(deletedPlanner.isDeleted());
        }

        @Test
        @DisplayName("Should return 404 for planner owned by another user")
        void deletePlanner_OwnedByOtherUser_Returns404() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            mockMvc.perform(delete("/api/planner/md/{id}", otherUserPlanner.getId())
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void deletePlanner_NotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(delete("/api/planner/md/{id}", nonExistentId)
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void deletePlanner_NoAuth_Returns403() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(delete("/api/planner/md/{id}", planner.getId())
                            .cookie(deviceIdCookie()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/planner/md/import - Import Planners")
    class ImportPlannersTests {

        @Test
        @DisplayName("Should return 201 when importing planners within limit")
        void importPlanners_WithinLimit_Returns201() throws Exception {
            List<CreatePlannerRequest> planners = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                CreatePlannerRequest req = createValidPlannerRequest();
                req.setTitle("Imported Planner " + i);
                planners.add(req);
            }

            ImportPlannersRequest request = new ImportPlannersRequest();
            request.setPlanners(planners);

            mockMvc.perform(post("/api/planner/md/import")
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.imported").value(3))
                    .andExpect(jsonPath("$.total").value(3))
                    .andExpect(jsonPath("$.planners", hasSize(3)));
        }

        @Test
        @DisplayName("Should return 409 when import would exceed 100 planner limit")
        void importPlanners_ExceedsLimit_Returns409() throws Exception {
            // Create 98 existing planners
            for (int i = 0; i < 98; i++) {
                createTestPlanner(testUser);
            }

            // Try to import 5 more (would exceed 100)
            List<CreatePlannerRequest> planners = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                CreatePlannerRequest req = createValidPlannerRequest();
                req.setTitle("Imported Planner " + i);
                planners.add(req);
            }

            ImportPlannersRequest request = new ImportPlannersRequest();
            request.setPlanners(planners);

            mockMvc.perform(post("/api/planner/md/import")
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 400 when importing more than 50 planners at once")
        void importPlanners_ExceedsBatchLimit_Returns400() throws Exception {
            List<CreatePlannerRequest> planners = new ArrayList<>();
            for (int i = 0; i < 51; i++) {
                CreatePlannerRequest req = createValidPlannerRequest();
                req.setTitle("Imported Planner " + i);
                planners.add(req);
            }

            ImportPlannersRequest request = new ImportPlannersRequest();
            request.setPlanners(planners);

            mockMvc.perform(post("/api/planner/md/import")
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void importPlanners_NoAuth_Returns403() throws Exception {
            List<CreatePlannerRequest> planners = new ArrayList<>();
            planners.add(createValidPlannerRequest());

            ImportPlannersRequest request = new ImportPlannersRequest();
            request.setPlanners(planners);

            mockMvc.perform(post("/api/planner/md/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should return 401 for expired/invalid token")
        void expiredToken_Returns401() throws Exception {
            // This test verifies behavior with invalid/expired token
            // Note: Invalid tokens return 401, while missing tokens return 403
            String invalidToken = "invalid.jwt.token";

            mockMvc.perform(get("/api/planner/md")
                            .cookie(new Cookie("accessToken", invalidToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("All planner endpoints require authentication")
        void allEndpoints_RequireAuth() throws Exception {
            UUID randomId = UUID.randomUUID();

            // POST /api/planner/md
            mockMvc.perform(post("/api/planner/md")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());

            // GET /api/planner/md
            mockMvc.perform(get("/api/planner/md"))
                    .andExpect(status().isForbidden());

            // GET /api/planner/md/{id}
            mockMvc.perform(get("/api/planner/md/{id}", randomId))
                    .andExpect(status().isForbidden());

            // PUT /api/planner/md/{id}
            mockMvc.perform(put("/api/planner/md/{id}", randomId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());

            // DELETE /api/planner/md/{id}
            mockMvc.perform(delete("/api/planner/md/{id}", randomId))
                    .andExpect(status().isForbidden());

            // POST /api/planner/md/import
            mockMvc.perform(post("/api/planner/md/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/config - Get Planner Config")
    class GetConfigTests {

        @Test
        @DisplayName("Should return 200 with config values (public endpoint)")
        void getConfig_Success() throws Exception {
            // Config endpoint returns version info for planner creation:
            // - schemaVersion: data format version (for migration support)
            // - mdCurrentVersion: current Mirror Dungeon version (for MIRROR_DUNGEON planners)
            // - rrAvailableVersions: available Refracted Railway versions (for REFRACTED_RAILWAY planners)
            mockMvc.perform(get("/api/planner/md/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.schemaVersion").isNumber())
                    .andExpect(jsonPath("$.schemaVersion").value(1))
                    .andExpect(jsonPath("$.mdCurrentVersion").isNumber())
                    .andExpect(jsonPath("$.rrAvailableVersions").isArray());
        }

        @Test
        @DisplayName("Should be accessible without authentication")
        void getConfig_NoAuth_Success() throws Exception {
            // Config endpoint is public - no auth cookie needed
            mockMvc.perform(get("/api/planner/md/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.schemaVersion").exists())
                    .andExpect(jsonPath("$.mdCurrentVersion").exists())
                    .andExpect(jsonPath("$.rrAvailableVersions").exists());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Creating 100th planner should succeed")
        void create100thPlanner_Succeeds() throws Exception {
            // Create 99 planners
            for (int i = 0; i < 99; i++) {
                createTestPlanner(testUser);
            }

            // 100th planner should succeed
            CreatePlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Verify count is now 100
            assertEquals(100, plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId()));
        }

        @Test
        @DisplayName("Creating 101st planner should fail")
        void create101stPlanner_Fails() throws Exception {
            // Create 100 planners
            for (int i = 0; i < 100; i++) {
                createTestPlanner(testUser);
            }

            // 101st planner should fail
            CreatePlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Deleted planners should not count toward limit")
        void deletedPlanners_NotCountedInLimit() throws Exception {
            // Create 100 planners
            List<Planner> planners = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                planners.add(createTestPlanner(testUser));
            }

            // Soft delete one
            Planner toDelete = planners.get(0);
            toDelete.softDelete();
            plannerRepository.save(toDelete);

            // Now should be able to create one more
            CreatePlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Content well under 50KB should succeed")
        void contentAt50KB_Succeeds() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            // Use VALID_CONTENT which is already well under 50KB
            request.setContent(VALID_CONTENT);

            mockMvc.perform(post("/api/planner/md")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/published - Get Published Planners")
    class GetPublishedPlannersTests {

        private Planner createPublishedPlanner(User user, String title) {
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .title(title)
                    .category("5F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(0)
                    .downvotes(0)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            return plannerRepository.save(planner);
        }

        @Test
        @DisplayName("Should return 200 with paginated published planners (public endpoint)")
        void getPublishedPlanners_Success() throws Exception {
            // Arrange - Create published planners
            createPublishedPlanner(testUser, "Published Planner 1");
            createPublishedPlanner(testUser, "Published Planner 2");
            createPublishedPlanner(otherUser, "Published Planner 3");

            // Act & Assert - No authentication required
            mockMvc.perform(get("/api/planner/md/published")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.totalElements").value(3));
        }

        @Test
        @DisplayName("Should filter by category when provided")
        void getPublishedPlanners_WithCategoryFilter() throws Exception {
            // Arrange - Create planners with different categories
            createPublishedPlanner(testUser, "F5 Planner");

            Planner f10Planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .title("F10 Planner")
                    .category("10F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(0)
                    .downvotes(0)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            plannerRepository.save(f10Planner);

            // Act & Assert - Filter by F10
            mockMvc.perform(get("/api/planner/md/published")
                            .param("page", "0")
                            .param("size", "10")
                            .param("category", "10F"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].title").value("F10 Planner"));
        }

        @Test
        @DisplayName("Should not return unpublished planners")
        void getPublishedPlanners_ExcludesUnpublished() throws Exception {
            // Arrange - Create one published, one unpublished
            createPublishedPlanner(testUser, "Published");
            createTestPlanner(testUser); // Unpublished by default

            // Act & Assert
            mockMvc.perform(get("/api/planner/md/published")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].title").value("Published"));
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/recommended - Get Recommended Planners")
    class GetRecommendedPlannersTests {

        private Planner createRecommendedPlanner(User user, String title, int upvotes, int downvotes) {
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .title(title)
                    .category("5F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(upvotes)
                    .downvotes(downvotes)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            return plannerRepository.save(planner);
        }

        @Test
        @DisplayName("Should return 200 with planners meeting threshold (public endpoint)")
        void getRecommendedPlanners_Success() throws Exception {
            // Arrange - threshold is 10, create planners with various net votes
            createRecommendedPlanner(testUser, "Recommended 1", 15, 2);  // net 13 >= 10
            createRecommendedPlanner(testUser, "Recommended 2", 12, 1);  // net 11 >= 10
            createRecommendedPlanner(otherUser, "Not Recommended", 5, 0); // net 5 < 10

            // Act & Assert - No authentication required
            mockMvc.perform(get("/api/planner/md/recommended")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("Should return empty when no planners meet threshold")
        void getRecommendedPlanners_NoneQualify() throws Exception {
            // Arrange - All planners below threshold
            createRecommendedPlanner(testUser, "Low Votes 1", 5, 0);  // net 5 < 10
            createRecommendedPlanner(testUser, "Low Votes 2", 8, 2);  // net 6 < 10

            // Act & Assert
            mockMvc.perform(get("/api/planner/md/recommended")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("PUT /api/planner/md/{id}/publish - Toggle Publish")
    class TogglePublishTests {

        @Test
        @DisplayName("Should return 200 when owner toggles publish status")
        void togglePublish_OwnerSuccess() throws Exception {
            // Arrange - Create planner for test user
            Planner planner = createTestPlanner(testUser);
            assertFalse(planner.getPublished());

            // Act & Assert - Toggle to published
            mockMvc.perform(put("/api/planner/md/{id}/publish", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.published").value(true));

            // Verify in database
            Planner updated = plannerRepository.findById(planner.getId()).orElseThrow();
            assertTrue(updated.getPublished());
        }

        @Test
        @DisplayName("Should return 403 when non-owner attempts to toggle publish")
        void togglePublish_NonOwnerForbidden() throws Exception {
            // Arrange - Create planner for test user, but use other user's token
            Planner planner = createTestPlanner(testUser);

            // Act & Assert - Other user attempts to toggle
            mockMvc.perform(put("/api/planner/md/{id}/publish", planner.getId())
                            .cookie(new Cookie("accessToken", otherUserAccessToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLANNER_FORBIDDEN"));
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void togglePublish_NoAuth_Returns403() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(put("/api/planner/md/{id}/publish", planner.getId()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void togglePublish_NotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(put("/api/planner/md/{id}/publish", nonExistentId)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should toggle from published to unpublished")
        void togglePublish_PublishedToUnpublished() throws Exception {
            // Arrange - Create already published planner
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .title("Published Planner")
                    .category("5F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(5)
                    .downvotes(1)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            planner = plannerRepository.save(planner);
            assertTrue(planner.getPublished());

            // Act & Assert - Toggle to unpublished
            mockMvc.perform(put("/api/planner/md/{id}/publish", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/planner/md/{id}/vote - Cast Vote")
    class CastVoteTests {

        private Planner createPublishedPlanner() {
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(otherUser)  // Created by other user so test user can vote
                    .title("Votable Planner")
                    .category("5F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(5)
                    .downvotes(2)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            return plannerRepository.save(planner);
        }

        @Test
        @DisplayName("Should return 200 when casting upvote")
        void castVote_Success() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();
            VoteRequest request = new VoteRequest();
            request.setVoteType(VoteType.UP);

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/vote", planner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.upvotes").value(6))
                    .andExpect(jsonPath("$.downvotes").value(2))
                    .andExpect(jsonPath("$.userVote").value("UP"));
        }

        @Test
        @DisplayName("Should return 200 when casting downvote")
        void castVote_Downvote_Success() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();
            VoteRequest request = new VoteRequest();
            request.setVoteType(VoteType.DOWN);

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/vote", planner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upvotes").value(5))
                    .andExpect(jsonPath("$.downvotes").value(3))
                    .andExpect(jsonPath("$.userVote").value("DOWN"));
        }

        @Test
        @DisplayName("Should return 200 when removing vote (null voteType)")
        void castVote_RemoveVote_Success() throws Exception {
            // Arrange - Pre-create a vote directly in DB
            Planner planner = createPublishedPlanner();
            // Create upvote with upvotes=6 (5 initial + 1 for this vote)
            planner.setUpvotes(6);
            plannerRepository.saveAndFlush(planner);

            var vote = new org.danteplanner.backend.entity.PlannerVote(
                    testUser.getId(), planner.getId(),
                    org.danteplanner.backend.entity.VoteType.UP);
            plannerVoteRepository.saveAndFlush(vote);
            entityManager.clear();

            // Remove vote
            VoteRequest removeRequest = new VoteRequest();
            removeRequest.setVoteType(null);

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/vote", planner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(removeRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upvotes").value(5))  // Back to original
                    .andExpect(jsonPath("$.downvotes").value(2))
                    .andExpect(jsonPath("$.userVote").isEmpty());
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void castVote_Unauthenticated() throws Exception {
            Planner planner = createPublishedPlanner();
            VoteRequest request = new VoteRequest();
            request.setVoteType(VoteType.UP);

            mockMvc.perform(post("/api/planner/md/{id}/vote", planner.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void castVote_PlannerNotFound() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            VoteRequest request = new VoteRequest();
            request.setVoteType(VoteType.UP);

            mockMvc.perform(post("/api/planner/md/{id}/vote", nonExistentId)
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 when voting on unpublished planner")
        void castVote_UnpublishedPlanner_Returns404() throws Exception {
            // Arrange - Create unpublished planner
            Planner planner = createTestPlanner(otherUser);
            assertFalse(planner.getPublished());

            VoteRequest request = new VoteRequest();
            request.setVoteType(VoteType.UP);

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/vote", planner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should change vote from UP to DOWN")
        void castVote_ChangeVote() throws Exception {
            // Arrange - Pre-create an UP vote directly in DB
            Planner planner = createPublishedPlanner();
            // Start with upvotes=6 (5 initial + 1 for this vote)
            planner.setUpvotes(6);
            plannerRepository.saveAndFlush(planner);

            var vote = new org.danteplanner.backend.entity.PlannerVote(
                    testUser.getId(), planner.getId(),
                    org.danteplanner.backend.entity.VoteType.UP);
            plannerVoteRepository.saveAndFlush(vote);
            entityManager.clear();

            // Change to DOWN vote
            VoteRequest downRequest = new VoteRequest();
            downRequest.setVoteType(VoteType.DOWN);

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/vote", planner.getId())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(downRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upvotes").value(5))   // -1 from UP removal
                    .andExpect(jsonPath("$.downvotes").value(3)) // +1 from DOWN addition
                    .andExpect(jsonPath("$.userVote").value("DOWN"));
        }
    }

    @Nested
    @DisplayName("POST /api/planner/md/{id}/bookmark - Toggle Bookmark")
    class ToggleBookmarkTests {

        private Planner createPublishedPlanner() {
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(otherUser)  // Created by other user so test user can bookmark
                    .title("Bookmarkable Planner")
                    .category("5F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(5)
                    .downvotes(2)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            return plannerRepository.save(planner);
        }

        @Test
        @DisplayName("Should return 200 when adding bookmark")
        void toggleBookmark_AddBookmark_Success() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.bookmarked").value(true));
        }

        @Test
        @DisplayName("Should toggle bookmark off when already bookmarked")
        void toggleBookmark_RemoveBookmark_Success() throws Exception {
            // Arrange - Create planner and add bookmark
            Planner planner = createPublishedPlanner();

            // First, add bookmark
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookmarked").value(true));

            // Act & Assert - Toggle again to remove
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.bookmarked").value(false));
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void toggleBookmark_NoAuth_Returns403() throws Exception {
            Planner planner = createPublishedPlanner();

            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void toggleBookmark_PlannerNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(post("/api/planner/md/{id}/bookmark", nonExistentId)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for unpublished planner")
        void toggleBookmark_UnpublishedPlanner_Returns404() throws Exception {
            // Arrange - Create unpublished planner
            Planner planner = createTestPlanner(otherUser);
            assertFalse(planner.getPublished());

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should allow bookmarking own published planner")
        void toggleBookmark_OwnPlanner_Success() throws Exception {
            // Arrange - Create published planner owned by test user
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .title("My Published Planner")
                    .category("5F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(0)
                    .downvotes(0)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            plannerRepository.save(planner);

            // Act & Assert - Can bookmark own planner
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookmarked").value(true));
        }
    }

    @Nested
    @DisplayName("POST /api/planner/md/{id}/fork - Fork Planner")
    class ForkPlannerTests {

        private Planner createPublishedPlanner() {
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(otherUser)  // Created by other user
                    .title("Forkable Planner")
                    .category("5F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(10)
                    .downvotes(2)
                    .viewCount(50)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            return plannerRepository.save(planner);
        }

        @Test
        @DisplayName("Should return 201 when forking planner")
        void forkPlanner_Success_Returns201() throws Exception {
            // Arrange
            Planner original = createPublishedPlanner();

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/fork", original.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.originalPlannerId").value(original.getId().toString()))
                    .andExpect(jsonPath("$.newPlannerId").exists())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("Should create draft copy with reset counters")
        void forkPlanner_CreatesDraftCopy() throws Exception {
            // Arrange
            Planner original = createPublishedPlanner();

            // Act
            MvcResult result = mockMvc.perform(post("/api/planner/md/{id}/fork", original.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Extract new planner ID
            String responseBody = result.getResponse().getContentAsString();
            String newPlannerIdStr = objectMapper.readTree(responseBody).get("newPlannerId").asText();
            UUID newPlannerId = UUID.fromString(newPlannerIdStr);

            // Verify forked planner properties
            Planner forked = plannerRepository.findById(newPlannerId).orElseThrow();
            assertEquals("Forkable Planner (Fork)", forked.getTitle());
            assertEquals("draft", forked.getStatus());
            assertFalse(forked.getPublished());
            assertEquals(0, forked.getUpvotes());
            assertEquals(0, forked.getDownvotes());
            assertEquals(0, forked.getViewCount());
            assertEquals(original.getCategory(), forked.getCategory());
            assertEquals(testUser.getId(), forked.getUser().getId()); // New owner
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void forkPlanner_NoAuth_Returns403() throws Exception {
            Planner planner = createPublishedPlanner();

            mockMvc.perform(post("/api/planner/md/{id}/fork", planner.getId()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void forkPlanner_PlannerNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(post("/api/planner/md/{id}/fork", nonExistentId)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for unpublished planner")
        void forkPlanner_UnpublishedPlanner_Returns404() throws Exception {
            // Arrange - Create unpublished planner
            Planner planner = createTestPlanner(otherUser);
            assertFalse(planner.getPublished());

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/fork", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 409 when user at max planner limit")
        void forkPlanner_AtLimit_Returns409() throws Exception {
            // Arrange - Create 100 planners for test user
            for (int i = 0; i < 100; i++) {
                createTestPlanner(testUser);
            }
            Planner original = createPublishedPlanner();

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/fork", original.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Should allow forking own published planner")
        void forkPlanner_OwnPlanner_Success() throws Exception {
            // Arrange - Create published planner owned by test user
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .title("My Published Planner")
                    .category("5F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(5)
                    .downvotes(1)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            plannerRepository.save(planner);

            // Act & Assert - Can fork own planner
            mockMvc.perform(post("/api/planner/md/{id}/fork", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("POST /api/planner/md/{id}/view - Record View")
    class RecordViewTests {

        private Planner createPublishedPlanner() {
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(otherUser)
                    .title("Viewable Planner")
                    .category("5F")
                    .status("published")
                    .content(VALID_CONTENT)
                    .published(true)
                    .upvotes(5)
                    .downvotes(2)
                    .viewCount(10)
                    .syncVersion(1L)
                    .schemaVersion(1)
                    .contentVersion(6)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .savedAt(Instant.now())
                    .build();
            return plannerRepository.save(planner);
        }

        @Test
        @DisplayName("Should return 204 when recording view for published planner (anonymous)")
        void recordView_Anonymous_Returns204() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();

            // Act & Assert - No authentication, should be public
            mockMvc.perform(post("/api/planner/md/{id}/view", planner.getId())
                            .header("X-Forwarded-For", "192.168.1.100")
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isNoContent());

            // Verify view count incremented
            Planner updated = plannerRepository.findById(planner.getId()).orElseThrow();
            assertEquals(11, updated.getViewCount());
        }

        @Test
        @DisplayName("Should return 204 when recording view for published planner (authenticated)")
        void recordView_Authenticated_Returns204() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();

            // Act & Assert - With authentication
            mockMvc.perform(post("/api/planner/md/{id}/view", planner.getId())
                            .cookie(accessTokenCookie())
                            .header("X-Forwarded-For", "192.168.1.100")
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isNoContent());

            // Verify view count incremented
            Planner updated = plannerRepository.findById(planner.getId()).orElseThrow();
            assertEquals(11, updated.getViewCount());
        }

        @Test
        @DisplayName("Should not increment view count for duplicate view same day")
        void recordView_Duplicate_NoIncrement() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();

            // First view
            mockMvc.perform(post("/api/planner/md/{id}/view", planner.getId())
                            .header("X-Forwarded-For", "192.168.1.100")
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isNoContent());

            // Second view with same IP/UA
            mockMvc.perform(post("/api/planner/md/{id}/view", planner.getId())
                            .header("X-Forwarded-For", "192.168.1.100")
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isNoContent());

            // Verify view count only incremented once
            Planner updated = plannerRepository.findById(planner.getId()).orElseThrow();
            assertEquals(11, updated.getViewCount()); // Only +1, not +2
        }

        @Test
        @DisplayName("Should increment view count for different IPs")
        void recordView_DifferentIPs_IncrementsTwice() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();

            // First view from IP 1
            mockMvc.perform(post("/api/planner/md/{id}/view", planner.getId())
                            .header("X-Forwarded-For", "192.168.1.100")
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isNoContent());

            // Second view from IP 2
            mockMvc.perform(post("/api/planner/md/{id}/view", planner.getId())
                            .header("X-Forwarded-For", "192.168.1.200")
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isNoContent());

            // Verify view count incremented twice
            Planner updated = plannerRepository.findById(planner.getId()).orElseThrow();
            assertEquals(12, updated.getViewCount()); // +2
        }

        @Test
        @DisplayName("Should return 404 for unpublished planner")
        void recordView_UnpublishedPlanner_Returns404() throws Exception {
            // Arrange - Create unpublished planner
            Planner planner = createTestPlanner(otherUser);
            assertFalse(planner.getPublished());

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/view", planner.getId())
                            .header("X-Forwarded-For", "192.168.1.100")
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void recordView_PlannerNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(post("/api/planner/md/{id}/view", nonExistentId)
                            .header("X-Forwarded-For", "192.168.1.100")
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should handle missing User-Agent gracefully")
        void recordView_NoUserAgent_Returns204() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();

            // Act & Assert - No User-Agent header
            mockMvc.perform(post("/api/planner/md/{id}/view", planner.getId())
                            .header("X-Forwarded-For", "192.168.1.100"))
                    .andExpect(status().isNoContent());

            // Verify view count incremented
            Planner updated = plannerRepository.findById(planner.getId()).orElseThrow();
            assertEquals(11, updated.getViewCount());
        }

        @Test
        @DisplayName("Should use getRemoteAddr when X-Forwarded-For is missing")
        void recordView_NoXForwardedFor_UsesRemoteAddr() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();

            // Act & Assert - No X-Forwarded-For header
            mockMvc.perform(post("/api/planner/md/{id}/view", planner.getId())
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isNoContent());

            // Verify view count incremented
            Planner updated = plannerRepository.findById(planner.getId()).orElseThrow();
            assertEquals(11, updated.getViewCount());
        }
    }
}
