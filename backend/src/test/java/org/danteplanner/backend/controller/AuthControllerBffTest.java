package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.auth.facade.AuthenticationFacade;
import org.danteplanner.backend.auth.facade.AuthenticationFacade.AuthResult;
import org.danteplanner.backend.auth.oauth.OAuthStateService;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BFF OAuth flow tests for {@link AuthController}: the server-side {@code /google/start} and
 * {@code /google/callback} endpoints. Covers the happy path and the login-fixation rejections
 * (INV2): a callback is honored only when its query {@code state} matches the {@code state}
 * sealed in a valid {@code oauth_tx} cookie; otherwise no auth cookies are set.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestConfig.class, AuthControllerBffTest.MockAuthFacadeConfig.class})
@Transactional
class AuthControllerBffTest {

    @TestConfiguration
    static class MockAuthFacadeConfig {
        @Bean
        @Primary
        public AuthenticationFacade authenticationFacade() {
            return Mockito.mock(AuthenticationFacade.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthStateService oAuthStateService;

    @Autowired
    private AuthenticationFacade authFacade;

    @BeforeEach
    void setUp() {
        Mockito.reset(authFacade);
    }

    @Test
    @DisplayName("GET /google/start redirects to Google and sets the oauth_tx cookie")
    void googleStart_whenInvoked_redirectsToGoogleAndSetsOauthTx() throws Exception {
        mockMvc.perform(get("/api/auth/google/start"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("accounts.google.com")))
                .andExpect(header().string("Location", containsString("code_challenge_method=S256")))
                .andExpect(header().string("Location", containsString("scope=openid")))
                .andExpect(cookie().exists(CookieConstants.OAUTH_TX));
    }

    @Test
    @DisplayName("GET /google/callback with matching state logs in and clears oauth_tx")
    void googleCallback_whenStateMatches_setsAuthCookiesAndClearsOauthTx() throws Exception {
        String state = "state-match";
        String verifier = "verifier-match";
        String returnTo = "http://localhost:5173/planner/1";
        String oauthTx = oAuthStateService.seal(state, verifier, returnTo);

        User user = Mockito.mock(User.class);
        when(authFacade.authenticateWithOAuth(eq("google"), eq("auth-code"), anyString(), eq(verifier)))
                .thenReturn(new AuthResult(user, "access-jwt", "refresh-jwt", false));

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", "auth-code")
                        .param("state", state)
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, oauthTx)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", returnTo))
                .andExpect(cookie().value(CookieConstants.ACCESS_TOKEN, "access-jwt"))
                .andExpect(cookie().value(CookieConstants.REFRESH_TOKEN, "refresh-jwt"))
                .andExpect(cookie().maxAge(CookieConstants.OAUTH_TX, 0));
    }

    @Test
    @DisplayName("GET /google/callback with mismatched state is rejected and sets no auth cookies (INV2)")
    void googleCallback_whenStateMismatch_rejectsWithoutAuthCookies() throws Exception {
        String oauthTx = oAuthStateService.seal("real-state", "verifier", "http://localhost");

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", "auth-code")
                        .param("state", "forged-state")
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, oauthTx)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN))
                .andExpect(cookie().doesNotExist(CookieConstants.REFRESH_TOKEN));

        verify(authFacade, never()).authenticateWithOAuth(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /google/callback with no oauth_tx cookie is rejected (INV2)")
    void googleCallback_whenNoOauthTx_rejects() throws Exception {
        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", "auth-code")
                        .param("state", "some-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN));

        verify(authFacade, never()).authenticateWithOAuth(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /google/callback with an error param redirects to error and clears oauth_tx")
    void googleCallback_whenProviderError_redirectsToErrorAndClearsOauthTx() throws Exception {
        mockMvc.perform(get("/api/auth/google/callback")
                        .param("error", "access_denied")
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, "sealed-value")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().maxAge(CookieConstants.OAUTH_TX, 0))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN));

        verify(authFacade, never()).authenticateWithOAuth(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /google/callback redirects to error (no auth cookies) when the exchange throws")
    void googleCallback_whenExchangeThrows_redirectsToErrorWithoutAuthCookies() throws Exception {
        String state = "state-throw";
        String oauthTx = oAuthStateService.seal(state, "verifier", "http://localhost:5173/planner/1");
        when(authFacade.authenticateWithOAuth(eq("google"), eq("auth-code"), anyString(), anyString()))
                .thenThrow(new RuntimeException("token endpoint unavailable"));

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", "auth-code")
                        .param("state", state)
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, oauthTx)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN))
                .andExpect(cookie().doesNotExist(CookieConstants.REFRESH_TOKEN));
    }
}
