package org.danteplanner.backend.service;
import org.danteplanner.backend.shared.config.RateLimitProperties;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;

import org.danteplanner.backend.shared.exception.RateLimitExceededException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.redis.testcontainers.RedisContainer;

import io.github.bucket4j.distributed.proxy.ProxyManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitService.
 *
 * <p>Tests Bucket4j rate limiting behavior over a Redis-backed bucket4j
 * {@link ProxyManager}: bucket creation, request consumption within limits,
 * rate limit exceeded exceptions, per-user/per-endpoint isolation, and that
 * bucket keys live in the local rate-limit Redis (with a TTL) and never in
 * the auth Redis.</p>
 *
 * <p>A test needing a bucket shape of its own rebinds the {@code crud} bucket and charges
 * {@link RateLimitPolicy#CRUD} with a caller-named endpoint, so the key under test stays
 * {@code userId:endpoint}.</p>
 */
@Tag("containerized")
class RateLimitServiceIT {

    private static final String REDIS_IMAGE = "redis:7-alpine";

    private static final RedisContainer REDIS = new RedisContainer(REDIS_IMAGE);
    private static final RedisContainer AUTH_REDIS = new RedisContainer(REDIS_IMAGE);

    private static StringRedisTemplate rateLimitTemplate;
    private static StringRedisTemplate authTemplate;

    private RateLimitProperties properties;
    private RateLimitService rateLimitService;
    private RateLimitProperties.BucketConfig testBucketConfig;

    @BeforeAll
    static void startContainers() {
        REDIS.start();
        AUTH_REDIS.start();
        rateLimitTemplate = buildTemplate(REDIS.getRedisHost(), REDIS.getRedisPort());
        authTemplate = buildTemplate(AUTH_REDIS.getRedisHost(), AUTH_REDIS.getRedisPort());
    }

    @AfterAll
    static void stopContainers() {
        REDIS.stop();
        AUTH_REDIS.stop();
    }

    private static StringRedisTemplate buildTemplate(String host, int port) {
        LettuceConnectionFactory f = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        f.afterPropertiesSet();
        StringRedisTemplate t = new StringRedisTemplate(f);
        t.afterPropertiesSet();
        return t;
    }

    @BeforeEach
    void setUp() {
        // Externalized bucket state: flush both Redis DBs so leftover keys from a
        // prior test cannot leak into this one.
        rateLimitTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        authTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        ProxyManager<byte[]> proxyManager = RedisConnectionConfig.buildRateLimitProxyManager(
                REDIS.getRedisHost(), REDIS.getRedisPort(), Duration.ofSeconds(60));
        properties = new RateLimitProperties();
        rateLimitService = new RateLimitService(proxyManager, properties);

        // Configure a test bucket: 5 requests per 10 seconds
        testBucketConfig = new RateLimitProperties.BucketConfig();
        testBucketConfig.setCapacity(5);
        testBucketConfig.setRefillTokens(5);
        testBucketConfig.setRefillDurationSeconds(10);

        // Set up CRUD config for CRUD policy tests
        RateLimitProperties.BucketConfig crudConfig = new RateLimitProperties.BucketConfig();
        crudConfig.setCapacity(10);
        crudConfig.setRefillTokens(10);
        crudConfig.setRefillDurationSeconds(60);
        properties.setCrud(crudConfig);

        // Set up import config for IMPORT policy tests
        RateLimitProperties.BucketConfig importConfig = new RateLimitProperties.BucketConfig();
        importConfig.setCapacity(3);
        importConfig.setRefillTokens(3);
        importConfig.setRefillDurationSeconds(300);
        properties.setImportConfig(importConfig);

        // Set up SSE config for SSE policy tests
        RateLimitProperties.BucketConfig sseConfig = new RateLimitProperties.BucketConfig();
        sseConfig.setCapacity(2);
        sseConfig.setRefillTokens(2);
        sseConfig.setRefillDurationSeconds(60);
        properties.setSse(sseConfig);
    }

    @Nested
    @DisplayName("Bucket Allows Requests Within Limit Tests")
    class AllowRequestsWithinLimitTests {

        @Test
        @DisplayName("Should allow requests within capacity limit")
        void checkRateLimit_WhenWithinLimit_Succeeds() {
            Long userId = 1L;
            String endpoint = "test-endpoint";
            properties.setCrud(testBucketConfig);

            // Should not throw for 5 requests (capacity = 5)
            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint),
                        "Request " + (i + 1) + " should succeed");
            }
        }

        @Test
        @DisplayName("Should allow CRUD requests within limit")
        void checkCrudLimit_WhenWithinLimit_Succeeds() {
            Long userId = 1L;

            // Should not throw for 10 requests (CRUD capacity = 10)
            for (int i = 0; i < 10; i++) {
                assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.CRUD, userId, "planners"),
                        "CRUD request " + (i + 1) + " should succeed");
            }
        }

        @Test
        @DisplayName("Should allow import requests within limit")
        void checkImportLimit_WhenWithinLimit_Succeeds() {
            Long userId = 1L;

            // Should not throw for 3 requests (import capacity = 3)
            for (int i = 0; i < 3; i++) {
                assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.IMPORT, userId),
                        "Import request " + (i + 1) + " should succeed");
            }
        }

        @Test
        @DisplayName("Should allow SSE requests within limit")
        void checkSseLimit_WhenWithinLimit_Succeeds() {
            Long userId = 1L;

            // Should not throw for 2 requests (SSE capacity = 2)
            for (int i = 0; i < 2; i++) {
                assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.SSE, userId),
                        "SSE request " + (i + 1) + " should succeed");
            }
        }
    }

    @Nested
    @DisplayName("Bucket Throws RateLimitExceededException When Limit Exceeded Tests")
    class ExceededLimitTests {

        @Test
        @DisplayName("Should throw RateLimitExceededException when capacity exceeded")
        void checkRateLimit_WhenExceedsLimit_ThrowsException() {
            Long userId = 1L;
            String endpoint = "test-endpoint";
            properties.setCrud(testBucketConfig);

            // Consume all 5 tokens
            for (int i = 0; i < 5; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint);
            }

            // 6th request should fail
            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint)
            );

            assertEquals(userId, exception.getUserId());
            assertEquals(endpoint, exception.getEndpoint());
            assertTrue(exception.getMessage().contains(userId.toString()));
            assertTrue(exception.getMessage().contains(endpoint));
        }

        @Test
        @DisplayName("Should throw RateLimitExceededException for CRUD when limit exceeded")
        void checkCrudLimit_WhenExceedsLimit_ThrowsException() {
            Long userId = 1L;

            // Consume all 10 tokens
            for (int i = 0; i < 10; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, userId, "planners");
            }

            // 11th request should fail
            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, userId, "planners")
            );

            assertEquals(userId, exception.getUserId());
            assertEquals("planners", exception.getEndpoint());
        }

        @Test
        @DisplayName("Should throw RateLimitExceededException for import when limit exceeded")
        void checkImportLimit_WhenExceedsLimit_ThrowsException() {
            Long userId = 1L;

            // Consume all 3 tokens
            for (int i = 0; i < 3; i++) {
                rateLimitService.check(RateLimitPolicy.IMPORT, userId);
            }

            // 4th request should fail
            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.IMPORT, userId)
            );

            assertEquals(userId, exception.getUserId());
            assertEquals("import", exception.getEndpoint());
        }

        @Test
        @DisplayName("Should throw RateLimitExceededException for SSE when limit exceeded")
        void checkSseLimit_WhenExceedsLimit_ThrowsException() {
            Long userId = 1L;

            // Consume all 2 tokens
            for (int i = 0; i < 2; i++) {
                rateLimitService.check(RateLimitPolicy.SSE, userId);
            }

            // 3rd request should fail
            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.SSE, userId)
            );

            assertEquals(userId, exception.getUserId());
            assertEquals("sse", exception.getEndpoint());
        }
    }

    @Nested
    @DisplayName("Different Endpoints Have Separate Buckets Tests")
    class SeparateBucketsPerEndpointTests {

        @Test
        @DisplayName("Should maintain separate buckets for different endpoints")
        void checkRateLimit_WhenDifferentEndpoints_SeparateBuckets() {
            Long userId = 1L;
            properties.setCrud(testBucketConfig);

            // Exhaust capacity for endpoint1
            for (int i = 0; i < 5; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, userId, "endpoint1");
            }

            // Should still allow requests to endpoint2 (separate bucket)
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.CRUD, userId, "endpoint2"));

            // endpoint1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, userId, "endpoint1"));
        }

        @Test
        @DisplayName("Should maintain separate buckets for CRUD vs import")
        void checkRateLimit_WhenCrudVsImport_SeparateBuckets() {
            Long userId = 1L;

            // Exhaust CRUD capacity
            for (int i = 0; i < 10; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, userId, "planners");
            }

            // Import should still work (separate bucket)
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.IMPORT, userId));

            // CRUD should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, userId, "planners"));
        }

        @Test
        @DisplayName("Should maintain separate buckets for different CRUD endpoints")
        void checkCrudLimit_WhenDifferentEndpoints_SeparateBuckets() {
            Long userId = 1L;

            // Exhaust capacity for planners endpoint
            for (int i = 0; i < 10; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, userId, "planners");
            }

            // Should still allow requests to different endpoint
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.CRUD, userId, "other-resource"));
        }
    }

    @Nested
    @DisplayName("Different Users Have Separate Buckets Tests")
    class SeparateBucketsPerUserTests {

        @Test
        @DisplayName("Should maintain separate buckets for different users")
        void checkRateLimit_WhenDifferentUsers_SeparateBuckets() {
            Long user1 = 1L;
            Long user2 = 2L;
            String endpoint = "test-endpoint";
            properties.setCrud(testBucketConfig);

            // Exhaust user1's capacity
            for (int i = 0; i < 5; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, user1, endpoint);
            }

            // User2 should still be able to make requests (separate bucket)
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.CRUD, user2, endpoint));

            // User1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, user1, endpoint));
        }

        @Test
        @DisplayName("Should maintain separate CRUD buckets for different users")
        void checkCrudLimit_WhenDifferentUsers_SeparateBuckets() {
            Long user1 = 1L;
            Long user2 = 2L;

            // Exhaust user1's CRUD capacity
            for (int i = 0; i < 10; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, user1, "planners");
            }

            // User2 should still be able to make requests
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.CRUD, user2, "planners"));

            // User1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, user1, "planners"));
        }

        @Test
        @DisplayName("Should maintain separate import buckets for different users")
        void checkImportLimit_WhenDifferentUsers_SeparateBuckets() {
            Long user1 = 1L;
            Long user2 = 2L;

            // Exhaust user1's import capacity
            for (int i = 0; i < 3; i++) {
                rateLimitService.check(RateLimitPolicy.IMPORT, user1);
            }

            // User2 should still be able to import
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.IMPORT, user2));

            // User1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.IMPORT, user1));
        }

        @Test
        @DisplayName("Should maintain separate SSE buckets for different users")
        void checkSseLimit_WhenDifferentUsers_SeparateBuckets() {
            Long user1 = 1L;
            Long user2 = 2L;

            // Exhaust user1's SSE capacity
            for (int i = 0; i < 2; i++) {
                rateLimitService.check(RateLimitPolicy.SSE, user1);
            }

            // User2 should still be able to connect
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.SSE, user2));

            // User1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.SSE, user1));
        }
    }

    @Nested
    @DisplayName("Bucket Key Format Tests")
    class BucketKeyTests {

        @Test
        @DisplayName("Should create unique bucket for each user-endpoint combination")
        void checkRateLimit_WhenUniqueKeys_SeparateBuckets() {
            // User 1 + endpoint A
            // User 1 + endpoint B
            // User 2 + endpoint A
            // User 2 + endpoint B
            // All should have separate buckets

            Long user1 = 1L;
            Long user2 = 2L;
            String endpointA = "endpoint-a";
            String endpointB = "endpoint-b";
            properties.setCrud(testBucketConfig);

            // Each combination should allow up to 5 requests independently
            for (int i = 0; i < 5; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, user1, endpointA);
            }
            for (int i = 0; i < 5; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, user1, endpointB);
            }
            for (int i = 0; i < 5; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, user2, endpointA);
            }
            for (int i = 0; i < 5; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, user2, endpointB);
            }

            // All combinations should now be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, user1, endpointA));
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, user1, endpointB));
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, user2, endpointA));
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, user2, endpointB));
        }
    }

    @Nested
    @DisplayName("BucketConfig Tests")
    class BucketConfigTests {

        @Test
        @DisplayName("Should correctly store and retrieve capacity")
        void bucketConfig_WhenCapacity_StoredCorrectly() {
            RateLimitProperties.BucketConfig config = new RateLimitProperties.BucketConfig();
            config.setCapacity(100);

            assertEquals(100, config.getCapacity());
        }

        @Test
        @DisplayName("Should correctly store and retrieve refill tokens")
        void bucketConfig_WhenRefillTokens_StoredCorrectly() {
            RateLimitProperties.BucketConfig config = new RateLimitProperties.BucketConfig();
            config.setRefillTokens(50);

            assertEquals(50, config.getRefillTokens());
        }

        @Test
        @DisplayName("Should correctly store and retrieve refill duration")
        void bucketConfig_WhenRefillDuration_StoredCorrectly() {
            RateLimitProperties.BucketConfig config = new RateLimitProperties.BucketConfig();
            config.setRefillDurationSeconds(120);

            assertEquals(120, config.getRefillDurationSeconds());
        }
    }

    @Nested
    @DisplayName("Auth Limit (Identifier-Based) Tests")
    class AuthLimitIdentifierTests {

        @Test
        @DisplayName("Should create separate buckets for different identifiers")
        void checkAuthLimit_WhenDifferentIdentifiers_SeparateBuckets() {
            // Set up auth config
            RateLimitProperties.BucketConfig authConfig = new RateLimitProperties.BucketConfig();
            authConfig.setCapacity(5);
            authConfig.setRefillTokens(5);
            authConfig.setRefillDurationSeconds(60);
            properties.setAuth(authConfig);

            String ipIdentifier = "ip:203.0.113.1";
            String deviceIdentifier = "device:abc-123";

            // Exhaust IP identifier bucket
            for (int i = 0; i < 5; i++) {
                rateLimitService.check(RateLimitPolicy.AUTH, ipIdentifier);
            }

            // Device identifier should still work (separate bucket)
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.AUTH, deviceIdentifier));

            // IP identifier should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.AUTH, ipIdentifier));
        }

        @Test
        @DisplayName("Should isolate buckets between ip and device prefixes")
        void checkAuthLimit_WhenIpVsDevice_Isolated() {
            RateLimitProperties.BucketConfig authConfig = new RateLimitProperties.BucketConfig();
            authConfig.setCapacity(3);
            authConfig.setRefillTokens(3);
            authConfig.setRefillDurationSeconds(60);
            properties.setAuth(authConfig);

            // Different identifiers should not collide
            rateLimitService.check(RateLimitPolicy.AUTH, "ip:192.168.1.1");
            rateLimitService.check(RateLimitPolicy.AUTH, "ip:192.168.1.1");
            rateLimitService.check(RateLimitPolicy.AUTH, "ip:192.168.1.1");

            // ip:192.168.1.1 exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.AUTH, "ip:192.168.1.1"));

            // device:192.168.1.1 should have separate bucket
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.AUTH, "device:192.168.1.1"));
        }

        @Test
        @DisplayName("Should use unified bucket key format identifier:auth")
        void checkAuthLimit_WhenBucketKeyFormat_IdentifierColonAuth() {
            RateLimitProperties.BucketConfig authConfig = new RateLimitProperties.BucketConfig();
            authConfig.setCapacity(2);
            authConfig.setRefillTokens(2);
            authConfig.setRefillDurationSeconds(60);
            properties.setAuth(authConfig);

            String identifier = "device:test-device";

            // Use 2 tokens
            rateLimitService.check(RateLimitPolicy.AUTH, identifier);
            rateLimitService.check(RateLimitPolicy.AUTH, identifier);

            // 3rd should fail
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.AUTH, identifier));
        }

        @Test
        @DisplayName("Should handle unknown device identifier")
        void checkAuthLimit_WhenUnknownDevice_Works() {
            RateLimitProperties.BucketConfig authConfig = new RateLimitProperties.BucketConfig();
            authConfig.setCapacity(1);
            authConfig.setRefillTokens(1);
            authConfig.setRefillDurationSeconds(60);
            properties.setAuth(authConfig);

            String unknownIdentifier = "device:unknown";

            // Should work for first request
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.AUTH, unknownIdentifier));

            // Should fail for second (bucket exhausted)
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.AUTH, unknownIdentifier));
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle single capacity bucket correctly")
        void checkRateLimit_WhenSingleCapacity_ExhaustsAfterOne() {
            RateLimitProperties.BucketConfig singleConfig = new RateLimitProperties.BucketConfig();
            singleConfig.setCapacity(1);
            singleConfig.setRefillTokens(1);
            singleConfig.setRefillDurationSeconds(60);
            properties.setCrud(singleConfig);

            Long userId = 1L;
            String endpoint = "single-test";

            // First request should succeed
            assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint));

            // Second request should fail
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint));
        }

        @Test
        @DisplayName("Should handle large capacity bucket correctly")
        void checkRateLimit_WhenLargeCapacity_HandlesCorrectly() {
            RateLimitProperties.BucketConfig largeConfig = new RateLimitProperties.BucketConfig();
            largeConfig.setCapacity(1000);
            largeConfig.setRefillTokens(1000);
            largeConfig.setRefillDurationSeconds(86400);
            properties.setCrud(largeConfig);

            Long userId = 1L;
            String endpoint = "large-test";

            // Should allow 1000 requests
            for (int i = 0; i < 1000; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint);
            }

            // 1001st should fail
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint));
        }

        @Test
        @DisplayName("Should reuse bucket for same user-endpoint combination")
        void checkRateLimit_WhenSameKey_ReusesBucket() {
            Long userId = 1L;
            String endpoint = "reuse-test";
            properties.setCrud(testBucketConfig);

            // Use 3 tokens
            for (int i = 0; i < 3; i++) {
                rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint);
            }

            // Use 2 more tokens (should work, total 5)
            for (int i = 0; i < 2; i++) {
                assertDoesNotThrow(() -> rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint));
            }

            // 6th should fail
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitService.check(RateLimitPolicy.CRUD, userId, endpoint));
        }
    }

    @Nested
    @DisplayName("Local Redis Persistence Acceptance Tests")
    class LocalRedisPersistenceTests {

        @Test
        @DisplayName("Consuming a token persists the bucket key in the local rate-limit Redis with a positive TTL")
        void rateLimitBucket_WhenConsumed_PersistsInLocalRedisWithTtl() {
            ProxyManager<byte[]> proxyManager = RedisConnectionConfig.buildRateLimitProxyManager(
                    REDIS.getRedisHost(), REDIS.getRedisPort(), Duration.ofSeconds(2));

            RateLimitProperties.BucketConfig cfg = new RateLimitProperties.BucketConfig();
            cfg.setCapacity(5);
            cfg.setRefillTokens(5);
            cfg.setRefillDurationSeconds(10);
            RateLimitProperties ttlProperties = new RateLimitProperties();
            ttlProperties.setCrud(cfg);
            RateLimitService service = new RateLimitService(proxyManager, ttlProperties);

            // Consume one token for the known key "1:ttl-probe"
            service.check(RateLimitPolicy.CRUD, 1L, "ttl-probe");

            // The bucket key must exist in the local rate-limit Redis...
            assertThat(rateLimitTemplate.hasKey("1:ttl-probe")).isTrue();

            // ...and carry a positive Redis TTL bounded by the configured bucketTtl (2s).
            Long ttl = rateLimitTemplate.getExpire("1:ttl-probe");
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0L);
            assertThat(ttl).isLessThanOrEqualTo(2L);
        }

        @Test
        @DisplayName("Consuming a token writes to the rate-limit Redis only, never the auth Redis")
        void rateLimitBucket_WhenConsumed_IsAbsentFromAuthRedis() {
            ProxyManager<byte[]> proxyManager = RedisConnectionConfig.buildRateLimitProxyManager(
                    REDIS.getRedisHost(), REDIS.getRedisPort(), Duration.ofSeconds(2));

            RateLimitProperties.BucketConfig cfg = new RateLimitProperties.BucketConfig();
            cfg.setCapacity(5);
            cfg.setRefillTokens(5);
            cfg.setRefillDurationSeconds(10);
            RateLimitProperties authProbeProperties = new RateLimitProperties();
            authProbeProperties.setCrud(cfg);
            RateLimitService service = new RateLimitService(proxyManager, authProbeProperties);

            // Consume one token for the known key "2:auth-probe"
            service.check(RateLimitPolicy.CRUD, 2L, "auth-probe");

            // Present in the local rate-limit Redis, absent from the auth Redis.
            assertThat(rateLimitTemplate.hasKey("2:auth-probe")).isTrue();
            assertThat(authTemplate.hasKey("2:auth-probe")).isFalse();
        }
    }
}
