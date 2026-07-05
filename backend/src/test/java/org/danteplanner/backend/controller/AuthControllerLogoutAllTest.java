package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;

/**
 * Integration tests for POST /api/auth/logout-all.
 *
 * <p>Exercises the endpoint end-to-end against the real {@link org.danteplanner.backend.auth.facade.AuthenticationFacade}
 * and {@link TokenBlacklistService} so user-wide invalidation and the subsequent filter
 * rejection compose without mocking.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class AuthControllerLogoutAllTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.danteplanner.backend.user.repository.UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    private User testUser;
    private String accessToken;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        tokenBlacklistService.clear();

        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);

        testUser = TestDataFactory.createTestUser(userRepository, "logoutall@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
        refreshToken = jwtTokenService.generateRefreshToken(testUser.getId());
    }

    private Cookie accessTokenCookie() {
        return new Cookie("accessToken", accessToken);
    }

    private Cookie refreshTokenCookie() {
        return new Cookie("refreshToken", refreshToken);
    }

    @Nested
    @DisplayName("POST /api/auth/logout-all - Authorization")
    class AuthorizationTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void logoutAll_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 when token is malformed")
        void logoutAll_MalformedToken_Returns401() throws Exception {
            Cookie malformed = new Cookie("accessToken", "malformed.token.here");

            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(malformed))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout-all - Success")
    class SuccessTests {

        @Test
        @DisplayName("Should return 204 and clear both cookies when authenticated")
        void logoutAll_Authenticated_Returns204AndClearsCookies() throws Exception {
            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(accessTokenCookie())
                            .cookie(refreshTokenCookie()))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().exists("accessToken"))
                    .andExpect(cookie().maxAge("accessToken", 0))
                    .andExpect(cookie().exists("refreshToken"))
                    .andExpect(cookie().maxAge("refreshToken", 0));
        }

        @Test
        @DisplayName("Should invalidate all of the user's tokens")
        void logoutAll_Authenticated_InvalidatesUserTokens() throws Exception {
            assertThat(tokenBlacklistService.userInvalidationSize()).isZero();

            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(accessTokenCookie())
                            .cookie(refreshTokenCookie()))
                    .andExpect(status().isNoContent());

            assertThat(tokenBlacklistService.userInvalidationSize()).isEqualTo(1);
            assertThat(tokenBlacklistService.isUserTokenInvalidated(testUser.getId(), 0L)).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout-all - Post-action token rejection")
    class PostActionTests {

        @Test
        @DisplayName("Pre-action access token is rejected on a subsequent request")
        void logoutAll_ThenPreActionAccessToken_Rejected() throws Exception {
            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(accessTokenCookie())
                            .cookie(refreshTokenCookie()))
                    .andExpect(status().isNoContent());

            assertThat(tokenBlacklistService.isBlacklisted(accessToken)).isTrue();

            // The current access token was blacklisted immediately (no grace): the filter
            // throws TokenRevokedException, clears the context, and the protected matcher
            // forces a 401 via the authentication entry point.
            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Pre-action refresh token is rejected after user invalidation")
        void logoutAll_ThenPreActionRefreshToken_Invalidated() throws Exception {
            TokenClaims refreshClaims = jwtTokenService.validateToken(refreshToken);

            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(accessTokenCookie())
                            .cookie(refreshTokenCookie()))
                    .andExpect(status().isNoContent());

            // Auto-refresh (filter) and lineage rotation both consult isUserTokenInvalidated,
            // which now rejects any token issued at or before the invalidation timestamp.
            assertThat(tokenBlacklistService.isUserTokenInvalidated(
                    testUser.getId(), refreshClaims.issuedAt().getTime() - 1)).isTrue();
        }
    }
}
