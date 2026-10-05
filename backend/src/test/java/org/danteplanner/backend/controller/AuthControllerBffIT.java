package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.auth.oauth.OAuthStateService;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.support.GoogleStub;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class AuthControllerBffIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthStateService oAuthStateService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void googleStart_WhenInvoked_RedirectsToGoogleAndSetsOauthTx() throws Exception {
        mockMvc.perform(get("/api/auth/google/start"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("accounts.google.com")))
                .andExpect(header().string("Location", containsString("code_challenge_method=S256")))
                .andExpect(header().string("Location", containsString("scope=openid")))
                .andExpect(cookie().exists(CookieConstants.OAUTH_TX));
    }

    @Test
    void googleCallback_WhenStateMatches_SetsAuthCookiesAndClearsOauthTx() throws Exception {
        String state = "state-match";
        String verifier = "verifier-match";
        String returnTo = "http://localhost:5173/planner/1";
        String oauthTx = oAuthStateService.seal(state, verifier, returnTo);
        String code = uniqueCode();
        String subject = "google-sub-" + UUID.randomUUID();
        String email = "bff-login-" + UUID.randomUUID() + "@example.com";
        GoogleStub.script(code).user(subject, email);

        MvcResult result = mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, oauthTx)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", returnTo))
                .andExpect(cookie().exists(CookieConstants.ACCESS_TOKEN))
                .andExpect(cookie().exists(CookieConstants.REFRESH_TOKEN))
                .andExpect(cookie().maxAge(CookieConstants.OAUTH_TX, 0))
                .andReturn();

        User user = userRepository.findByProviderAndProviderId(AuthProviderType.GOOGLE, subject).orElseThrow();
        assertThat(user.getEmail()).isEqualTo(email);
        TokenClaims access = jwtTokenService.validateAccessToken(
                result.getResponse().getCookie(CookieConstants.ACCESS_TOKEN).getValue());
        TokenClaims refresh = jwtTokenService.validateRefreshToken(
                result.getResponse().getCookie(CookieConstants.REFRESH_TOKEN).getValue());
        assertThat(access.userId()).isEqualTo(user.getId());
        assertThat(refresh.userId()).isEqualTo(user.getId());
    }

    // INV2
    @Test
    void googleCallback_WhenStateMismatch_RejectsWithoutAuthCookies() throws Exception {
        String oauthTx = oAuthStateService.seal("real-state", "verifier", "http://localhost");
        String code = scriptedCode();

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", code)
                        .param("state", "forged-state")
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, oauthTx)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN))
                .andExpect(cookie().doesNotExist(CookieConstants.REFRESH_TOKEN));

        assertThat(GoogleStub.tokenCalls(code)).isZero();
    }

    // INV2
    @Test
    void googleCallback_WhenNoOauthTx_Rejects() throws Exception {
        String code = scriptedCode();

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", code)
                        .param("state", "some-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN));

        assertThat(GoogleStub.tokenCalls(code)).isZero();
    }

    @Test
    void googleCallback_WhenProviderError_RedirectsToErrorAndClearsOauthTx() throws Exception {
        String code = scriptedCode();

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", code)
                        .param("error", "access_denied")
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, "sealed-value")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().maxAge(CookieConstants.OAUTH_TX, 0))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN));

        assertThat(GoogleStub.tokenCalls(code)).isZero();
    }

    @Test
    void googleCallback_WhenExchangeThrows_RedirectsToErrorWithoutAuthCookies() throws Exception {
        String state = "state-throw";
        String oauthTx = oAuthStateService.seal(state, "verifier", "http://localhost:5173/planner/1");
        String code = uniqueCode();
        GoogleStub.script(code).tokenEndpointFails();

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, oauthTx)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN))
                .andExpect(cookie().doesNotExist(CookieConstants.REFRESH_TOKEN));

        assertThat(GoogleStub.tokenCalls(code)).isEqualTo(1);
    }

    private static String uniqueCode() {
        return "auth-code-" + UUID.randomUUID();
    }

    private static String scriptedCode() {
        String code = uniqueCode();
        GoogleStub.script(code).user("google-sub-" + UUID.randomUUID(), "bff-unused@example.com");
        return code;
    }
}
