package org.danteplanner.backend.security;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.repository.PlannerRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for security boundaries that controller tests cannot reach.
 *
 * <p>Tests CORS preflight handling, RBAC enforcement at HTTP layer, and rate limiting.
 * These tests verify the security infrastructure works correctly end-to-end.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestConfig.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User regularUser;
    private User adminUser;
    private String regularUserToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);

        regularUser = TestDataFactory.createTestUser(userRepository, "user@example.com");
        adminUser = TestDataFactory.createAdmin(userRepository, "admin@example.com");

        regularUserToken = TestDataFactory.generateAccessToken(jwtTokenService, regularUser);
        adminToken = TestDataFactory.generateAccessToken(jwtTokenService, adminUser);
    }

    private Cookie regularUserCookie() {
        return new Cookie("accessToken", regularUserToken);
    }

    private Cookie adminCookie() {
        return new Cookie("accessToken", adminToken);
    }

    @Nested
    @DisplayName("CORS Preflight Tests")
    class CorsTests {

        @Test
        @DisplayName("Should return 200 for OPTIONS with allowed origin")
        void preflight_AllowedOrigin_Returns200() throws Exception {
            mockMvc.perform(options("/api/planner/md")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return Access-Control-Allow-Origin header for allowed origin")
        void preflight_AllowedOrigin_ReturnsAllowOriginHeader() throws Exception {
            mockMvc.perform(options("/api/planner/md")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        }

        @Test
        @DisplayName("Should return Access-Control-Allow-Methods header")
        void preflight_AllowedOrigin_ReturnsAllowMethodsHeader() throws Exception {
            mockMvc.perform(options("/api/planner/md")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")));
        }

        @Test
        @DisplayName("Should return Access-Control-Allow-Headers header with configured values")
        void preflight_AllowedOrigin_ReturnsAllowHeadersHeader() throws Exception {
            mockMvc.perform(options("/api/planner/md")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "GET")
                            .header("Access-Control-Request-Headers", "Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Headers", containsString("Content-Type")));
        }

        @Test
        @DisplayName("Should return Access-Control-Max-Age header with 3600")
        void preflight_AllowedOrigin_ReturnsMaxAgeHeader() throws Exception {
            mockMvc.perform(options("/api/planner/md")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Max-Age", "3600"));
        }

        @Test
        @DisplayName("Should return 403 for OPTIONS with denied origin")
        void preflight_DeniedOrigin_Returns403() throws Exception {
            mockMvc.perform(options("/api/planner/md")
                            .header("Origin", "http://evil.com")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow actual GET request after successful preflight")
        void actualRequest_AfterPreflight_Works() throws Exception {
            mockMvc.perform(options("/api/planner/md/config")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/planner/md/config")
                            .header("Origin", "http://localhost:5173"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Access-Control-Allow-Origin"));
        }
    }

    @Nested
    @DisplayName("RBAC Enforcement Tests")
    class RbacTests {

        @Test
        @DisplayName("Should return 403 when regular user accesses admin endpoint")
        void adminEndpoint_RegularUser_Returns403() throws Exception {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, regularUser, true);

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", planner.getId())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Test reason for hiding planner\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 200 when admin user accesses admin endpoint")
        void adminEndpoint_AdminUser_Returns200() throws Exception {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, regularUser, true);

            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", planner.getId())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"Test reason for hiding planner\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 200 for public endpoint without authentication")
        void publicEndpoint_NoAuth_Returns200() throws Exception {
            mockMvc.perform(get("/api/planner/md/config"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 401 for protected endpoint without authentication")
        void protectedEndpoint_NoAuth_Returns401() throws Exception {
            mockMvc.perform(get("/api/planner/md"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should verify SecurityFilterChain precedence over controller annotations")
        void securityFilterChain_PrecedesControllerAnnotations() throws Exception {
            mockMvc.perform(get("/api/planner/md")
                            .cookie(regularUserCookie()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Rate Limiting Tests")
    class RateLimitTests {

        @Test
        @DisplayName("Should allow requests within rate limit for different users")
        void rateLimit_PerUserIsolation_DifferentBuckets() throws Exception {
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(get("/api/planner/md")
                                .cookie(regularUserCookie()))
                        .andExpect(status().isOk());
            }

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(get("/api/planner/md")
                                .cookie(adminCookie()))
                        .andExpect(status().isOk());
            }

            mockMvc.perform(get("/api/planner/md")
                            .cookie(regularUserCookie()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should maintain separate rate limit buckets per user")
        void rateLimit_SeparateBucketsPerUser_NoInterference() throws Exception {
            for (int i = 0; i < 10; i++) {
                mockMvc.perform(get("/api/planner/md")
                                .cookie(regularUserCookie()))
                        .andExpect(status().isOk());
            }

            mockMvc.perform(get("/api/planner/md")
                            .cookie(adminCookie()))
                    .andExpect(status().isOk());
        }
    }
}
