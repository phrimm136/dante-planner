package org.danteplanner.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.dto.planner.CreatePlannerRequest;
import org.danteplanner.backend.dto.planner.ImportPlannersRequest;
import org.danteplanner.backend.dto.planner.UpdatePlannerRequest;
import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.JwtService;
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
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

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
                .build();
        testUser = userRepository.save(testUser);

        otherUser = User.builder()
                .email("other@example.com")
                .provider("google")
                .providerId("google-456")
                .build();
        otherUser = userRepository.save(otherUser);

        // Generate JWT tokens
        accessToken = jwtService.generateAccessToken(testUser.getId(), testUser.getEmail());
        otherUserAccessToken = jwtService.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        // Generate device ID
        deviceId = UUID.randomUUID();
    }

    private Cookie accessTokenCookie() {
        return new Cookie("accessToken", accessToken);
    }

    private Cookie deviceIdCookie() {
        return new Cookie("deviceId", deviceId.toString());
    }

    private CreatePlannerRequest createValidPlannerRequest() {
        CreatePlannerRequest request = new CreatePlannerRequest();
        request.setCategory(MDCategory.F5);
        request.setTitle("Test Planner");
        request.setStatus("draft");
        request.setContent("{\"data\": \"test\"}");
        return request;
    }

    private Planner createTestPlanner(User user) {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Test Planner")
                .category(MDCategory.F5)
                .status("draft")
                .content("{\"data\": \"test\"}")
                .syncVersion(1L)
                .version(1)
                .savedAt(Instant.now())
                .build();
        return plannerRepository.save(planner);
    }

    @Nested
    @DisplayName("POST /api/planners - Create Planner")
    class CreatePlannerTests {

        @Test
        @DisplayName("Should return 201 when creating planner with valid data")
        void createPlanner_ValidData_Returns201() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(post("/api/planners")
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
                    .andExpect(jsonPath("$.userId").value(testUser.getId()));
        }

        @Test
        @DisplayName("Should return 403 without JWT cookie")
        void createPlanner_NoAuth_Returns403() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(post("/api/planners")
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

            mockMvc.perform(post("/api/planners")
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

            mockMvc.perform(post("/api/planners")
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
            // Create content larger than 50KB (51KB)
            String largeContent = "{\"data\": \"" + "x".repeat(52000) + "\"}";
            request.setContent(largeContent);

            mockMvc.perform(post("/api/planners")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONTENT_TOO_LARGE"));
        }

        @Test
        @DisplayName("Should return 400 when note exceeds 1KB")
        void createPlanner_NoteTooLarge_Returns400() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            // Create content with a note larger than 1KB
            String largeNote = "x".repeat(1100);
            String contentWithLargeNote = "{\"sectionNotes\": {\"section1\": {\"content\": \"" + largeNote + "\"}}}";
            request.setContent(contentWithLargeNote);

            mockMvc.perform(post("/api/planners")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NOTE_TOO_LARGE"));
        }

        @Test
        @DisplayName("Should return 409 when user exceeds 100 planner limit")
        void createPlanner_ExceedsLimit_Returns409() throws Exception {
            // Create 100 planners for the test user
            for (int i = 0; i < 100; i++) {
                createTestPlanner(testUser);
            }

            CreatePlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(post("/api/planners")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }
    }

    @Nested
    @DisplayName("GET /api/planners - List Planners")
    class ListPlannersTests {

        @Test
        @DisplayName("Should return only current user's non-deleted planners")
        void getPlanners_ReturnsOnlyUsersPlanners() throws Exception {
            // Create planners for test user
            createTestPlanner(testUser);
            createTestPlanner(testUser);

            // Create planner for other user (should not be returned)
            createTestPlanner(otherUser);

            mockMvc.perform(get("/api/planners")
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

            mockMvc.perform(get("/api/planners")
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
            mockMvc.perform(get("/api/planners"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should support pagination")
        void getPlanners_SupportsPagination() throws Exception {
            // Create 5 planners
            for (int i = 0; i < 5; i++) {
                createTestPlanner(testUser);
            }

            mockMvc.perform(get("/api/planners")
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
    @DisplayName("GET /api/planners/{id} - Get Single Planner")
    class GetPlannerTests {

        @Test
        @DisplayName("Should return planner when owned by user")
        void getPlanner_OwnedByUser_ReturnsPlanner() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planners/{id}", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.title").value(planner.getTitle()));
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void getPlanner_NotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/planners/{id}", nonExistentId)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for planner owned by another user")
        void getPlanner_OwnedByOtherUser_Returns404() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            mockMvc.perform(get("/api/planners/{id}", otherUserPlanner.getId())
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

            mockMvc.perform(get("/api/planners/{id}", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void getPlanner_NoAuth_Returns403() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planners/{id}", planner.getId()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/planners/{id} - Update Planner")
    class UpdatePlannerTests {

        @Test
        @DisplayName("Should increment syncVersion on successful update")
        void updatePlanner_Success_IncrementsSyncVersion() throws Exception {
            Planner planner = createTestPlanner(testUser);
            Long initialSyncVersion = planner.getSyncVersion();

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setTitle("Updated Title");
            request.setSyncVersion(initialSyncVersion);

            mockMvc.perform(put("/api/planners/{id}", planner.getId())
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

            mockMvc.perform(put("/api/planners/{id}", planner.getId())
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

            mockMvc.perform(put("/api/planners/{id}", otherUserPlanner.getId())
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
            request.setContent("{\"data\": \"" + "x".repeat(52000) + "\"}");

            mockMvc.perform(put("/api/planners/{id}", planner.getId())
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONTENT_TOO_LARGE"));
        }

        @Test
        @DisplayName("Should return 400 when syncVersion is missing")
        void updatePlanner_MissingSyncVersion_Returns400() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setTitle("Updated Title");
            // syncVersion is null

            mockMvc.perform(put("/api/planners/{id}", planner.getId())
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

            mockMvc.perform(put("/api/planners/{id}", planner.getId())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/planners/{id} - Delete Planner")
    class DeletePlannerTests {

        @Test
        @DisplayName("Should soft delete planner (set deleted_at)")
        void deletePlanner_Success_SoftDeletes() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(delete("/api/planners/{id}", planner.getId())
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

            mockMvc.perform(delete("/api/planners/{id}", otherUserPlanner.getId())
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void deletePlanner_NotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(delete("/api/planners/{id}", nonExistentId)
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 403 without authentication")
        void deletePlanner_NoAuth_Returns403() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(delete("/api/planners/{id}", planner.getId())
                            .cookie(deviceIdCookie()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/planners/import - Import Planners")
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

            mockMvc.perform(post("/api/planners/import")
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

            mockMvc.perform(post("/api/planners/import")
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

            mockMvc.perform(post("/api/planners/import")
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

            mockMvc.perform(post("/api/planners/import")
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

            mockMvc.perform(get("/api/planners")
                            .cookie(new Cookie("accessToken", invalidToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("All planner endpoints require authentication")
        void allEndpoints_RequireAuth() throws Exception {
            UUID randomId = UUID.randomUUID();

            // POST /api/planners
            mockMvc.perform(post("/api/planners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());

            // GET /api/planners
            mockMvc.perform(get("/api/planners"))
                    .andExpect(status().isForbidden());

            // GET /api/planners/{id}
            mockMvc.perform(get("/api/planners/{id}", randomId))
                    .andExpect(status().isForbidden());

            // PUT /api/planners/{id}
            mockMvc.perform(put("/api/planners/{id}", randomId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());

            // DELETE /api/planners/{id}
            mockMvc.perform(delete("/api/planners/{id}", randomId))
                    .andExpect(status().isForbidden());

            // POST /api/planners/import
            mockMvc.perform(post("/api/planners/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
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

            mockMvc.perform(post("/api/planners")
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

            mockMvc.perform(post("/api/planners")
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

            mockMvc.perform(post("/api/planners")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Content at exactly 50KB should succeed")
        void contentAt50KB_Succeeds() throws Exception {
            CreatePlannerRequest request = createValidPlannerRequest();
            // Create content at exactly 50KB minus overhead for JSON structure
            // 50 * 1024 = 51200 bytes, minus ~20 bytes for {"data": ""}
            String content = "{\"data\": \"" + "x".repeat(51180) + "\"}";
            request.setContent(content);

            // This should be right at the limit
            int contentSize = content.getBytes("UTF-8").length;
            assertTrue(contentSize <= 50 * 1024);

            mockMvc.perform(post("/api/planners")
                            .cookie(accessTokenCookie())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }
}
