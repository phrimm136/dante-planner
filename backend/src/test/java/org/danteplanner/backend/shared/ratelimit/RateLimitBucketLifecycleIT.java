package org.danteplanner.backend.shared.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.ratelimitfixture.DeclaredHandlerFixture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.redis.testcontainers.RedisContainer;

import org.assertj.core.api.SoftAssertions;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Tag("containerized")
class RateLimitBucketLifecycleIT {

    private static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");
    private static final long USER_ID = 7L;
    private static final String ENDPOINT = "fixture";
    private static final Duration REFILL_PERIOD = Duration.ofSeconds(60);

    private ProxyManager<byte[]> proxyManager;
    private RateLimitProperties properties;
    private RateLimitService rateLimitService;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        proxyManager = RedisConnectionConfig.buildRateLimitProxyManager(
                REDIS.getRedisHost(), REDIS.getRedisPort(), Duration.ofSeconds(60));
        properties = new RateLimitProperties();
        properties.setConfigVersion(1);
        rateLimitService = new RateLimitService(proxyManager, properties);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static RateLimitProperties.BucketConfig bucket(int capacity) {
        RateLimitProperties.BucketConfig config = new RateLimitProperties.BucketConfig();
        config.setCapacity(capacity);
        config.setRefillTokens(capacity);
        config.setRefillDurationSeconds((int) REFILL_PERIOD.toSeconds());
        return config;
    }

    private long storedCapacity(long userId) {
        byte[] key = (userId + ":" + ENDPOINT).getBytes(StandardCharsets.UTF_8);
        BucketConfiguration stored = proxyManager.getProxyConfiguration(key).orElseThrow();
        return stored.getBandwidths()[0].getCapacity();
    }

    private int successfulCharges(long userId, int attempts) {
        int passed = 0;
        for (int i = 0; i < attempts; i++) {
            if (catchThrowable(() -> rateLimitService.check(RateLimitPolicy.CRUD, userId, ENDPOINT)) == null) {
                passed++;
            }
        }
        return passed;
    }

