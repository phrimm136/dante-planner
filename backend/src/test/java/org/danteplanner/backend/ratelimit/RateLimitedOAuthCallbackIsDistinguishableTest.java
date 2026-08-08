package org.danteplanner.backend.ratelimit;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.danteplanner.backend.auth.controller.AuthController;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthStateService;
import org.danteplanner.backend.auth.service.AuthenticationService;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.config.LoginRedirect;
import org.danteplanner.backend.shared.config.OAuthProperties;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitExceededException;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.util.CookieUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario: Rate-limited OAuth callback is distinguishable.
 *
 * <p>The callback is a top-level browser navigation, so a 429 body is not renderable there and the
 * outcome has to travel as a URL. It previously travelled as the generic login-error URL, which
 * made a rate-limited user indistinguishable from one who declined consent — the two need
 * different remedies, so they need different codes.</p>
 */
@Isolated
class RateLimitedOAuthCallbackIsDistinguishableTest {

    private static final String FRONTEND_URL = "https://planner.example";

    private RateLimitService rateLimitService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        FrontendProperties frontendProperties = new FrontendProperties(FRONTEND_URL);

        AuthController authController = new AuthController(
                mock(AuthenticationService.class),
                mock(TokenValidator.class),
                mock(OAuthProperties.class),
                new CookieUtils(false, "", "Lax"),
                mock(JwtProperties.class),
                mock(OAuthStateService.class),
                mock(OAuthProviderRegistry.class),
                frontendProperties);

        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimitService,
                mock(SecurityProperties.class),
                new DeviceIdResolver(new CookieUtils(false, "", "Lax")),
                frontendProperties,
                new ObjectMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    @DisplayName("An exhausted AUTH bucket redirects the callback under its own rate-limit code")
    void exhaustedAuthBucket_WhenCallbackRequested_RedirectsWithTheRateLimitCode() throws Exception {
        doThrow(new RateLimitExceededException(null, "auth"))
                .when(rateLimitService).check(eq(RateLimitPolicy.AUTH), anyString());

        mockMvc.perform(get("/api/auth/google/callback").param("code", "any").param("state", "any"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.RATE_LIMITED));
    }

    @Test
    @DisplayName("Declined consent keeps the generic login-error code, distinct from the rate-limit one")
    void declinedConsent_WhenCallbackRequested_RedirectsWithTheLoginErrorCode() throws Exception {
        mockMvc.perform(get("/api/auth/google/callback").param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.ERROR));
    }

    @Test
    @DisplayName("The two rejection codes are different URLs")
    void rejectionCodes_WhenCompared_AreDistinct() {
        assertThat(LoginRedirect.RATE_LIMITED).isNotEqualTo(LoginRedirect.ERROR);
    }
}
