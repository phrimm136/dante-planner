package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
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
 * Tier-2 wire-contract tests for {@link AdminController}.
 *
 * <p>Covers the two role-management endpoints ({@code changeRole}, {@code getUserRole}) that
 * {@code AdminModerationControllerTest} does not exercise (that class targets the separate
 * {@code AdminModerationController} at {@code /api/admin/planner}). Pins the
 * {@code UserRoleResponse} JSON shape plus the ADMIN authorization boundary
 * (non-admin NORMAL &rarr; 403, unauthenticated &rarr; 401).</p>
 *
 * <p>Unlike moderation responses, {@code UserRoleResponse} deliberately exposes {@code userId}
 * (admin-only role administration) — this test pins it as present.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User regularUser;
    private User adminUser;
    private String regularUserToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);

        regularUser = TestDataFactory.createTestUser(userRepository, "user@example.com");
        adminUser = TestDataFactory.createAdmin(userRepository, "admin@example.com");

        regularUserToken = TestDataFactory.generateAccessToken(jwtTokenService, regularUser);
        adminToken = TestDataFactory.generateAccessToken(jwtTokenService, adminUser);
    }

    private Cookie adminCookie() {
        return new Cookie("accessToken", adminToken);
    }

    private Cookie regularUserCookie() {
        return new Cookie("accessToken", regularUserToken);
    }

    @Nested
    @DisplayName("PUT /api/admin/user/{targetId}/role")
    class ChangeRoleTests {

        @Test
        void changeRole_WhenAdmin_Returns200WithRoleShape() throws Exception {
            mockMvc.perform(put("/api/admin/user/{targetId}/role", regularUser.getId()).with(withCsrf())
                            .cookie(adminCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"role\":\"MODERATOR\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(regularUser.getId()))
                    .andExpect(jsonPath("$.role").value("MODERATOR"))
                    .andExpect(jsonPath("$.email").value(regularUser.getEmail()));
        }

        @Test
        void changeRole_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(put("/api/admin/user/{targetId}/role", regularUser.getId()).with(withCsrf())
                            .cookie(regularUserCookie())
                            .contentType(APPLICATION_JSON)
                            .content("{\"role\":\"MODERATOR\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void changeRole_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(put("/api/admin/user/{targetId}/role", regularUser.getId()).with(withCsrf())
                            .contentType(APPLICATION_JSON)
                            .content("{\"role\":\"MODERATOR\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/user/{targetId}/role")
    class GetUserRoleTests {

        @Test
        void getUserRole_WhenAdmin_Returns200WithRoleShape() throws Exception {
            mockMvc.perform(get("/api/admin/user/{targetId}/role", regularUser.getId())
                            .cookie(adminCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(regularUser.getId()))
                    .andExpect(jsonPath("$.role").value(UserRole.NORMAL.name()));
        }

        @Test
        void getUserRole_WhenNormalUser_Returns403() throws Exception {
            mockMvc.perform(get("/api/admin/user/{targetId}/role", regularUser.getId())
                            .cookie(regularUserCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void getUserRole_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/admin/user/{targetId}/role", regularUser.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
