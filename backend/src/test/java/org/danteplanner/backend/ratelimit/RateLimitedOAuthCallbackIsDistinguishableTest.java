package org.danteplanner.backend.ratelimit;

import java.util.concurrent.TimeUnit;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.danteplanner.backend.auth.controller.AuthController;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.LoginRedirect;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.InMemoryProxyManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.exception.ProblemWriter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.support.TestJsonMappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class RateLimitedOAuthCallbackIsDistinguishableTest {

    private static final String FRONTEND_URL = "https://planner.example";

    private final InMemoryProxyManager rateLimitStore = new InMemoryProxyManager();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FrontendProperties frontendProperties = new FrontendProperties(FRONTEND_URL);
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.parseTrustedProxyIps();

        AuthController authController = new AuthController(
                null,
                null,
                null,
                null,
                new CookieUtils(false, "", "Lax"),
                null,
                null,
                null,
                frontendProperties);

        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new RateLimitService(rateLimitStore, authBucket()),
                securityProperties,
                new DeviceIdResolver(new CookieUtils(false, "", "Lax")),
                frontendProperties,
                new ProblemWriter(TestJsonMappers.springDefaults()),
                new SimpleMeterRegistry());

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    void exhaustedAuthBucket_WhenCallbackRequested_RedirectsWithTheRateLimitCode() throws Exception {
        rateLimitStore.denyNext(TimeUnit.SECONDS.toNanos(1));

        mockMvc.perform(get("/api/auth/google/callback").param("code", "any").param("state", "any"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.RATE_LIMITED));

        assertThat(rateLimitStore.charges()).singleElement()
                .satisfies(charge -> assertThat(charge.key()).endsWith(":auth"));
    }

    @Test
    void exhaustedAuthBucket_WhenAppleCallbackPosted_RedirectsWithTheRateLimitCode() throws Exception {
        rateLimitStore.denyNext(TimeUnit.SECONDS.toNanos(1));

        mockMvc.perform(post("/api/auth/apple/callback"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.RATE_LIMITED));

        assertThat(rateLimitStore.charges()).singleElement()
                .satisfies(charge -> assertThat(charge.key()).endsWith(":auth"));
    }

    @Test
    void declinedConsent_WhenCallbackRequested_RedirectsWithTheLoginErrorCode() throws Exception {
        mockMvc.perform(get("/api/auth/google/callback").param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.ERROR));
    }

    @Test
    void rejectionCodes_WhenCompared_AreDistinct() {
        assertThat(LoginRedirect.RATE_LIMITED).isNotEqualTo(LoginRedirect.ERROR);
    }

    private static RateLimitProperties authBucket() {
        RateLimitProperties.BucketConfig bucket = new RateLimitProperties.BucketConfig();
        bucket.setCapacity(10);
        bucket.setRefillTokens(10);
        bucket.setRefillDurationSeconds(60);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setAuth(bucket);
        return properties;
    }
}
