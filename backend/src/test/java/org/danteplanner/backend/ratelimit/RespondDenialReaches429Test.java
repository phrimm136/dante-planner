package org.danteplanner.backend.ratelimit;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.InMemoryProxyManager;
import org.danteplanner.ratelimitfixture.DeclaredHandlerFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.exception.ProblemWriter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The default denial path: a refusal raised inside the interceptor still reaches the global handler
 * and renders as 429.
 *
 * <p>Every rate-limited handler but the OAuth callback relies on this. The charge used to happen
 * inside the handler, where the exception plainly reached the boundary; raising it from
 * {@code preHandle} routes it through the dispatcher's exception path instead, which is a different
 * mechanism and was until now only assumed to work.</p>
 */
class RespondDenialReaches429Test {

    private final InMemoryProxyManager rateLimitStore = new InMemoryProxyManager();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.parseTrustedProxyIps();

        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new RateLimitService(rateLimitStore, crudBucket()),
                securityProperties,
                new DeviceIdResolver(new CookieUtils(false, "", "Lax")),
                new FrontendProperties("https://planner.example"),
                new ProblemWriter(objectMapper),
                new SimpleMeterRegistry());

        mockMvc = MockMvcBuilders.standaloneSetup(new DeclaredHandlerFixture())
                .addInterceptors(interceptor)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exhaustedBucket_WhenRespondHandlerRequested_Renders429() throws Exception {
        rateLimitStore.denyNext(TimeUnit.SECONDS.toNanos(1));

        mockMvc.perform(get("/api/fixture/inherited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));

        assertThat(rateLimitStore.charges()).extracting(InMemoryProxyManager.Charge::key)
                .containsExactly("7:fixture");
    }

    @Test
    void availableBucket_WhenRespondHandlerRequested_ReachesTheHandler() throws Exception {
        mockMvc.perform(get("/api/fixture/inherited"))
                .andExpect(status().isOk());
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
