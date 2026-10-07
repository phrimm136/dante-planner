package org.danteplanner.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.VoteRequest;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.PlannerView;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.danteplanner.backend.config.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.support.UpsertRequests;

/**
 * Integration tests for the planner endpoints under /api/planner/md.
 *
 * <p>Tests all REST API endpoints including authentication,
 * validation, error handling, and business logic.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
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

    @Value("${planner.validation.max-content-size}")
    private int maxContentSizeBytes;

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

    private static final String UNKNOWN_GIFT_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "\"observationGiftIds\":[]", "\"observationGiftIds\":[\"9899\"]");

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
    class CreatePlannerTests {

        @Test
        void createPlanner_WhenValidData_Returns201() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .title("Test Planner")
                    .category("5F")
                    .status(PlannerStatus.DRAFT)
                    .contentVersion(7)
                    .build();

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
        void createPlanner_WhenNoAuth_Returns401() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.valid();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void createPlanner_WhenMissingCategory_Returns400() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft().category(null).build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void createPlanner_WhenInvalidStatus_Returns400WithoutEcho() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft().status(PlannerStatus.DRAFT).build();
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
        void createPlanner_WhenMissingContent_Returns400() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft().content(null).build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void createPlanner_WhenContentTooLarge_Returns400() throws Exception {
            // Create valid content structure that exceeds 50KB
            String largeTitle = "x".repeat(52000);
            String largeContent = String.format(
                "{\"title\":\"%s\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{}}",
                largeTitle
            );
            UpsertPlannerRequest request = UpsertRequests.draft().content(largeContent).build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SIZE_EXCEEDED"));
        }

        @Test
        void createPlanner_WhenNoteTooLarge_Returns400() throws Exception {
            // Create valid content structure with a note larger than 1KB
            String largeNote = "x".repeat(1100);
            String contentWithLargeNote = String.format(
                "{\"title\":\"Test\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{\"section1\":{\"content\":{\"type\":\"doc\",\"text\":\"%s\"}}}}",
                largeNote
            );
            UpsertPlannerRequest request = UpsertRequests.draft().content(contentWithLargeNote).build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void createPlanner_WhenMissingContentVersion_Returns400() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft().contentVersion(null).build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void createPlanner_WhenMissingPlannerType_Returns400() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft().plannerType(null).build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void createPlanner_WhenNonPositiveContentVersion_Returns400() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft().contentVersion(0).build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void createPlanner_WhenContentVersionIsThePreviousSeason_Returns400() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft().contentVersion(6).build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void createPlanner_WhenExceedsLimit_Returns409() throws Exception {
            // Create 100 planners for the test user
            for (int i = 0; i < 100; i++) {
                createTestPlanner(testUser);
            }

            UpsertPlannerRequest request = UpsertRequests.valid();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }

        @Test
        void createPlanner_WhenInvalidCategoryForType_Returns400() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft().category("INVALID_CATEGORY").build();

            // Note: INVALID_CATEGORY is mapped to generic VALIDATION_ERROR in ApiExceptionHandler
            // to prevent schema probing attacks
            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void createPlanner_WhenMdCategoryWithRrType_Returns400() throws Exception {
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .plannerType(PlannerType.REFRACTED_RAILWAY)
                    .category("5F") // MD category, invalid for RR
                    .build();

            // Note: INVALID_CATEGORY is mapped to generic VALIDATION_ERROR in ApiExceptionHandler
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
    class ListPlannersTests {

        @Test
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
        void getPlanners_WhenNoAuth_Returns401() throws Exception {
            mockMvc.perform(get("/api/planner/md"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
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
    class GetPlannerTests {

        @Test
        void getPlanner_WhenOwnedByUser_ReturnsPlanner() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planner/md/{id}", planner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(planner.getId().toString()))
                    .andExpect(jsonPath("$.title").value(planner.getTitle()));
        }

        @Test
        void getPlanner_WhenNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/planner/md/{id}", nonExistentId)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        void getPlanner_WhenOwnedByOtherUser_Returns404() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            mockMvc.perform(get("/api/planner/md/{id}", otherUserPlanner.getId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
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
        void getPlanner_WhenNoAuth_Returns401() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planner/md/{id}", planner.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class UpdatePlannerTests {

        @Test
        void updatePlanner_WhenSuccess_IncrementsSyncVersion() throws Exception {
            Planner planner = createTestPlanner(testUser);
            Long initialSyncVersion = planner.getSyncVersion();

            UpsertPlannerRequest request = UpsertRequests.resending(planner).title("Updated Title").build();

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.syncVersion").value(initialSyncVersion + 1))
                    .andExpect(jsonPath("$.title").value("Updated Title"));
        }

        @Test
        void updatePlanner_WhenOnlyCategoryChangesOverARetiredId_Returns200() throws Exception {
            Planner planner = TestDataFactory.planner(testUser)
                    .status(PlannerStatus.DRAFT)
                    .content(UNKNOWN_GIFT_CONTENT)
                    .save(plannerRepository);
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title(null).category("10F").build();

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.category").value("10F"));
        }

        @Test
        void updatePlanner_WhenCategoryAndContentChangeOverARetiredId_Returns400() throws Exception {
            Planner planner = createTestPlanner(testUser);
            UpsertPlannerRequest request = UpsertRequests.resending(planner)
                    .title(null)
                    .category("10F")
                    .content(UNKNOWN_GIFT_CONTENT)
                    .build();

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void updatePlanner_WhenVersionMismatch_Returns409() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpsertPlannerRequest request = UpsertRequests.resending(planner)
                    .title("Updated Title")
                    .syncVersion(999L) // Wrong version
                    .build();

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"))
                    .andExpect(jsonPath("$.serverVersion").value(planner.getSyncVersion()));
        }

        @Test
        void updatePlanner_WhenOwnedByOtherUser_Returns403() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            UpsertPlannerRequest request = UpsertRequests.resending(otherUserPlanner).title("Updated Title").build();

            mockMvc.perform(put("/api/planner/md/{id}", otherUserPlanner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLANNER_FORBIDDEN"));
        }

        @Test
        void updatePlanner_WhenContentTooLarge_Returns400() throws Exception {
            Planner planner = createTestPlanner(testUser);

            String largeTitle = "x".repeat(52000);
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title(null).content(String.format(
                "{\"title\":\"%s\",\"category\":\"5F\",\"selectedKeywords\":[],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{}}",
                largeTitle
            )).build();

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SIZE_EXCEEDED"));
        }

        @Test
        void updatePlanner_WhenNoAuth_Returns401() throws Exception {
            Planner planner = createTestPlanner(testUser);

            UpsertPlannerRequest request = UpsertRequests.resending(planner).title("Updated Title").build();

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(deviceIdCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void updatePlanner_WhenContentVersionUpdated_PersistsNewVersion() throws Exception {
            Planner planner = createTestPlanner(testUser); // contentVersion = 6

            UpsertPlannerRequest request = UpsertRequests.resending(planner).title(null).contentVersion(7).build();

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contentVersion").value(7));
        }

        @Test
        void updatePlanner_WhenPublishedCategoryGrowsPastItsFloors_Returns400AndKeepsTheCategory() throws Exception {
            Planner planner = createPublishedPlanner(testUser, "Five Floors", "5F", 0);
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title(null).category("15F").build();

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            assertThat(plannerRepository.findAggregate(planner.getId()).orElseThrow().getCategory()).isEqualTo("5F");
        }

        @Test
        void updatePlanner_WhenAPublishedPlannersStoredFloorIsNotAnObjectAndTheCategoryGrows_Returns400() throws Exception {
            Planner planner = TestDataFactory.planner(testUser)
                    .status(PlannerStatus.SAVED)
                    .content(TestDataFactory.VALID_CONTENT.replace(
                            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]}",
                            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]},"
                                    + "{\"themePackId\":\"1006\",\"difficulty\":1,\"giftIds\":[]},5"))
                    .published(true)
                    .save(plannerRepository);
            statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
            catalogService.add(planner);
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title(null).category("15F").build();

            mockMvc.perform(put("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            assertThat(plannerRepository.findAggregate(planner.getId()).orElseThrow().getCategory()).isEqualTo("5F");
        }
    }

    @Nested
    class DeletePlannerTests {

        @Test
        void deletePlanner_WhenSuccess_SoftDeletes() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(delete("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(session()))
                    .andExpect(status().isNoContent());

            // Verify soft delete
            Planner deletedPlanner = plannerRepository.findById(planner.getId()).orElseThrow();
            assertThat(deletedPlanner.getContent().getDeletedAt()).isNotNull();
            assertThat(deletedPlanner.isDeleted()).isTrue();
        }

        @Test
        void deletePlanner_WhenOwnedByOtherUser_Returns404() throws Exception {
            Planner otherUserPlanner = createTestPlanner(otherUser);

            mockMvc.perform(delete("/api/planner/md/{id}", otherUserPlanner.getId()).with(withCsrf())
                            .cookie(session()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        void deletePlanner_WhenNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(delete("/api/planner/md/{id}", nonExistentId).with(withCsrf())
                            .cookie(session()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        void deletePlanner_WhenNoAuth_Returns401() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(delete("/api/planner/md/{id}", planner.getId()).with(withCsrf())
                            .cookie(deviceIdCookie()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class AuthenticationTests {

        @Test
        void getPlanners_WhenExpiredToken_Returns401() throws Exception {
            String expiredToken =
                    ExpiredTokens.accessToken(jwtProperties, testUser.getId(), UserRole.NORMAL);

            mockMvc.perform(get("/api/planner/md")
                            .cookie(AuthCookies.accessToken(expiredToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void getPlanners_WhenMalformedToken_Returns401() throws Exception {
            mockMvc.perform(get("/api/planner/md")
                            .cookie(AuthCookies.accessToken("invalid.jwt.token")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
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
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void upsertPlanner_When100thPlanner_Succeeds() throws Exception {
            // Create 99 planners
            for (int i = 0; i < 99; i++) {
                createTestPlanner(testUser);
            }

            // 100th planner should succeed
            UpsertPlannerRequest request = UpsertRequests.valid();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Verify count is now 100
            assertThat(plannerRepository.countActiveByUserId(testUser.getId())).isEqualTo(100);
        }

        @Test
        void upsertPlanner_When101stPlanner_ReturnsConflict() throws Exception {
            // Create 100 planners
            for (int i = 0; i < 100; i++) {
                createTestPlanner(testUser);
            }

            // 101st planner should fail
            UpsertPlannerRequest request = UpsertRequests.valid();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLANNER_LIMIT_EXCEEDED"));
        }

        @Test
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
            UpsertPlannerRequest request = UpsertRequests.valid();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        void upsertPlanner_WhenContentUnder50KB_Succeeds() throws Exception {
            // Use TestDataFactory.VALID_CONTENT which is already well under 50KB
            UpsertPlannerRequest request = UpsertRequests.draft().content(TestDataFactory.VALID_CONTENT).build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    class GetPublishedPlannersTests {

        private Planner createPublishedPlanner(User user, String title) {
            return PlannerControllerIT.this.createPublishedPlanner(user, title, "5F", 0);
        }

        @Test
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
    class GetRecommendedPlannersTests {

        private Planner createRecommendedPlanner(User user, String title, int upvotes) {
            return createPublishedPlanner(user, title, "5F", upvotes);
        }

        @Test
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
    class PublicationIntentTests {

        @Test
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
            assertThat(stored.isPublished()).isFalse();
            assertThat(stored.getFirstPublishedAt()).as("a refused publish leaves no first-publish stamp").isNull();
        }

        @Test
        void publicationIntents_WhenOwnerCallsEach_MovePlannerBothWays() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(post("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(true));
            assertThat(plannerRepository.findById(planner.getId()).orElseThrow().isPublished()).isTrue();

            mockMvc.perform(post("/api/planner/md/{id}/unpublish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(false));
            assertThat(plannerRepository.findById(planner.getId()).orElseThrow().isPublished()).isFalse();
        }

        @Test
        void publishIntent_WhenStoredRenderingExceedsLimit_PublishesWhatSaveAccepted() throws Exception {
            ObjectNode document = (ObjectNode) objectMapper.readTree(TestDataFactory.VALID_CONTENT);
            ObjectNode notes = document.putObject("sectionNotes");
            for (int i = 0; i < 45; i++) {
                notes.set("n" + i, objectMapper.createObjectNode().set("content", objectMapper.valueToTree(new int[500])));
            }
            String content = objectMapper.writeValueAsString(document);
            UpsertPlannerRequest request = UpsertRequests.draft().content(content).build();
            UUID plannerId = UUID.fromString(request.id());

            mockMvc.perform(put("/api/planner/md/{id}", plannerId).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            String stored = plannerRepository.findById(plannerId).orElseThrow().getContentJson();
            assertThat(content.getBytes(StandardCharsets.UTF_8).length).isLessThan(maxContentSizeBytes);
            assertThat(stored.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(maxContentSizeBytes);

            mockMvc.perform(post("/api/planner/md/{id}/publish", plannerId).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(true));
        }

        @Test
        void publishIntent_WhenAFloorHasNoThemePack_Returns400AfterTheDraftSaved() throws Exception {
            ObjectNode document = (ObjectNode) objectMapper.readTree(TestDataFactory.VALID_CONTENT);
            document.set("floorSelections",
                    objectMapper.readTree("[{\"themePackId\":null,\"difficulty\":0,\"giftIds\":[]}]"));
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .content(objectMapper.writeValueAsString(document))
                    .build();
            UUID plannerId = UUID.fromString(request.id());

            mockMvc.perform(put("/api/planner/md/{id}", plannerId).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/planner/md/{id}/publish", plannerId).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            assertThat(plannerRepository.findById(plannerId).orElseThrow().isPublished()).isFalse();
        }

        @Test
        void publishIntent_WhenFewerFloorsThanTheCategoryNeeds_Returns400AfterTheDraftSaved() throws Exception {
            ObjectNode document = (ObjectNode) objectMapper.readTree(TestDataFactory.VALID_CONTENT);
            document.set("floorSelections", objectMapper.readTree(
                    "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},"
                            + "{\"themePackId\":\"1002\",\"difficulty\":0,\"giftIds\":[]},"
                            + "{\"themePackId\":\"1003\",\"difficulty\":0,\"giftIds\":[]}]"));
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .content(objectMapper.writeValueAsString(document))
                    .build();
            UUID plannerId = UUID.fromString(request.id());

            mockMvc.perform(put("/api/planner/md/{id}", plannerId).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/planner/md/{id}/publish", plannerId).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            assertThat(plannerRepository.findById(plannerId).orElseThrow().isPublished()).isFalse();
        }

        @Test
        void saveDraft_WhenAFloorRepeatsAnEarlierThemePack_Returns400() throws Exception {
            ObjectNode document = (ObjectNode) objectMapper.readTree(TestDataFactory.VALID_CONTENT);
            document.set("floorSelections", objectMapper.readTree(
                    "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},"
                            + "{\"themePackId\":\"1002\",\"difficulty\":0,\"giftIds\":[]},"
                            + "{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},"
                            + "{\"themePackId\":\"1003\",\"difficulty\":0,\"giftIds\":[]},"
                            + "{\"themePackId\":\"1004\",\"difficulty\":0,\"giftIds\":[]}]"));
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .content(objectMapper.writeValueAsString(document))
                    .build();

            mockMvc.perform(put("/api/planner/md/{id}", request.id()).with(withCsrf())
                            .cookie(session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        void unpublishIntent_WhenNonOwner_Returns403() throws Exception {
            Planner planner = createPublishedPlanner(testUser, "Someone Else's", "5F", 0);

            mockMvc.perform(post("/api/planner/md/{id}/unpublish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(AuthCookies.accessToken(otherUserAccessToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLANNER_FORBIDDEN"));
        }

        @Test
        void publishIntent_WhenNonOwner_Returns403() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(post("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(AuthCookies.accessToken(otherUserAccessToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLANNER_FORBIDDEN"));
            assertThat(plannerRepository.findById(planner.getId()).orElseThrow().isPublished()).isFalse();
        }

        @Test
        void publishIntent_WhenNoAuth_Returns401() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(post("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void publishIntent_WhenNotFound_Returns404() throws Exception {
            mockMvc.perform(post("/api/planner/md/{id}/publish", UUID.randomUUID()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        void publishPath_WhenCalledWithPut_Returns405() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(put("/api/planner/md/{id}/publish", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"published\":true}")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isMethodNotAllowed());
            assertThat(plannerRepository.findById(planner.getId()).orElseThrow().isPublished()).isFalse();
        }
    }

    @Nested
    class CastVoteTests {

        private Planner createPublishedPlanner() {
            // Created by other user so test user can vote
            return PlannerControllerIT.this.createPublishedPlanner(otherUser, "Votable Planner", "5F", 5);
        }

        @Test
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
        void castVote_WhenUnauthenticated_Returns401() throws Exception {
            Planner planner = createPublishedPlanner();
            VoteRequest request = new VoteRequest(VoteType.UP);

            mockMvc.perform(post("/api/planner/md/{id}/upvote", planner.getId()).with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
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
        void castVote_WhenUnpublishedPlanner_Returns404() throws Exception {
            // Arrange - Create unpublished planner
            Planner planner = createTestPlanner(otherUser);
            assertThat(planner.isPublished()).isFalse();

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
    class MalformedUuidTests {

        @Test
        void getPublishedPlanner_WhenMalformedUuid_Returns404() throws Exception {
            // Act & Assert - Malformed UUID should return 404, not 500
            mockMvc.perform(get("/api/planner/md/published/{id}", "not-a-uuid"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("Resource not found"));
        }

        @Test
        void getPublishedPlanner_WhenEmptyUuid_Returns401() throws Exception {
            // The trailing slash matches neither permitAll pattern, so anyRequest().authenticated() answers before routing
            mockMvc.perform(get("/api/planner/md/published/"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void getPublishedPlanner_WhenPartialUuid_Returns404() throws Exception {
            // Act & Assert - Partial UUID should return 404
            mockMvc.perform(get("/api/planner/md/published/{id}", "123e4567"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("Resource not found"));
        }

        @Test
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
        void getPublishedPlanner_WhenFirstView_ReturnsPreRequestViewCount() throws Exception {
            Planner planner = createPublishedPlannerWithViewCount(testUser, 5);

            mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId())
                            .header("X-Forwarded-For", "10.0.0.1")
                            .header("User-Agent", "TestBrowser/1.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viewCount").value(5));
        }

        @Test
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
        void getPublishedPlanner_WhenNotPublished_Returns404() throws Exception {
            Planner planner = createTestPlanner(testUser);

            mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }

        @Test
        void getPublishedPlanner_WhenNotFound_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/planner/md/published/{id}", nonExistentId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
        }
    }
}
