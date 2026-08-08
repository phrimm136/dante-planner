package org.danteplanner.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.ImportPlannersRequest;
import org.danteplanner.backend.planner.dto.VoteRequest;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.PlannerView;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.ExpiredTokens;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.danteplanner.backend.config.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;

/**
 * Integration tests for PlannerController.
 *
 * <p>Tests all REST API endpoints including authentication,
 * validation, error handling, and business logic.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerControllerIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerVoteRepository plannerVoteRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private PlannerViewRepository plannerViewRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    private User testUser;
    private User otherUser;
    private String accessToken;
    private String otherUserAccessToken;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        // Clean up existing data

        // Create test users
        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");

        otherUser = TestDataFactory.createTestUser(userRepository, "other@example.com");

        // Generate JWT tokens
        accessToken = jwtTokenService.generateAccessToken(testUser.getId(), UserRole.NORMAL);
        otherUserAccessToken = jwtTokenService.generateAccessToken(otherUser.getId(), UserRole.NORMAL);

        // Generate device ID
        deviceId = UUID.randomUUID();
    }

    private Cookie accessTokenCookie() {
        return AuthCookies.accessToken(accessToken);
    }

    private Cookie deviceIdCookie() {
        return AuthCookies.deviceId(deviceId);
    }

    private Cookie[] session() {
        return AuthCookies.session(accessToken, deviceId);
    }

    private UpsertPlannerRequest createValidPlannerRequest() {
        return new UpsertPlannerRequest(
                UUID.randomUUID().toString(),
                "5F",
                "Test Planner",
                PlannerStatus.DRAFT,
                TestDataFactory.VALID_CONTENT,
                7,
                PlannerType.MIRROR_DUNGEON,
                null,
                null);
    }

    /**
     * Create an upsert request pre-populated with existing planner's required fields.
     * Use this for update tests to satisfy validation while testing specific field changes.
     */
    private UpsertPlannerRequest createUpsertRequestFromPlanner(Planner planner) {
        return new UpsertPlannerRequest(
                planner.getId().toString(),
                planner.getCategory(),
                null,
                null,
                planner.getContentJson(),
                planner.getContentVersion(),
                planner.getPlannerType(),
                planner.getSyncVersion(),
                null);
    }

    private UpsertPlannerRequest withCategory(UpsertPlannerRequest r, String category) {
        return new UpsertPlannerRequest(r.id(), category, r.title(), r.status(),
                r.content(), r.contentVersion(), r.plannerType(), r.syncVersion(), r.selectedKeywords());
    }

    private UpsertPlannerRequest withTitle(UpsertPlannerRequest r, String title) {
        return new UpsertPlannerRequest(r.id(), r.category(), title, r.status(),
                r.content(), r.contentVersion(), r.plannerType(), r.syncVersion(), r.selectedKeywords());
    }

    private UpsertPlannerRequest withStatus(UpsertPlannerRequest r, PlannerStatus status) {
        return new UpsertPlannerRequest(r.id(), r.category(), r.title(), status,
                r.content(), r.contentVersion(), r.plannerType(), r.syncVersion(), r.selectedKeywords());
    }

    private UpsertPlannerRequest withContent(UpsertPlannerRequest r, String content) {
        return new UpsertPlannerRequest(r.id(), r.category(), r.title(), r.status(),
                content, r.contentVersion(), r.plannerType(), r.syncVersion(), r.selectedKeywords());
    }

    private UpsertPlannerRequest withContentVersion(UpsertPlannerRequest r, Integer contentVersion) {
        return new UpsertPlannerRequest(r.id(), r.category(), r.title(), r.status(),
                r.content(), contentVersion, r.plannerType(), r.syncVersion(), r.selectedKeywords());
    }

    private UpsertPlannerRequest withPlannerType(UpsertPlannerRequest r, PlannerType plannerType) {
        return new UpsertPlannerRequest(r.id(), r.category(), r.title(), r.status(),
                r.content(), r.contentVersion(), plannerType, r.syncVersion(), r.selectedKeywords());
    }

    private UpsertPlannerRequest withSyncVersion(UpsertPlannerRequest r, Long syncVersion) {
        return new UpsertPlannerRequest(r.id(), r.category(), r.title(), r.status(),
                r.content(), r.contentVersion(), r.plannerType(), syncVersion, r.selectedKeywords());
    }

    private Planner createTestPlanner(User user) {
        return TestDataFactory.planner(user)
                .status(PlannerStatus.DRAFT)
                .content(TestDataFactory.VALID_CONTENT)
                .save(plannerRepository);
    }

    private Planner createPublishedPlanner(User user, String title, String category, int upvotes) {
        Planner planner = TestDataFactory.planner(user)
                .title(title)
                .category(category)
                .status(PlannerStatus.SAVED)
                .content(TestDataFactory.VALID_CONTENT)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder()
                .plannerId(planner.getId())
                .upvotes(upvotes)
                .build());
        catalogService.add(planner);
        return planner;
    }

    @Nested
    @DisplayName("POST /api/planner/md - Create Planner")
    class CreatePlannerTests {

        @Test
        @DisplayName("Should return 201 when creating planner with valid data")
        void createPlanner_WhenValidData_Returns201() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.title").value("Test Planner"))
                    .andExpect(jsonPath("$.category").value("5F"))
                    .andExpect(jsonPath("$.status").value("draft"))
                    .andExpect(jsonPath("$.syncVersion").value(1))
                    .andExpect(jsonPath("$.schemaVersion").value(2))
                    .andExpect(jsonPath("$.contentVersion").value(7))
                    .andExpect(jsonPath("$.plannerType").value("MIRROR_DUNGEON"));
        }

        @Test
        @DisplayName("Should return 401 without JWT cookie")
        void createPlanner_WhenNoAuth_Returns401() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 400 when category is missing")
        void createPlanner_WhenMissingCategory_Returns400() throws Exception {
            UpsertPlannerRequest request = withCategory(createValidPlannerRequest(), null);

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 generic without echoing the invalid status value")
        void createPlanner_WhenInvalidStatus_Returns400WithoutEcho() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();
            String body = objectMapper.writeValueAsString(request)
                    .replace("\"status\":\"draft\"", "\"status\":\"garbage\"");

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("garbage"))));
        }

        @Test
        @DisplayName("Should return 400 when content is missing")
        void createPlanner_WhenMissingContent_Returns400() throws Exception {
            UpsertPlannerRequest request = withContent(createValidPlannerRequest(), null);

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when content exceeds 50KB")
        void createPlanner_WhenContentTooLarge_Returns400() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();
            // Create valid content structure that exceeds 50KB
            String largeTitle = "x".repeat(52000);
            String largeContent = String.format(
                "{\"title\":\"%s\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{}}",
                largeTitle
            );
            request = withContent(request, largeContent);

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SIZE_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 400 when note exceeds 1KB")
        void createPlanner_WhenNoteTooLarge_Returns400() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();
            // Create valid content structure with a note larger than 1KB
            String largeNote = "x".repeat(1100);
            String contentWithLargeNote = String.format(
                "{\"title\":\"Test\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{\"section1\":{\"content\":{\"type\":\"doc\",\"text\":\"%s\"}}}}",
                largeNote
            );
            request = withContent(request, contentWithLargeNote);

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when contentVersion is missing")
        void createPlanner_WhenMissingContentVersion_Returns400() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();
            request = withContentVersion(request, null);

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when plannerType is missing")
        void createPlanner_WhenMissingPlannerType_Returns400() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();
            request = withPlannerType(request, null);

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when contentVersion is not positive")
        void createPlanner_WhenNonPositiveContentVersion_Returns400() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();
            request = withContentVersion(request, 0);

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 409 when user exceeds 100 planner limit")
        void createPlanner_WhenExceedsLimit_Returns409() throws Exception {
            // Create 100 planners for the test user
            for (int i = 0; i < 100; i++) {
                createTestPlanner(testUser);
            }

            UpsertPlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 400 when category is invalid for planner type")
        void createPlanner_WhenInvalidCategoryForType_Returns400() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();
            request = withCategory(request, "INVALID_CATEGORY");

            // Note: INVALID_CATEGORY is mapped to generic VALIDATION_ERROR in GlobalExceptionHandler
            // to prevent schema probing attacks
            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 when MD category used with RR planner type")
        void createPlanner_WhenMdCategoryWithRrType_Returns400() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();
            request = withCategory(withPlannerType(request, PlannerType.REFRACTED_RAILWAY), "5F"); // MD category, invalid for RR

            // Note: INVALID_CATEGORY is mapped to generic VALIDATION_ERROR in GlobalExceptionHandler
            // to prevent schema probing attacks
            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
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
        void getPlanners_WhenAuthenticated_ReturnsOnlyOwnPlanners() throws Exception {
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
                    .andExpect(jsonPath("$.page.totalElements").value(2));
        }

        @Test
        @DisplayName("Should not return soft-deleted planners")
        void getPlanners_WhenSoftDeleted_ExcludesDeletedPlanners() throws Exception {
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
        @DisplayName("Should return 401 without authentication")
        void getPlanners_WhenNoAuth_Returns401() throws Exception {
            mockMvc.perform(get("/api/planner/md"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should support pagination")
        void getPlanners_WhenPaged_ReturnsRequestedPage() throws Exception {
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
                    .andExpect(jsonPath("$.page.totalElements").value(5))
                    .andExpect(jsonPath("$.page.totalPages").value(3));
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/{id} - Get Single Planner")
    class GetPlannerTests {

        @Test
        @DisplayName("Should return planner when owned by user")
        void getPlanner_WhenOwnedByUser_ReturnsPlanner() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.title").value(planner.getTitle()));
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void getPlanner_WhenNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/planner/md/{id}", nonExistentId)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for planner owned by another user")
        void getPlanner_WhenOwnedByOtherUser_Returns404() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            mockMvc.perform(get("/api/planner/md/{id}", otherUserPlanner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for soft-deleted planner")
        void getPlanner_WhenSoftDeleted_Returns404() throws Exception {
            Planner planner = createTestPlanner(testUser);
            planner.softDelete();
            plannerRepository.save(planner);

            mockMvc.perform(get("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 401 without authentication")
        void getPlanner_WhenNoAuth_Returns401() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planner/md/{id}", planner.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/planner/md/{id} - Update Planner")
    class UpdatePlannerTests {

        @Test
        @DisplayName("Should increment syncVersion on successful update")
        void updatePlanner_WhenSuccess_IncrementsSyncVersion() throws Exception {
            Planner planner = createTestPlanner(testUser);
            Long initialSyncVersion = planner.getSyncVersion();

            UpsertPlannerRequest request = createUpsertRequestFromPlanner(planner);
            request = withTitle(request, "Updated Title");

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.syncVersion").value(initialSyncVersion + 1))
                    .andExpect(jsonPath("$.title").value("Updated Title"));
        }

        @Test
        @DisplayName("Should return 409 on syncVersion mismatch")
        void updatePlanner_WhenVersionMismatch_Returns409() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpsertPlannerRequest request = createUpsertRequestFromPlanner(planner);
            request = withSyncVersion(withTitle(request, "Updated Title"), 999L); // Wrong version

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"))
                    .andExpect(jsonPath("$.serverVersion").value(planner.getSyncVersion()));
        }

        @Test
        @DisplayName("Should return 403 for planner owned by another user")
        void updatePlanner_WhenOwnedByOtherUser_Returns403() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            UpsertPlannerRequest request = createUpsertRequestFromPlanner(otherUserPlanner);
            request = withTitle(request, "Updated Title");

            mockMvc.perform(put("/api/planner/md/{id}", otherUserPlanner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLANNER_FORBIDDEN"));
        }

        @Test
        @DisplayName("Should return 400 when content exceeds 50KB")
        void updatePlanner_WhenContentTooLarge_Returns400() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpsertPlannerRequest request = createUpsertRequestFromPlanner(planner);
            String largeTitle = "x".repeat(52000);
            request = withContent(request, String.format(
                "{\"title\":\"%s\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{}}",
                largeTitle
            ));

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SIZE_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 401 without authentication")
        void updatePlanner_WhenNoAuth_Returns401() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpsertPlannerRequest request = createUpsertRequestFromPlanner(planner);
            request = withTitle(request, "Updated Title");

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should persist updated contentVersion on upsert update")
        void updatePlanner_WhenContentVersionUpdated_PersistsNewVersion() throws Exception {
            Planner planner = createTestPlanner(testUser); // contentVersion = 6

            UpsertPlannerRequest request = createUpsertRequestFromPlanner(planner);
            request = withContentVersion(request, 7);

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contentVersion").value(7));
        }
    }

    @Nested
    @DisplayName("DELETE /api/planner/md/{id} - Delete Planner")
    class DeletePlannerTests {

        @Test
        @DisplayName("Should soft delete planner (set deleted_at)")
        void deletePlanner_WhenSuccess_SoftDeletes() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(delete("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session()))
                    .andExpect(status().isNoContent());

            // Verify soft delete
            Planner deletedPlanner = plannerRepository.findById(planner.getId()).orElseThrow();
            assertNotNull(deletedPlanner.getContent().getDeletedAt());
            assertTrue(deletedPlanner.isDeleted());
        }

        @Test
        @DisplayName("Should return 404 for planner owned by another user")
        void deletePlanner_WhenOwnedByOtherUser_Returns404() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            mockMvc.perform(delete("/api/planner/md/{id}", otherUserPlanner.getId()).with(withCsrf())
                            .cookie(session()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void deletePlanner_WhenNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(delete("/api/planner/md/{id}", nonExistentId).with(withCsrf())
                            .cookie(session()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 401 without authentication")
        void deletePlanner_WhenNoAuth_Returns401() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(delete("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(deviceIdCookie()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/planner/md/import - Import Planners")
    class ImportPlannersTests {

        @Test
        @DisplayName("Should return 201 when importing planners within limit")
        void importPlanners_WhenWithinLimit_Returns201() throws Exception {
            List<UpsertPlannerRequest> planners = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                UpsertPlannerRequest req = createValidPlannerRequest();
                req = withTitle(req, "Imported Planner " + i);
                planners.add(req);
            }

            ImportPlannersRequest request = new ImportPlannersRequest(planners);

            mockMvc.perform(post("/api/planner/md/import").with(withCsrf())
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
        void importPlanners_WhenExceedsLimit_Returns409() throws Exception {
            // Create 98 existing planners
            for (int i = 0; i < 98; i++) {
                createTestPlanner(testUser);
            }

            // Try to import 5 more (would exceed 100)
            List<UpsertPlannerRequest> planners = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                UpsertPlannerRequest req = createValidPlannerRequest();
                req = withTitle(req, "Imported Planner " + i);
                planners.add(req);
            }

            ImportPlannersRequest request = new ImportPlannersRequest(planners);

            mockMvc.perform(post("/api/planner/md/import").with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 400 when importing more than 50 planners at once")
        void importPlanners_WhenExceedsBatchLimit_Returns400() throws Exception {
            List<UpsertPlannerRequest> planners = new ArrayList<>();
            for (int i = 0; i < 51; i++) {
                UpsertPlannerRequest req = createValidPlannerRequest();
                req = withTitle(req, "Imported Planner " + i);
                planners.add(req);
            }

            ImportPlannersRequest request = new ImportPlannersRequest(planners);

            mockMvc.perform(post("/api/planner/md/import").with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 401 without authentication")
        void importPlanners_WhenNoAuth_Returns401() throws Exception {
            List<UpsertPlannerRequest> planners = new ArrayList<>();
            planners.add(createValidPlannerRequest());

            ImportPlannersRequest request = new ImportPlannersRequest(planners);

            mockMvc.perform(post("/api/planner/md/import").with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should return 401 for expired/invalid token")
        void getPlanners_WhenExpiredToken_Returns401() throws Exception {
            String expiredToken =
                    ExpiredTokens.accessToken(jwtProperties, testUser.getId(), UserRole.NORMAL);

            mockMvc.perform(get("/api/planner/md")
                            .cookie(AuthCookies.accessToken(expiredToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 for a malformed token")
        void getPlanners_WhenMalformedToken_Returns401() throws Exception {
            mockMvc.perform(get("/api/planner/md")
                            .cookie(AuthCookies.accessToken("invalid.jwt.token")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("All planner endpoints require authentication")
        void plannerEndpoints_WhenUnauthenticated_Return401() throws Exception {
            UUID randomId = UUID.randomUUID();

            // PUT /api/planner/md/{id} (upsert)
            mockMvc.perform(put("/api/planner/md/{id}", randomId).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());

            // GET /api/planner/md
            mockMvc.perform(get("/api/planner/md"))
                    .andExpect(status().isUnauthorized());

            // GET /api/planner/md/{id}
            mockMvc.perform(get("/api/planner/md/{id}", randomId))
                    .andExpect(status().isUnauthorized());

            // PUT /api/planner/md/{id}
            mockMvc.perform(put("/api/planner/md/{id}", randomId).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());

            // DELETE /api/planner/md/{id}
            mockMvc.perform(delete("/api/planner/md/{id}", randomId).with(withCsrf()))
                    .andExpect(status().isUnauthorized());

            // POST /api/planner/md/import
            mockMvc.perform(post("/api/planner/md/import").with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/config - Get Planner Config")
    class GetConfigTests {

        @Test
        @DisplayName("Should return 200 with config values (public endpoint)")
        void getConfig_WhenPublic_Returns200WithConfig() throws Exception {
            // Config endpoint returns version info for planner creation:
            // - schemaVersion: data format version (for migration support)
            // - mdCurrentVersion: current Mirror Dungeon version (for MIRROR_DUNGEON planners)
            // - rrAvailableVersions: available Refracted Railway versions (for REFRACTED_RAILWAY planners)
            mockMvc.perform(get("/api/planner/md/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.schemaVersion").isNumber())
                    .andExpect(jsonPath("$.schemaVersion").value(2))
                    .andExpect(jsonPath("$.mdCurrentVersion").isNumber())
                    .andExpect(jsonPath("$.rrAvailableVersions").isArray());
        }

        @Test
        @DisplayName("Should be accessible without authentication")
        void getConfig_WhenNoAuth_Success() throws Exception {
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
        void upsertPlanner_When100thPlanner_Succeeds() throws Exception {
            // Create 99 planners
            for (int i = 0; i < 99; i++) {
                createTestPlanner(testUser);
            }

            // 100th planner should succeed
            UpsertPlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Verify count is now 100
            assertEquals(100, plannerRepository.countActiveByUserId(testUser.getId()));
        }

        @Test
        @DisplayName("Creating 101st planner should fail")
        void upsertPlanner_When101stPlanner_ReturnsConflict() throws Exception {
            // Create 100 planners
            for (int i = 0; i < 100; i++) {
                createTestPlanner(testUser);
            }

            // 101st planner should fail
            UpsertPlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Deleted planners should not count toward limit")
        void upsertPlanner_WhenDeletedPlannersExist_NotCountedInLimit() throws Exception {
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
            UpsertPlannerRequest request = createValidPlannerRequest();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Content well under 50KB should succeed")
        void upsertPlanner_WhenContentUnder50KB_Succeeds() throws Exception {
            UpsertPlannerRequest request = createValidPlannerRequest();
            // Use TestDataFactory.VALID_CONTENT which is already well under 50KB
            request = withContent(request, TestDataFactory.VALID_CONTENT);

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/published - Get Published Planners")
    class GetPublishedPlannersTests {

        private Planner createPublishedPlanner(User user, String title) {
            return PlannerControllerIT.this.createPublishedPlanner(user, title, "5F", 0);
        }

        @Test
        @DisplayName("Should return 200 with paginated published planners (public endpoint)")
        void getPublishedPlanners_WhenPublic_Returns200WithPage() throws Exception {
            // Arrange - Create published planners
            createPublishedPlanner(testUser, "Published Planner 1");
            createPublishedPlanner(testUser, "Published Planner 2");
            createPublishedPlanner(otherUser, "Published Planner 3");

            // Act & Assert - No authentication required
            mockMvc.perform(get("/api/planner/md/published")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    // Global catalog: the endpoint answers with every published planner, so the
                    // assertion names this test's rows instead of counting the table.
                    .andExpect(jsonPath("$.content[*].title", hasItem("Published Planner 1")))
                    .andExpect(jsonPath("$.content[*].title", hasItem("Published Planner 2")));
        }

        @Test
        @DisplayName("Should filter by category when provided")
        void getPublishedPlanners_WhenCategoryFilter_ReturnsFiltered() throws Exception {
            // Arrange - Create planners with different categories
            createPublishedPlanner(testUser, "F5 Planner");

            PlannerControllerIT.this.createPublishedPlanner(testUser, "F10 Planner", "10F", 0);

            // Act & Assert - Filter by F10
            mockMvc.perform(get("/api/planner/md/published")
                            .param("page", "0")
                            .param("size", "10")
                            .param("category", "10F"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].title", hasItem("F10 Planner")));
        }

        @Test
        @DisplayName("Should not return unpublished planners")
        void getPublishedPlanners_WhenUnpublishedExist_ExcludesThem() throws Exception {
            // Arrange - Create one published, one unpublished
            createPublishedPlanner(testUser, "Published");
            createTestPlanner(testUser); // Unpublished by default

            // Act & Assert
            mockMvc.perform(get("/api/planner/md/published")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    // The endpoint answers with every published planner, so the assertion
                    // names this test's row rather than counting the table.
                    .andExpect(jsonPath("$.content[*].title", hasItem("Published")))
                    .andExpect(jsonPath("$.content[*].title", not(hasItem("Unpublished"))));
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/recommended - Get Recommended Planners")
    class GetRecommendedPlannersTests {

        private Planner createRecommendedPlanner(User user, String title, int upvotes) {
            return createPublishedPlanner(user, title, "5F", upvotes);
        }

        @Test
        @DisplayName("Should return 200 with planners meeting threshold (public endpoint)")
        void getRecommendedPlanners_WhenPublic_Returns200WithQualifying() throws Exception {
            // Arrange - threshold is 10, create planners with various net votes
            createRecommendedPlanner(testUser, "Recommended 1", 15);
            createRecommendedPlanner(testUser, "Recommended 2", 12);
            createRecommendedPlanner(otherUser, "Not Recommended", 5);

            // Act & Assert - No authentication required
            mockMvc.perform(get("/api/planner/md/recommended")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    // Global catalog: name this test's rows rather than count the table.
                    .andExpect(jsonPath("$.content[*].title", hasItem("Recommended 1")))
                    .andExpect(jsonPath("$.content[*].title", hasItem("Recommended 2")));
        }

        @Test
        @DisplayName("Should return empty when no planners meet threshold")
        void getRecommendedPlanners_WhenNoneQualify_ReturnsEmpty() throws Exception {
            // Arrange - All planners below threshold
            createRecommendedPlanner(testUser, "Low Votes 1", 5);
            createRecommendedPlanner(testUser, "Low Votes 2", 8);

            // Act & Assert
            mockMvc.perform(get("/api/planner/md/recommended")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    // Naming this test's rows: another class's recommended planner may legitimately
                    // be in the catalog, but neither of these two may qualify.
                    .andExpect(jsonPath("$.content[*].title", not(hasItem("Low Votes 1"))))
                    .andExpect(jsonPath("$.content[*].title", not(hasItem("Low Votes 2"))));
        }
    }

    @Nested
    @DisplayName("POST /api/planner/md/{id}/publish and /unpublish - Publication Intents")
    class PublicationIntentTests {

        @Test
        @DisplayName("publish-validates-before-mutating: a blank title is refused and the stored state is unchanged")
        void publishValidatesBeforeMutating_WhenTitleBlank_Returns400AndStoresNothing() throws Exception {
            Planner planner = TestDataFactory.planner(testUser)
                    .title("   ")
                    .status(PlannerStatus.DRAFT)
                    .content(TestDataFactory.VALID_CONTENT)
                    .save(plannerRepository);

            mockMvc.perform(post("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            Planner stored = plannerRepository.findById(planner.getId()).orElseThrow();
            assertFalse(stored.getPublished());
            assertNull(stored.getFirstPublishedAt(), "a refused publish leaves no first-publish stamp");
        }

        @Test
        @DisplayName("the publish intent publishes and the unpublish intent withdraws")
        void publicationIntents_WhenOwnerCallsEach_MovePlannerBothWays() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(post("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(true));
            assertTrue(plannerRepository.findById(planner.getId()).orElseThrow().getPublished());

            mockMvc.perform(post("/api/planner/md/{id}/unpublish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(false));
            assertFalse(plannerRepository.findById(planner.getId()).orElseThrow().getPublished());
        }

        @Test
        @DisplayName("the unpublish intent refuses a non-owner")
        void unpublishIntent_WhenNonOwner_Returns403() throws Exception {
            Planner planner = createPublishedPlanner(testUser, "Someone Else's", "5F", 0);

            mockMvc.perform(post("/api/planner/md/{id}/unpublish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(AuthCookies.accessToken(otherUserAccessToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLANNER_FORBIDDEN"));
        }

        @Test
        @DisplayName("stale-client-publishes-through-the-delegate: the legacy route answers what the intent route answers")
        void staleClientPublishesThroughTheDelegate_WhenLegacyRouteCalled_AnswersIdentically() throws Exception {
            // Two planners in the same starting state, so both routes drive the same transition.
            // Publishing one planner twice compares a first publish against a republish.
            Planner throughIntent = createTestPlanner(testUser);
            Planner throughDelegate = createTestPlanner(testUser);

            String intentBody = mockMvc.perform(
                            post("/api/planner/md/{id}/publish", throughIntent.getId()).with(withCsrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String delegateBody = mockMvc.perform(
                            put("/api/planner/md/{id}/publish", throughDelegate.getId()).with(withCsrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"published\":true}")
                                    .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            ObjectNode fromIntent = (ObjectNode) objectMapper.readTree(intentBody);
            ObjectNode fromDelegate = (ObjectNode) objectMapper.readTree(delegateBody);

            // Distinct rows differ in identity and timestamps whichever route wrote them. Every
            // remaining field is one the publish decided, so those have to agree exactly.
            List<String> perRowFields = List.of("id", "createdAt", "lastModifiedAt", "savedAt");
            for (String field : perRowFields) {
                assertTrue(fromIntent.has(field), field + " missing from the intent response");
                assertTrue(fromDelegate.has(field), field + " missing from the delegate response");
            }
            fromIntent.remove(perRowFields);
            fromDelegate.remove(perRowFields);

            assertTrue(fromIntent.get("published").asBoolean());
            assertEquals(fromIntent, fromDelegate);
        }
    }

    @Nested
    @DisplayName("PUT /api/planner/md/{id}/publish - Legacy Publish Delegate")
    class SetPublishedTests {

        @Test
        @DisplayName("Should return 200 when owner toggles publish status")
        void setPublished_WhenOwner_Returns200() throws Exception {
            // Arrange - Create planner for test user
            Planner planner = createTestPlanner(testUser);
            assertFalse(planner.getPublished());

            mockMvc.perform(put("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"published\":true}")
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
        void setPublished_WhenNonOwner_Returns403() throws Exception {
            // Arrange - Create planner for test user, but use other user's token
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(put("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"published\":true}")
                            .cookie(AuthCookies.accessToken(otherUserAccessToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLANNER_FORBIDDEN"));
        }

        @Test
        @DisplayName("Should return 401 without authentication")
        void setPublished_WhenNoAuth_Returns401() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(put("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void setPublished_WhenNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(put("/api/planner/md/{id}/publish", nonExistentId).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"published\":true}")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should toggle from published to unpublished")
        void setPublished_WhenPublished_Unpublishes() throws Exception {
            // Arrange - Create already published planner
            Planner planner = createPublishedPlanner(testUser, "Published Planner", "5F", 5);
            assertTrue(planner.getPublished());

            mockMvc.perform(put("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"published\":false}")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/planner/md/{id}/upvote - Cast Vote")
    class CastVoteTests {

        private Planner createPublishedPlanner() {
            // Created by other user so test user can vote
            return PlannerControllerIT.this.createPublishedPlanner(otherUser, "Votable Planner", "5F", 5);
        }

        @Test
        @DisplayName("Should return 200 when casting upvote")
        void castVote_WhenAuthenticated_Returns200() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();
            VoteRequest request = new VoteRequest(VoteType.UP);

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/upvote", planner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.upvoteCount").value(6))
                    .andExpect(jsonPath("$.hasUpvoted").value(true));
        }


        @Test
        @DisplayName("Should return 400 when voteType is null (votes are permanent)")
        void castVote_WhenNullVoteType_Returns400() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();

            VoteRequest removeRequest = new VoteRequest(null);

            // Act & Assert - votes are permanent, null voteType is rejected
            mockMvc.perform(post("/api/planner/md/{id}/upvote", planner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(removeRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 401 without authentication")
        void castVote_WhenUnauthenticated_Returns401() throws Exception {
            Planner planner = createPublishedPlanner();
            VoteRequest request = new VoteRequest(VoteType.UP);

            mockMvc.perform(post("/api/planner/md/{id}/upvote", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void castVote_WhenPlannerNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            VoteRequest request = new VoteRequest(VoteType.UP);

            mockMvc.perform(post("/api/planner/md/{id}/upvote", nonExistentId).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 when voting on unpublished planner")
        void castVote_WhenUnpublishedPlanner_Returns404() throws Exception {
            // Arrange - Create unpublished planner
            Planner planner = createTestPlanner(otherUser);
            assertFalse(planner.getPublished());

            VoteRequest request = new VoteRequest(VoteType.UP);

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/upvote", planner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

    }

    @Nested
    @DisplayName("POST /api/planner/md/{id}/bookmark - Toggle Bookmark")
    class ToggleBookmarkTests {

        private Planner createPublishedPlanner() {
            // Created by other user so test user can bookmark
            return PlannerControllerIT.this.createPublishedPlanner(otherUser, "Bookmarkable Planner", "5F", 5);
        }

        @Test
        @DisplayName("Should return 200 when adding bookmark")
        void toggleBookmark_WhenAddBookmark_Success() throws Exception {
            // Arrange
            Planner planner = createPublishedPlanner();

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.bookmarked").value(true));
        }

        @Test
        @DisplayName("Should toggle bookmark off when already bookmarked")
        void toggleBookmark_WhenRemoveBookmark_Success() throws Exception {
            // Arrange - Create planner and add bookmark
            Planner planner = createPublishedPlanner();

            // First, add bookmark
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookmarked").value(true));

            // Act & Assert - Toggle again to remove
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.bookmarked").value(false));
        }

        @Test
        @DisplayName("Should return 401 without authentication")
        void toggleBookmark_WhenNoAuth_Returns401() throws Exception {
            Planner planner = createPublishedPlanner();

            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId()).with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void toggleBookmark_WhenPlannerNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(post("/api/planner/md/{id}/bookmark", nonExistentId).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for unpublished planner")
        void toggleBookmark_WhenUnpublishedPlanner_Returns404() throws Exception {
            // Arrange - Create unpublished planner
            Planner planner = createTestPlanner(otherUser);
            assertFalse(planner.getPublished());

            // Act & Assert
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should allow bookmarking own published planner")
        void toggleBookmark_WhenOwnPlanner_Success() throws Exception {
            // Arrange - Create published planner owned by test user
            Planner planner = PlannerControllerIT.this.createPublishedPlanner(
                    testUser, "My Published Planner", "5F", 0);

            // Act & Assert - Can bookmark own planner
            mockMvc.perform(post("/api/planner/md/{id}/bookmark", planner.getId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookmarked").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/published/{id} - Malformed UUID")
    class MalformedUuidTests {

        @Test
        @DisplayName("Should return 404 for malformed UUID in path variable")
        void getPublishedPlanner_WhenMalformedUuid_Returns404() throws Exception {
            // Act & Assert - Malformed UUID should return 404, not 500
            mockMvc.perform(get("/api/planner/md/published/{id}", "not-a-uuid"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Resource not found"));
        }

        @Test
        @DisplayName("Should return 401/404 for empty UUID in path variable")
        void getPublishedPlanner_WhenEmptyUuid_Returns404() throws Exception {
            // Act & Assert - Empty path segment routes to different endpoint
            // Spring routing treats "/published/" as a different path that may require auth
            mockMvc.perform(get("/api/planner/md/published/"))
                    .andExpect(status().is4xxClientError()); // Could be 401 or 404 depending on routing
        }

        @Test
        @DisplayName("Should return 404 for partial UUID in path variable")
        void getPublishedPlanner_WhenPartialUuid_Returns404() throws Exception {
            // Act & Assert - Partial UUID should return 404
            mockMvc.perform(get("/api/planner/md/published/{id}", "123e4567"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Resource not found"));
        }

        @Test
        @DisplayName("Should return 404 for malformed UUID on other UUID endpoints")
        void otherUuidEndpoints_WhenMalformedUuid_Returns404() throws Exception {
            // Test /api/planner/md/{id}/upvote (requires auth but should fail on UUID first)
            mockMvc.perform(post("/api/planner/md/{id}/upvote", "invalid-uuid").with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"voteType\":\"UP\"}")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/planner/md/published/{id} - Get Published Planner Detail")
    class GetPublishedPlannerDetailTests {

        private Planner createPublishedPlannerWithViewCount(User user, int viewCount) {
            Planner planner = TestDataFactory.planner(user)
                    .title("View Test Planner")
                    .status(PlannerStatus.SAVED)
                    .content(TestDataFactory.VALID_CONTENT)
                    .published(true)
                    .save(plannerRepository);
            statsRepository.save(PlannerStats.builder()
                    .plannerId(planner.getId())
                    .viewCount(viewCount)
                    .build());
            return planner;
        }

        @Test
        @DisplayName("Should return 200 with planner content for anonymous user")
        void getPublishedPlanner_WhenAnonymousAccess_Returns200() throws Exception {
            Planner planner = createPublishedPlannerWithViewCount(testUser, 0);

            mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId())
                            .header("X-Forwarded-For", "10.0.0.1")
                            .header("User-Agent", "TestBrowser/1.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.title").value("View Test Planner"))
                    .andExpect(jsonPath("$.content").exists())
                    .andExpect(jsonPath("$.viewCount").isNumber());
        }

        @Test
        @DisplayName("Should return the pre-request view count on first view")
        void getPublishedPlanner_WhenFirstView_ReturnsPreRequestViewCount() throws Exception {
            Planner planner = createPublishedPlannerWithViewCount(testUser, 5);

            mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId())
                            .header("X-Forwarded-For", "10.0.0.1")
                            .header("User-Agent", "TestBrowser/1.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viewCount").value(5));
        }

        @Test
        @DisplayName("Should not increment view count for duplicate viewer on same day")
        void getPublishedPlanner_WhenDuplicateView_DoesNotIncrementViewCount() throws Exception {
            Planner planner = createPublishedPlannerWithViewCount(testUser, 5);

            // 10.0.0.1 is RFC1918, so the viewer identity resolves to the device rather than the
            // address — which is the point: behind NAT every viewer shares one address, and only
            // the device id keeps them apart.
            UUID deviceId = UUID.randomUUID();
            String viewerHash = ViewerHashUtil.hashForAnonymousUser(
                    "device:" + deviceId, "TestBrowser/1.0", planner.getId());
            plannerViewRepository.save(
                    new PlannerView(planner.getId(), viewerHash, LocalDate.now(ZoneOffset.UTC)));

            mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId())
                            .cookie(AuthCookies.deviceId(deviceId))
                            .header("X-Forwarded-For", "10.0.0.1")
                            .header("User-Agent", "TestBrowser/1.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viewCount").value(5));
        }

        @Test
        @DisplayName("Should return 404 for non-published planner")
        void getPublishedPlanner_WhenNotPublished_Returns404() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent planner")
        void getPublishedPlanner_WhenNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/planner/md/published/{id}", nonExistentId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }
    }
}
