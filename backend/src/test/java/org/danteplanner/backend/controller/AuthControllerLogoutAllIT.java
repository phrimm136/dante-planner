package org.danteplanner.backend.controller;

import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.RevocationOutcome;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.RefreshClaims;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import org.danteplanner.backend.user.repository.UserRepository;

/**
 * Integration tests for POST /api/auth/logout-all.
 *
 * <p>Exercises the endpoint end-to-end against the real {@link org.danteplanner.backend.auth.service.AuthenticationService}
 * and {@link TokenBlacklistService} so user-wide invalidation and the subsequent filter
 * rejection compose without mocking.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
@Tag("containerized")
class AuthControllerLogoutAllIT extends SharedMySqlContainerSupport {

    static {
    }


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    private User testUser;
    private User bystander;
    private String accessToken;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUser(userRepository, "logoutall@example.com");
        bystander = TestDataFactory.createTestUser(userRepository, "logoutall-bystander@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
        refreshToken = jwtTokenService.generateRefreshToken(testUser.getId());
    }

    private Cookie accessTokenCookie() {
        return AuthCookies.accessToken(accessToken);
    }

    private Cookie refreshTokenCookie() {
        return AuthCookies.refreshToken(refreshToken);
    }

    @Nested
    class AuthorizationTests {

        @Test
        void logoutAll_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void logoutAll_WhenTheAuthenticationCarriesNoPrincipal_FailsWithoutClearingCookies() throws Exception {
            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .with(authentication(new UsernamePasswordAuthenticationToken(null, null, List.of()))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(cookie().doesNotExist("accessToken"))
                    .andExpect(cookie().doesNotExist("refreshToken"));
        }

        @Test
        void logoutAll_WhenThePrincipalIsNotAUserId_FailsWithoutClearingCookies() throws Exception {
            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .with(authentication(new UsernamePasswordAuthenticationToken("someone", null, List.of()))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(cookie().doesNotExist("accessToken"))
                    .andExpect(cookie().doesNotExist("refreshToken"));
        }

        @Test
        void logoutAll_WhenMalformedToken_Returns401() throws Exception {
            Cookie malformed = AuthCookies.accessToken("malformed.token.here");

            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(malformed))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class SuccessTests {

        @Test
        void logout_WhenOnlyTheAccessCookieIsPresent_RevokesItAndLeavesTheRefreshToken() throws Exception {
            mockMvc.perform(post("/api/auth/logout").with(withCsrf()).cookie(accessTokenCookie()))
                    .andExpect(status().isNoContent());

            assertThat(tokenBlacklistService.blacklistCheck(accessToken)).isEqualTo(RevocationOutcome.REVOKED);
            assertThat(tokenBlacklistService.blacklistCheck(refreshToken)).isEqualTo(RevocationOutcome.CLEAR);
        }

        @Test
        void logoutAll_WhenAuthenticated_Returns204AndClearsCookies() throws Exception {
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
        void logoutAll_WhenAuthenticated_InvalidatesUserTokens() throws Exception {
            assertThat(tokenBlacklistService.invalidationCheck(testUser.getId(), 0L)).isEqualTo(RevocationOutcome.CLEAR);

            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(accessTokenCookie())
                            .cookie(refreshTokenCookie()))
                    .andExpect(status().isNoContent());

            assertThat(tokenBlacklistService.invalidationCheck(testUser.getId(), 0L)).isEqualTo(RevocationOutcome.REVOKED);
            assertThat(tokenBlacklistService.invalidationCheck(bystander.getId(), 0L))
                    .as("invalidation is scoped to the acting user")
                    .isEqualTo(RevocationOutcome.CLEAR);
        }
    }

    @Nested
    class PostActionTests {

        @Test
        void preActionAccessToken_WhenLoggedOutEverywhere_IsRejected() throws Exception {
            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(accessTokenCookie())
                            .cookie(refreshTokenCookie()))
                    .andExpect(status().isNoContent());

            assertThat(tokenBlacklistService.blacklistCheck(accessToken)).isEqualTo(RevocationOutcome.REVOKED);

            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void preActionRefreshToken_WhenLoggedOutEverywhere_IsInvalidated() throws Exception {
            RefreshClaims refreshClaims = jwtTokenService.validateRefreshToken(refreshToken);

            mockMvc.perform(post("/api/auth/logout-all").with(withCsrf())
                            .cookie(accessTokenCookie())
                            .cookie(refreshTokenCookie()))
                    .andExpect(status().isNoContent());

            // Auto-refresh (filter) and lineage rotation both consult invalidationCheck,
            // which now rejects any token issued at or before the invalidation timestamp.
            assertThat(tokenBlacklistService.invalidationCheck(
                    testUser.getId(), refreshClaims.issuedAt().getTime() - 1)).isEqualTo(RevocationOutcome.REVOKED);
        }
    }
}