    @Test
    void check_WhenLimitLoweredOnALiveBucket_EnforcesTheLoweredLimit() {
        long userId = 101L;
        properties.setCrud(bucket(10));
        rateLimitService.check(RateLimitPolicy.CRUD, userId, ENDPOINT);

        properties.setCrud(bucket(3));
        properties.setConfigVersion(2);
        int passed = successfulCharges(userId, 10);

        long stored = storedCapacity(userId);
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(stored).as("stored capacity after lowering 10 -> 3").isEqualTo(3);
            soft.assertThat(passed).as("charges admitted after lowering 10 -> 3").isLessThanOrEqualTo(3);
        });
    }

    @Test
    void check_WhenLimitRaisedOnALiveBucket_StoresTheRaisedLimit() {
        long userId = 102L;
        properties.setCrud(bucket(3));
        rateLimitService.check(RateLimitPolicy.CRUD, userId, ENDPOINT);

        properties.setCrud(bucket(10));
        properties.setConfigVersion(2);
        rateLimitService.check(RateLimitPolicy.CRUD, userId, ENDPOINT);

        assertThat(storedCapacity(userId)).as("stored capacity after raising 3 -> 10").isEqualTo(10);
    }

    @Test
    void check_WhenLimitRaisedOnALiveBucket_CarriesTheRemainingTokensAsIs() {
        long userId = 105L;
        long startNanos = System.nanoTime();
        properties.setCrud(bucket(3));
        rateLimitService.check(RateLimitPolicy.CRUD, userId, ENDPOINT);
        rateLimitService.check(RateLimitPolicy.CRUD, userId, ENDPOINT);

        properties.setCrud(bucket(10));
        properties.setConfigVersion(2);
        rateLimitService.check(RateLimitPolicy.CRUD, userId, ENDPOINT);
        long available = availableTokens(userId);
        long accruable = refillAccruableSince(startNanos, 10);

        assertThat(available)
                .as("1 carried token minus the versioned charge, plus at most %d refilled", accruable)
                .isBetween(0L, accruable);
    }

    @Test
    void check_WhenLimitRaisedOnADrainedBucket_CarriesZeroTokens() {
        long userId = 106L;
        long startNanos = System.nanoTime();
        properties.setCrud(bucket(3));
        int drained = successfulCharges(userId, 3);

        properties.setCrud(bucket(10));
        properties.setConfigVersion(2);
        int passed = successfulCharges(userId, 1);
        long accruable = refillAccruableSince(startNanos, 10);

        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(drained).as("charges admitted at capacity 3").isEqualTo(3);
            soft.assertThat(passed)
                    .as("versioned charges admitted on 0 carried tokens, at most %d refilled", accruable)
                    .isLessThanOrEqualTo((int) accruable);
        });
    }

    private long availableTokens(long userId) {
        byte[] key = (userId + ":" + ENDPOINT).getBytes(StandardCharsets.UTF_8);
        return proxyManager.builder().build(key, () -> {
            throw new IllegalStateException("bucket " + userId + " was never created");
        }).getAvailableTokens();
    }

    private static long refillAccruableSince(long startNanos, int tokensPerRefillPeriod) {
        long elapsedNanos = System.nanoTime() - startNanos;
        return elapsedNanos * tokensPerRefillPeriod / REFILL_PERIOD.toNanos();
    }

    @Test
    void check_WhenLimitChangedWithoutAVersionBump_KeepsTheStoredLimit() {
        long userId = 103L;
        properties.setCrud(bucket(3));
        rateLimitService.check(RateLimitPolicy.CRUD, userId, ENDPOINT);

        properties.setCrud(bucket(10));
        rateLimitService.check(RateLimitPolicy.CRUD, userId, ENDPOINT);

        assertThat(storedCapacity(userId)).as("stored capacity after 3 -> 10 at the same version").isEqualTo(3);
    }

    @Test
    void check_WhenStoredBucketPredatesTheVersion_ReplacesItOnTheFirstCharge() {
        long userId = 104L;
        byte[] key = (userId + ":" + ENDPOINT).getBytes(StandardCharsets.UTF_8);
        properties.setCrud(bucket(10));
        proxyManager.builder().build(key, () -> BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(10).refillGreedy(10, Duration.ofSeconds(60)))
                .build()).tryConsume(1);

        properties.setCrud(bucket(3));
        int passed = successfulCharges(userId, 10);

        long stored = storedCapacity(userId);
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(stored).as("stored capacity after the first versioned charge").isEqualTo(3);
            soft.assertThat(passed).as("charges admitted after the first versioned charge").isEqualTo(3);
        });
    }

    @Test
    void exhaustedBucket_WhenOneMoreRequestArrives_Answers429WithRetryAfter() throws Exception {
        properties.setCrud(bucket(1));
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.parseTrustedProxyIps();
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimitService,
                securityProperties,
                new DeviceIdResolver(new CookieUtils(false, "", "Lax")),
                new FrontendProperties("https://planner.example"),
                new ProblemWriter(Jackson2ObjectMapperBuilder.json().build()),
                new SimpleMeterRegistry());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DeclaredHandlerFixture())
                .addInterceptors(interceptor)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        MvcResult first = mockMvc.perform(get("/api/fixture/inherited")).andReturn();
        MvcResult denied = mockMvc.perform(get("/api/fixture/inherited")).andReturn();

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(denied.getResponse().getStatus()).isEqualTo(429);
        String retryAfter = denied.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).as("Retry-After on the 429; headers=%s",
                denied.getResponse().getHeaderNames()).isNotNull().matches("\\d+");
        assertThat(Long.parseLong(retryAfter)).as("ceil of the ~60s wait for the next token").isEqualTo(60L);
    }
}
