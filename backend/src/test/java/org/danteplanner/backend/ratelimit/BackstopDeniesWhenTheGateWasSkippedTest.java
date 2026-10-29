package org.danteplanner.backend.ratelimit;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.InMemoryProxyManager;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.ratelimitfixture.BareHandlerFixture;
import org.danteplanner.ratelimitfixture.DeclaredHandlerFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.exception.ProblemWriter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.http.MediaType;

import ch.qos.logback.classic.Level;
import org.danteplanner.backend.support.TestJsonMappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario: Backstop denies when the gate was skipped.
 *
 * <p>The coverage rule makes an undeclared handler unreachable in a build that ran its tests. This
 * covers the deploy where it did not: the interceptor refuses rather than waving the request
 * through, because a silently unguarded endpoint is the failure the whole seam exists to end.</p>
 */
class BackstopDeniesWhenTheGateWasSkippedTest {

    private final BareHandlerFixture bareFixture = new BareHandlerFixture();

    private final InMemoryProxyManager rateLimitStore = new InMemoryProxyManager();
    private LogCapture interceptorLog;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.parseTrustedProxyIps();
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new RateLimitService(rateLimitStore, crudBucket()),
                securityProperties,
                new DeviceIdResolver(new CookieUtils(false, "", "Lax")),
                new FrontendProperties("https://planner.example"),
                new ProblemWriter(TestJsonMappers.springDefaults()),
                new SimpleMeterRegistry());

        mockMvc = MockMvcBuilders.standaloneSetup(bareFixture, new DeclaredHandlerFixture())
                .addInterceptors(interceptor)
                .build();

        interceptorLog = LogCapture.onThisThread(RateLimitInterceptor.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        interceptorLog.close();
    }

    @Test
    void undeclaredHandler_WhenRequested_IsDeniedAndNeverReached() throws Exception {
        mockMvc.perform(get("/api/fixture/bare"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_UNDECLARED"))
                .andExpect(jsonPath("$.detail").value("Request rejected"))
                .andExpect(jsonPath("$.message").value("Request rejected"));

        assertThat(bareFixture.bodyRan()).isFalse();
        assertThat(rateLimitStore.charges()).isEmpty();

        assertThat(interceptorLog.events()).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains(BareHandlerFixture.class.getName() + ".bare");
        });
    }

    @Test
    void inheritedPolicy_WhenRequested_IsChargedAndReached() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));

        mockMvc.perform(get("/api/fixture/inherited"))
                .andExpect(status().isOk());

        assertThat(rateLimitStore.charges()).extracting(InMemoryProxyManager.Charge::key)
                .containsExactly("7:fixture");
        assertThat(interceptorLog.events()).isEmpty();
    }

    @Test
    void methodExemption_WhenRequested_OverridesTheClassPolicy() throws Exception {
        mockMvc.perform(get("/api/fixture/exempt"))
                .andExpect(status().isOk());

        assertThat(rateLimitStore.charges()).isEmpty();
        assertThat(interceptorLog.events()).isEmpty();
    }

    private static RateLimitProperties crudBucket() {
        RateLimitProperties.BucketConfig bucket = new RateLimitProperties.BucketConfig();
        bucket.setCapacity(10);
        bucket.setRefillTokens(10);
        bucket.setRefillDurationSeconds(60);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCrud(bucket);
        return properties;
    }
}
