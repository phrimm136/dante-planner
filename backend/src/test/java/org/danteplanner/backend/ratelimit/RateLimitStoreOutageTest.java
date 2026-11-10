package org.danteplanner.backend.ratelimit;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.danteplanner.backend.auth.controller.AuthController;
import org.danteplanner.backend.planner.controller.PublishedPlannerController;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.LoginRedirect;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.ratelimit.RateLimitStore;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RoleOutages;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.InMemoryProxyManager;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.ratelimitfixture.DeclaredHandlerFixture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.qos.logback.classic.Level;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.support.TestJsonMappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RateLimitStoreOutageTest {

    private static final String SKIPPED_COUNTER = "rate_limit.charge_skipped";
    private static final String TRUSTED_PROXY_IP = "127.0.0.1";
    private static final Duration BUCKET_TTL = Duration.ofHours(1);
    private static final String FRONTEND_URL = "https://planner.example";
    private static final String NOT_CONNECTED = "Rate-limit store not connected";
    private static final ClientResources CLIENT_RESOURCES = DefaultClientResources.create();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final InMemoryProxyManager rateLimitStore = new InMemoryProxyManager();
    private RedisRoleHealth roleHealth = new RedisRoleHealth();

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void shutDownClientResources() {
        CLIENT_RESOURCES.shutdown();
    }

    @Test
    void rateLimitStore_WhenNeverConnected_FailsEveryCallAtOnceWithoutConnecting() throws IOException {
        try (RateLimitStore absent = new RateLimitStore(CLIENT_RESOURCES, TRUSTED_PROXY_IP, closedPort(), BUCKET_TTL)) {
            assertThatThrownBy(absent::builder)
                    .isInstanceOf(RedisConnectionException.class)
                    .hasMessage(NOT_CONNECTED);
            assertThatThrownBy(() -> absent.getProxyConfiguration(new byte[0]))
                    .isInstanceOf(RedisConnectionException.class).hasMessage(NOT_CONNECTED);
            assertThatThrownBy(() -> absent.removeProxy(new byte[0]))
                    .isInstanceOf(RedisConnectionException.class).hasMessage(NOT_CONNECTED);
            assertThatThrownBy(absent::isAsyncModeSupported)
                    .isInstanceOf(RedisConnectionException.class).hasMessage(NOT_CONNECTED);
            assertThatThrownBy(absent::asAsync)
                    .isInstanceOf(RedisConnectionException.class).hasMessage(NOT_CONNECTED);
            assertThat(absent.isConnected()).isFalse();
        }
    }

    @Test
    void connect_WhenTheStoreRefuses_FailsWithTheEndpointAndLeavesTheStoreAbsent() throws IOException {
        int port = closedPort();
        try (RateLimitStore refused = new RateLimitStore(CLIENT_RESOURCES, TRUSTED_PROXY_IP, port, BUCKET_TTL)) {
            Throwable failure = catchThrowable(() -> refused.connect().join());

            assertThat(failure).hasRootCauseInstanceOf(ConnectException.class);
            assertThat(failure.getCause()).isInstanceOf(RedisConnectionException.class)
                    .hasMessageEndingWith(":" + port);
            assertThat(refused.isConnected()).isFalse();
            assertThat(refused.role()).isEqualTo(RedisRole.RATE_LIMIT);
        }
    }

    @Test
    void connect_WhenAnAttemptIsStillRunning_HandsBackThatAttempt() throws IOException {
        try (ServerSocket neverAnswers = new ServerSocket(0);
                RateLimitStore hanging = new RateLimitStore(
                        CLIENT_RESOURCES, TRUSTED_PROXY_IP, neverAnswers.getLocalPort(), BUCKET_TTL)) {
            CompletableFuture<Void> first = hanging.connect();

            assertThat(hanging.connect()).isSameAs(first);
            assertThat(first).isNotDone();
        }
    }

    @Test
    void publishedList_WhenTheRateLimitBreakerIsOpen_ServesAtOnceAndCountsTheSkippedChargeWithoutAWarning()
            throws Exception {
        roleHealth = RoleOutages.withOpen(RedisRole.RATE_LIMIT);
        catalogHasNoPlanners();

        try (LogCapture interceptorLog = LogCapture.onThisThread(RateLimitInterceptor.class)) {
            mvcAgainst().perform(get("/api/planner/md/published")).andExpect(status().isOk());

            assertThat(interceptorLog.events()).isEmpty();
        }
        assertThat(skipped(RateLimitPolicy.PUBLIC_READ)).isEqualTo(1.0);
        assertThat(chargedKeys()).isEmpty();
    }

    @Test
    void googleCallback_WhenTheRateLimitBreakerIsOpen_RedirectsToLoginUnavailableWithoutAWarning() throws Exception {
        roleHealth = RoleOutages.withOpen(RedisRole.RATE_LIMIT);

        try (LogCapture interceptorLog = LogCapture.onThisThread(RateLimitInterceptor.class)) {
            mvcAgainst().perform(get("/api/auth/google/callback").param("code", "any").param("state", "any"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.UNAVAILABLE));

            assertThat(interceptorLog.events()).isEmpty();
        }
        assertThat(meterRegistry.find(SKIPPED_COUNTER).counter()).isNull();
        assertThat(chargedKeys()).isEmpty();
    }

    @Test
    void crudHandler_WhenOnlyAnotherRolesBreakerIsOpen_ChargesTheStore() throws Exception {
        roleHealth = RoleOutages.withOpen(RedisRole.AUTH, RedisRole.AUTH_LOCAL, RedisRole.SSE_LOCAL);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));

        mvcAgainst().perform(get("/api/fixture/inherited")).andExpect(status().isOk());

        assertThat(chargedKeys()).singleElement().satisfies(key -> assertThat(key).startsWith("7:"));
        assertThat(meterRegistry.find(SKIPPED_COUNTER).counter()).isNull();
    }

    @Test
    void publishedList_WhenStoreUnreachable_ServesAndCountsTheSkippedCharge() throws Exception {
        storeUnreachableOnNextCharge();
        catalogHasNoPlanners();
        MockMvc mockMvc = mvcAgainst();

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
        storeUnreachableOnNextCharge();
        MockMvc mockMvc = mvcAgainst();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));

        mockMvc.perform(get("/api/fixture/inherited"))
                .andExpect(status().isOk());

        assertThat(skipped(RateLimitPolicy.CRUD)).isEqualTo(1.0);
    }

    @Test
    void googleCallback_WhenStoreUnreachable_RedirectsToLoginUnavailable() throws Exception {
        storeUnreachableOnNextCharge();
        MockMvc mockMvc = mvcAgainst();

        mockMvc.perform(get("/api/auth/google/callback").param("code", "any").param("state", "any"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.UNAVAILABLE));

        assertThat(meterRegistry.find(SKIPPED_COUNTER).counter()).isNull();
    }

    @Test
    void appleCallback_WhenStoreUnreachable_RedirectsToLoginUnavailable() throws Exception {
        storeUnreachableOnNextCharge();
        MockMvc mockMvc = mvcAgainst();

        mockMvc.perform(post("/api/auth/apple/callback"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.UNAVAILABLE));

        assertThat(meterRegistry.find(SKIPPED_COUNTER).counter()).isNull();
    }

    @Test
    void publishedList_WhenChargeTimesOut_ServesAndCountsTheSkippedCharge() throws Exception {
        rateLimitStore.failNext(new io.github.bucket4j.TimeoutException(
                "Violated timeout while waiting for redis future", 3_000_000_000L, 3_000_000_000L));
        catalogHasNoPlanners();

        mvcAgainst().perform(get("/api/planner/md/published"))
                .andExpect(status().isOk());

        assertThat(skipped(RateLimitPolicy.PUBLIC_READ)).isEqualTo(1.0);
        assertThat(chargedKeys()).singleElement().satisfies(key -> assertThat(key).endsWith(":public-read"));
    }

    @Test
    void appleCallback_WhenChargeTimesOut_RedirectsToLoginUnavailable() throws Exception {
        rateLimitStore.failNext(new io.github.bucket4j.TimeoutException(
                "Violated timeout while waiting for redis future", 3_000_000_000L, 3_000_000_000L));

        mvcAgainst().perform(post("/api/auth/apple/callback"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_URL + LoginRedirect.UNAVAILABLE));
        assertThat(chargedKeys()).singleElement().satisfies(key -> assertThat(key).endsWith(":auth"));
    }

    @Test
    void skippedCharges_WhenStoreFailsInBursts_WarnOncePerBurst() throws Exception {
        catalogHasNoPlanners();
        MockMvc mockMvc = mvcAgainst();

        try (LogCapture interceptorLog = LogCapture.onThisThread(RateLimitInterceptor.class)) {
            for (int request = 0; request < 3; request++) {
                rateLimitStore.failNext(new RedisConnectionException("down"));
                mockMvc.perform(get("/api/planner/md/published")).andExpect(status().isOk());
            }
            mockMvc.perform(get("/api/planner/md/published")).andExpect(status().isOk());
            rateLimitStore.failNext(new RedisConnectionException("down"));
            mockMvc.perform(get("/api/planner/md/published")).andExpect(status().isOk());

            assertThat(interceptorLog.at(Level.WARN)).hasSize(2);
        }
        assertThat(skipped(RateLimitPolicy.PUBLIC_READ)).isEqualTo(4.0);
        assertThat(chargedKeys()).hasSize(5).allSatisfy(key -> assertThat(key).endsWith(":public-read"));
    }

    @Test
    void policies_WhenClassified_OnlyAuthFailsClosed() {
        assertThat(List.of(RateLimitPolicy.values()))
                .filteredOn(RateLimitPolicy::failsClosed)
                .containsExactly(RateLimitPolicy.AUTH);
    }

    private MockMvc mvcAgainst() {
        CookieUtils cookieUtils = new CookieUtils(false, "", "Lax");
        FrontendProperties frontendProperties = new FrontendProperties(FRONTEND_URL);
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setTrustedProxyIps(TRUSTED_PROXY_IP);
        securityProperties.parseTrustedProxyIps();
        DeviceIdResolver deviceIdResolver = new DeviceIdResolver(cookieUtils);

        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new RateLimitService(rateLimitStore, bucketProperties()),
                securityProperties,
                deviceIdResolver,
                frontendProperties,
                new ProblemWriter(TestJsonMappers.springDefaults()),
                meterRegistry,
                roleHealth);

        AuthController authController = new AuthController(
                null,
                null,
                null,
                null,
                cookieUtils,
                null,
                null,
                null,
                frontendProperties);
        PublishedPlannerQueryService queryService = new PublishedPlannerQueryService(
                null, catalogRepository, null, null, null, null, null, null, null, null);
        PublishedPlannerController publishedPlannerController = new PublishedPlannerController(
                queryService, securityProperties, new ByIdReadGuard(), deviceIdResolver);

        return MockMvcBuilders.standaloneSetup(authController, publishedPlannerController, new DeclaredHandlerFixture())
                .addInterceptors(interceptor)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private void storeUnreachableOnNextCharge() throws IOException {
        try (RateLimitStore absent = new RateLimitStore(CLIENT_RESOURCES, TRUSTED_PROXY_IP, closedPort(), BUCKET_TTL)) {
            Throwable unreachable = catchThrowable(absent::builder);
            assertThat(unreachable).isInstanceOf(RedisConnectionException.class);
            rateLimitStore.failNext((RedisConnectionException) unreachable);
        }
    }

    @SuppressWarnings("unchecked")
    private void catalogHasNoPlanners() {
        when(catalogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.<Pageable>getArgument(1), 0));
    }

    private List<String> chargedKeys() {
        return rateLimitStore.charges().stream().map(InMemoryProxyManager.Charge::key).toList();
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
