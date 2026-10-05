package org.danteplanner.backend.ratelimit;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.danteplanner.backend.auth.controller.AuthController;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthStateService;
import org.danteplanner.backend.auth.service.AuthenticationService;
import org.danteplanner.backend.auth.service.SessionRevocationService;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.planner.controller.PublishedPlannerController;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.config.LoginRedirect;
import org.danteplanner.backend.shared.config.OAuthProperties;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.ratelimitfixture.DeclaredHandlerFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisConnectionException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitStoreOutageTest {

    private static final String SKIPPED_COUNTER = "rate_limit.charge_skipped";
    private static final String TRUSTED_PROXY_IP = "127.0.0.1";
    private static final Duration BUCKET_TTL = Duration.ofHours(1);
    private static final String FRONTEND_URL = "https://planner.example";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unreachableStore_WhenProxyManagerBuilt_ThrowsRedisConnectionException() throws IOException {
        int port = closedPort();

        assertThatThrownBy(() -> RedisConnectionConfig.buildRateLimitProxyManager(TRUSTED_PROXY_IP, port, BUCKET_TTL))
                .isInstanceOf(RedisConnectionException.class)
                .hasMessageEndingWith(":" + port);
    }

    @Test
    void publishedList_WhenStoreUnreachable_ServesAndCountsTheSkippedCharge() throws Exception {
        MockMvc mockMvc = mvcAgainst(new RateLimitService(unreachableProxyManager(), bucketProperties()));

        mockMvc.perform(get("/api/planner/md/published").with(request -> {
                    request.setRemoteAddr(TRUSTED_PROXY_IP);
                    request.addHeader("CF-Connecting-IP", "203.0.113.7");
                    return request;
                }))
                .andExpect(status().isOk());

        assertThat(skipped(RateLimitPolicy.PUBLIC_READ)).isEqualTo(1.0);
    }

    @Test
    void crudHandler_WhenStoreUnreachable_RunsAndCountsTheSkippedCharge() throws Exception {
        MockMvc mockMvc = mvcAgainst(new RateLimitService(unreachableProxyManager(), bucketProperties()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));

        mockMvc.perform(get("/api/fixture/inherited"))
                .andExpect(status().isOk());

        assertThat(skipped(RateLimitPolicy.CRUD)).isEqualTo(1.0);
    }

    @Test
    void googleCallback_WhenStoreUnreachable_RedirectsToLoginUnavailable() throws Exception {
        MockMvc mockMvc = mvcAgainst(new RateLimitService(unreachableProxyManager(), bucketProperties()));

        mockMvc.perform(get("/api/auth/google/callback").param("code", "any").param("state", "any"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.UNAVAILABLE));

        assertThat(meterRegistry.find(SKIPPED_COUNTER).counter()).isNull();
    }

    @Test
    void appleCallback_WhenStoreUnreachable_RedirectsToLoginUnavailable() throws Exception {
        MockMvc mockMvc = mvcAgainst(new RateLimitService(unreachableProxyManager(), bucketProperties()));

        mockMvc.perform(post("/api/auth/apple/callback"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.UNAVAILABLE));

        assertThat(meterRegistry.find(SKIPPED_COUNTER).counter()).isNull();
    }

    @Test
    void publishedList_WhenChargeTimesOut_ServesAndCountsTheSkippedCharge() throws Exception {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        doThrow(new io.github.bucket4j.TimeoutException(
                "Violated timeout while waiting for redis future", 3_000_000_000L, 3_000_000_000L))
                .when(rateLimitService).check(eq(RateLimitPolicy.PUBLIC_READ), anyString());

        mvcAgainst(rateLimitService).perform(get("/api/planner/md/published"))
                .andExpect(status().isOk());

        assertThat(skipped(RateLimitPolicy.PUBLIC_READ)).isEqualTo(1.0);
    }

    @Test
    void appleCallback_WhenChargeTimesOut_RedirectsToLoginUnavailable() throws Exception {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        doThrow(new io.github.bucket4j.TimeoutException(
                "Violated timeout while waiting for redis future", 3_000_000_000L, 3_000_000_000L))
                .when(rateLimitService).check(eq(RateLimitPolicy.AUTH), anyString());

        mvcAgainst(rateLimitService).perform(post("/api/auth/apple/callback"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.UNAVAILABLE));
    }

    @Test
    void skippedCharges_WhenStoreFailsInBursts_WarnOncePerBurst() throws Exception {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        doThrow(new RedisConnectionException("down"))
                .doThrow(new RedisConnectionException("down"))
                .doThrow(new RedisConnectionException("down"))
                .doNothing()
                .doThrow(new RedisConnectionException("down"))
                .when(rateLimitService).check(eq(RateLimitPolicy.PUBLIC_READ), anyString());
        MockMvc mockMvc = mvcAgainst(rateLimitService);

        try (LogCapture interceptorLog = LogCapture.onThisThread(RateLimitInterceptor.class)) {
            for (int request = 0; request < 5; request++) {
                mockMvc.perform(get("/api/planner/md/published")).andExpect(status().isOk());
            }

            assertThat(interceptorLog.at(Level.WARN)).hasSize(2);
        }
        assertThat(skipped(RateLimitPolicy.PUBLIC_READ)).isEqualTo(4.0);
    }

    @Test
    void policies_WhenClassified_OnlyAuthFailsClosed() {
        assertThat(List.of(RateLimitPolicy.values()))
                .filteredOn(RateLimitPolicy::failsClosed)
                .containsExactly(RateLimitPolicy.AUTH);
    }

    private MockMvc mvcAgainst(RateLimitService rateLimitService) {
        CookieUtils cookieUtils = new CookieUtils(false, "", "Lax");
        FrontendProperties frontendProperties = new FrontendProperties(FRONTEND_URL);
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setTrustedProxyIps(TRUSTED_PROXY_IP);
        securityProperties.parseTrustedProxyIps();
        DeviceIdResolver deviceIdResolver = new DeviceIdResolver(cookieUtils);

        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimitService,
                securityProperties,
                deviceIdResolver,
                frontendProperties,
                new ProblemWriter(Jackson2ObjectMapperBuilder.json().build()),
                meterRegistry);

        AuthController authController = new AuthController(
                mock(AuthenticationService.class),
                mock(SessionRevocationService.class),
                mock(TokenValidator.class),
                mock(OAuthProperties.class),
                cookieUtils,
                mock(JwtProperties.class),
                mock(OAuthStateService.class),
                mock(OAuthProviderRegistry.class),
                frontendProperties);
        PublishedPlannerController publishedPlannerController = new PublishedPlannerController(
                mock(PublishedPlannerQueryService.class), securityProperties, new ByIdReadGuard(), deviceIdResolver);

        return MockMvcBuilders.standaloneSetup(authController, publishedPlannerController, new DeclaredHandlerFixture())
                .addInterceptors(interceptor)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ProxyManager<byte[]> unreachableProxyManager() throws IOException {
        int port = closedPort();
        ProxyManager<byte[]> proxyManager = mock(ProxyManager.class);
        when(proxyManager.builder()).thenAnswer(invocation ->
                RedisConnectionConfig.buildRateLimitProxyManager(TRUSTED_PROXY_IP, port, BUCKET_TTL).builder());
        return proxyManager;
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static RateLimitProperties bucketProperties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCrud(bucket());
        properties.setAuth(bucket());
        properties.setPublicRead(bucket());
        return properties;
    }

    private static BucketConfig bucket() {
        BucketConfig config = new BucketConfig();
        config.setCapacity(10);
        config.setRefillTokens(10);
        config.setRefillDurationSeconds(60);
        return config;
    }

    private double skipped(RateLimitPolicy policy) {
        Counter counter = meterRegistry.find(SKIPPED_COUNTER).tag("policy", policy.name()).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
