package org.danteplanner.backend.config;

import org.danteplanner.backend.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitConfig.
 *
 * <p>Tests Bucket4j rate limiting behavior including:
 * bucket creation, request consumption within limits,
 * rate limit exceeded exceptions, and per-user/per-endpoint isolation.</p>
 */
class RateLimitConfigTest {

    private RateLimitConfig rateLimitConfig;
    private RateLimitConfig.BucketConfig testBucketConfig;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig();

        // Configure a test bucket: 5 requests per 10 seconds
        testBucketConfig = new RateLimitConfig.BucketConfig();
        testBucketConfig.setCapacity(5);
        testBucketConfig.setRefillTokens(5);
        testBucketConfig.setRefillDurationSeconds(10);

        // Set up CRUD config for checkCrudLimit tests
        RateLimitConfig.BucketConfig crudConfig = new RateLimitConfig.BucketConfig();
        crudConfig.setCapacity(10);
        crudConfig.setRefillTokens(10);
        crudConfig.setRefillDurationSeconds(60);
        rateLimitConfig.setCrud(crudConfig);

        // Set up import config for checkImportLimit tests
        RateLimitConfig.BucketConfig importConfig = new RateLimitConfig.BucketConfig();
        importConfig.setCapacity(3);
        importConfig.setRefillTokens(3);
        importConfig.setRefillDurationSeconds(300);
        rateLimitConfig.setImportConfig(importConfig);

        // Set up SSE config for checkSseLimit tests
        RateLimitConfig.BucketConfig sseConfig = new RateLimitConfig.BucketConfig();
        sseConfig.setCapacity(2);
        sseConfig.setRefillTokens(2);
        sseConfig.setRefillDurationSeconds(60);
        rateLimitConfig.setSse(sseConfig);
    }

    @Nested
    @DisplayName("Bucket Allows Requests Within Limit Tests")
    class AllowRequestsWithinLimitTests {

        @Test
        @DisplayName("Should allow requests within capacity limit")
        void checkRateLimit_WithinLimit_Succeeds() {
            Long userId = 1L;
            String endpoint = "test-endpoint";

            // Should not throw for 5 requests (capacity = 5)
            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(() -> rateLimitConfig.checkRateLimit(userId, endpoint, testBucketConfig),
                        "Request " + (i + 1) + " should succeed");
            }
        }

        @Test
        @DisplayName("Should allow CRUD requests within limit")
        void checkCrudLimit_WithinLimit_Succeeds() {
            Long userId = 1L;

            // Should not throw for 10 requests (CRUD capacity = 10)
            for (int i = 0; i < 10; i++) {
                assertDoesNotThrow(() -> rateLimitConfig.checkCrudLimit(userId, "planners"),
                        "CRUD request " + (i + 1) + " should succeed");
            }
        }

        @Test
        @DisplayName("Should allow import requests within limit")
        void checkImportLimit_WithinLimit_Succeeds() {
            Long userId = 1L;

            // Should not throw for 3 requests (import capacity = 3)
            for (int i = 0; i < 3; i++) {
                assertDoesNotThrow(() -> rateLimitConfig.checkImportLimit(userId),
                        "Import request " + (i + 1) + " should succeed");
            }
        }

        @Test
        @DisplayName("Should allow SSE requests within limit")
        void checkSseLimit_WithinLimit_Succeeds() {
            Long userId = 1L;

            // Should not throw for 2 requests (SSE capacity = 2)
            for (int i = 0; i < 2; i++) {
                assertDoesNotThrow(() -> rateLimitConfig.checkSseLimit(userId),
                        "SSE request " + (i + 1) + " should succeed");
            }
        }
    }

    @Nested
    @DisplayName("Bucket Throws RateLimitExceededException When Limit Exceeded Tests")
    class ExceededLimitTests {

        @Test
        @DisplayName("Should throw RateLimitExceededException when capacity exceeded")
        void checkRateLimit_ExceedsLimit_ThrowsException() {
            Long userId = 1L;
            String endpoint = "test-endpoint";

            // Consume all 5 tokens
            for (int i = 0; i < 5; i++) {
                rateLimitConfig.checkRateLimit(userId, endpoint, testBucketConfig);
            }

            // 6th request should fail
            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(userId, endpoint, testBucketConfig)
            );

            assertEquals(userId, exception.getUserId());
            assertEquals(endpoint, exception.getEndpoint());
            assertTrue(exception.getMessage().contains(userId.toString()));
            assertTrue(exception.getMessage().contains(endpoint));
        }

        @Test
        @DisplayName("Should throw RateLimitExceededException for CRUD when limit exceeded")
        void checkCrudLimit_ExceedsLimit_ThrowsException() {
            Long userId = 1L;

            // Consume all 10 tokens
            for (int i = 0; i < 10; i++) {
                rateLimitConfig.checkCrudLimit(userId, "planners");
            }

            // 11th request should fail
            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> rateLimitConfig.checkCrudLimit(userId, "planners")
            );

            assertEquals(userId, exception.getUserId());
            assertEquals("planners", exception.getEndpoint());
        }

        @Test
        @DisplayName("Should throw RateLimitExceededException for import when limit exceeded")
        void checkImportLimit_ExceedsLimit_ThrowsException() {
            Long userId = 1L;

            // Consume all 3 tokens
            for (int i = 0; i < 3; i++) {
                rateLimitConfig.checkImportLimit(userId);
            }

            // 4th request should fail
            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> rateLimitConfig.checkImportLimit(userId)
            );

            assertEquals(userId, exception.getUserId());
            assertEquals("import", exception.getEndpoint());
        }

        @Test
        @DisplayName("Should throw RateLimitExceededException for SSE when limit exceeded")
        void checkSseLimit_ExceedsLimit_ThrowsException() {
            Long userId = 1L;

            // Consume all 2 tokens
            for (int i = 0; i < 2; i++) {
                rateLimitConfig.checkSseLimit(userId);
            }

            // 3rd request should fail
            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> rateLimitConfig.checkSseLimit(userId)
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
        void checkRateLimit_DifferentEndpoints_SeparateBuckets() {
            Long userId = 1L;

            // Exhaust capacity for endpoint1
            for (int i = 0; i < 5; i++) {
                rateLimitConfig.checkRateLimit(userId, "endpoint1", testBucketConfig);
            }

            // Should still allow requests to endpoint2 (separate bucket)
            assertDoesNotThrow(() -> rateLimitConfig.checkRateLimit(userId, "endpoint2", testBucketConfig));

            // endpoint1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(userId, "endpoint1", testBucketConfig));
        }

        @Test
        @DisplayName("Should maintain separate buckets for CRUD vs import")
        void checkRateLimit_CrudVsImport_SeparateBuckets() {
            Long userId = 1L;

            // Exhaust CRUD capacity
            for (int i = 0; i < 10; i++) {
                rateLimitConfig.checkCrudLimit(userId, "planners");
            }

            // Import should still work (separate bucket)
            assertDoesNotThrow(() -> rateLimitConfig.checkImportLimit(userId));

            // CRUD should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkCrudLimit(userId, "planners"));
        }

        @Test
        @DisplayName("Should maintain separate buckets for different CRUD endpoints")
        void checkCrudLimit_DifferentEndpoints_SeparateBuckets() {
            Long userId = 1L;

            // Exhaust capacity for planners endpoint
            for (int i = 0; i < 10; i++) {
                rateLimitConfig.checkCrudLimit(userId, "planners");
            }

            // Should still allow requests to different endpoint
            assertDoesNotThrow(() -> rateLimitConfig.checkCrudLimit(userId, "other-resource"));
        }
    }

    @Nested
    @DisplayName("Different Users Have Separate Buckets Tests")
    class SeparateBucketsPerUserTests {

        @Test
        @DisplayName("Should maintain separate buckets for different users")
        void checkRateLimit_DifferentUsers_SeparateBuckets() {
            Long user1 = 1L;
            Long user2 = 2L;
            String endpoint = "test-endpoint";

            // Exhaust user1's capacity
            for (int i = 0; i < 5; i++) {
                rateLimitConfig.checkRateLimit(user1, endpoint, testBucketConfig);
            }

            // User2 should still be able to make requests (separate bucket)
            assertDoesNotThrow(() -> rateLimitConfig.checkRateLimit(user2, endpoint, testBucketConfig));

            // User1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(user1, endpoint, testBucketConfig));
        }

        @Test
        @DisplayName("Should maintain separate CRUD buckets for different users")
        void checkCrudLimit_DifferentUsers_SeparateBuckets() {
            Long user1 = 1L;
            Long user2 = 2L;

            // Exhaust user1's CRUD capacity
            for (int i = 0; i < 10; i++) {
                rateLimitConfig.checkCrudLimit(user1, "planners");
            }

            // User2 should still be able to make requests
            assertDoesNotThrow(() -> rateLimitConfig.checkCrudLimit(user2, "planners"));

            // User1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkCrudLimit(user1, "planners"));
        }

        @Test
        @DisplayName("Should maintain separate import buckets for different users")
        void checkImportLimit_DifferentUsers_SeparateBuckets() {
            Long user1 = 1L;
            Long user2 = 2L;

            // Exhaust user1's import capacity
            for (int i = 0; i < 3; i++) {
                rateLimitConfig.checkImportLimit(user1);
            }

            // User2 should still be able to import
            assertDoesNotThrow(() -> rateLimitConfig.checkImportLimit(user2));

            // User1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkImportLimit(user1));
        }

        @Test
        @DisplayName("Should maintain separate SSE buckets for different users")
        void checkSseLimit_DifferentUsers_SeparateBuckets() {
            Long user1 = 1L;
            Long user2 = 2L;

            // Exhaust user1's SSE capacity
            for (int i = 0; i < 2; i++) {
                rateLimitConfig.checkSseLimit(user1);
            }

            // User2 should still be able to connect
            assertDoesNotThrow(() -> rateLimitConfig.checkSseLimit(user2));

            // User1 should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkSseLimit(user1));
        }
    }

    @Nested
    @DisplayName("Bucket Key Format Tests")
    class BucketKeyTests {

        @Test
        @DisplayName("Should create unique bucket for each user-endpoint combination")
        void checkRateLimit_UniqueKeys_SeparateBuckets() {
            // User 1 + endpoint A
            // User 1 + endpoint B
            // User 2 + endpoint A
            // User 2 + endpoint B
            // All should have separate buckets

            Long user1 = 1L;
            Long user2 = 2L;
            String endpointA = "endpoint-a";
            String endpointB = "endpoint-b";

            // Each combination should allow up to 5 requests independently
            for (int i = 0; i < 5; i++) {
                rateLimitConfig.checkRateLimit(user1, endpointA, testBucketConfig);
            }
            for (int i = 0; i < 5; i++) {
                rateLimitConfig.checkRateLimit(user1, endpointB, testBucketConfig);
            }
            for (int i = 0; i < 5; i++) {
                rateLimitConfig.checkRateLimit(user2, endpointA, testBucketConfig);
            }
            for (int i = 0; i < 5; i++) {
                rateLimitConfig.checkRateLimit(user2, endpointB, testBucketConfig);
            }

            // All combinations should now be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(user1, endpointA, testBucketConfig));
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(user1, endpointB, testBucketConfig));
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(user2, endpointA, testBucketConfig));
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(user2, endpointB, testBucketConfig));
        }
    }

    @Nested
    @DisplayName("BucketConfig Tests")
    class BucketConfigTests {

        @Test
        @DisplayName("Should correctly store and retrieve capacity")
        void bucketConfig_Capacity_StoredCorrectly() {
            RateLimitConfig.BucketConfig config = new RateLimitConfig.BucketConfig();
            config.setCapacity(100);

            assertEquals(100, config.getCapacity());
        }

        @Test
        @DisplayName("Should correctly store and retrieve refill tokens")
        void bucketConfig_RefillTokens_StoredCorrectly() {
            RateLimitConfig.BucketConfig config = new RateLimitConfig.BucketConfig();
            config.setRefillTokens(50);

            assertEquals(50, config.getRefillTokens());
        }

        @Test
        @DisplayName("Should correctly store and retrieve refill duration")
        void bucketConfig_RefillDuration_StoredCorrectly() {
            RateLimitConfig.BucketConfig config = new RateLimitConfig.BucketConfig();
            config.setRefillDurationSeconds(120);

            assertEquals(120, config.getRefillDurationSeconds());
        }
    }

    @Nested
    @DisplayName("Auth Limit (Identifier-Based) Tests")
    class AuthLimitIdentifierTests {

        @Test
        @DisplayName("Should create separate buckets for different identifiers")
        void checkAuthLimit_DifferentIdentifiers_SeparateBuckets() {
            // Set up auth config
            RateLimitConfig.BucketConfig authConfig = new RateLimitConfig.BucketConfig();
            authConfig.setCapacity(5);
            authConfig.setRefillTokens(5);
            authConfig.setRefillDurationSeconds(60);
            rateLimitConfig.setAuth(authConfig);

            String ipIdentifier = "ip:203.0.113.1";
            String deviceIdentifier = "device:abc-123";

            // Exhaust IP identifier bucket
            for (int i = 0; i < 5; i++) {
                rateLimitConfig.checkAuthLimit(ipIdentifier);
            }

            // Device identifier should still work (separate bucket)
            assertDoesNotThrow(() -> rateLimitConfig.checkAuthLimit(deviceIdentifier));

            // IP identifier should still be exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkAuthLimit(ipIdentifier));
        }

        @Test
        @DisplayName("Should isolate buckets between ip and device prefixes")
        void checkAuthLimit_IpVsDevice_Isolated() {
            RateLimitConfig.BucketConfig authConfig = new RateLimitConfig.BucketConfig();
            authConfig.setCapacity(3);
            authConfig.setRefillTokens(3);
            authConfig.setRefillDurationSeconds(60);
            rateLimitConfig.setAuth(authConfig);

            // Different identifiers should not collide
            rateLimitConfig.checkAuthLimit("ip:192.168.1.1");
            rateLimitConfig.checkAuthLimit("ip:192.168.1.1");
            rateLimitConfig.checkAuthLimit("ip:192.168.1.1");

            // ip:192.168.1.1 exhausted
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkAuthLimit("ip:192.168.1.1"));

            // device:192.168.1.1 should have separate bucket
            assertDoesNotThrow(() -> rateLimitConfig.checkAuthLimit("device:192.168.1.1"));
        }

        @Test
        @DisplayName("Should use unified bucket key format identifier:auth")
        void checkAuthLimit_BucketKeyFormat_IdentifierColonAuth() {
            RateLimitConfig.BucketConfig authConfig = new RateLimitConfig.BucketConfig();
            authConfig.setCapacity(2);
            authConfig.setRefillTokens(2);
            authConfig.setRefillDurationSeconds(60);
            rateLimitConfig.setAuth(authConfig);

            String identifier = "device:test-device";

            // Use 2 tokens
            rateLimitConfig.checkAuthLimit(identifier);
            rateLimitConfig.checkAuthLimit(identifier);

            // 3rd should fail
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkAuthLimit(identifier));
        }

        @Test
        @DisplayName("Should handle unknown device identifier")
        void checkAuthLimit_UnknownDevice_Works() {
            RateLimitConfig.BucketConfig authConfig = new RateLimitConfig.BucketConfig();
            authConfig.setCapacity(1);
            authConfig.setRefillTokens(1);
            authConfig.setRefillDurationSeconds(60);
            rateLimitConfig.setAuth(authConfig);

            String unknownIdentifier = "device:unknown";

            // Should work for first request
            assertDoesNotThrow(() -> rateLimitConfig.checkAuthLimit(unknownIdentifier));

            // Should fail for second (bucket exhausted)
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkAuthLimit(unknownIdentifier));
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle single capacity bucket correctly")
        void checkRateLimit_SingleCapacity_ExhaustsAfterOne() {
            RateLimitConfig.BucketConfig singleConfig = new RateLimitConfig.BucketConfig();
            singleConfig.setCapacity(1);
            singleConfig.setRefillTokens(1);
            singleConfig.setRefillDurationSeconds(60);

            Long userId = 1L;
            String endpoint = "single-test";

            // First request should succeed
            assertDoesNotThrow(() -> rateLimitConfig.checkRateLimit(userId, endpoint, singleConfig));

            // Second request should fail
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(userId, endpoint, singleConfig));
        }

        @Test
        @DisplayName("Should handle large capacity bucket correctly")
        void checkRateLimit_LargeCapacity_HandlesCorrectly() {
            RateLimitConfig.BucketConfig largeConfig = new RateLimitConfig.BucketConfig();
            largeConfig.setCapacity(1000);
            largeConfig.setRefillTokens(1000);
            largeConfig.setRefillDurationSeconds(60);

            Long userId = 1L;
            String endpoint = "large-test";

            // Should allow 1000 requests
            for (int i = 0; i < 1000; i++) {
                rateLimitConfig.checkRateLimit(userId, endpoint, largeConfig);
            }

            // 1001st should fail
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(userId, endpoint, largeConfig));
        }

        @Test
        @DisplayName("Should reuse bucket for same user-endpoint combination")
        void checkRateLimit_SameKey_ReusesBucket() {
            Long userId = 1L;
            String endpoint = "reuse-test";

            // Use 3 tokens
            for (int i = 0; i < 3; i++) {
                rateLimitConfig.checkRateLimit(userId, endpoint, testBucketConfig);
            }

            // Use 2 more tokens (should work, total 5)
            for (int i = 0; i < 2; i++) {
                assertDoesNotThrow(() -> rateLimitConfig.checkRateLimit(userId, endpoint, testBucketConfig));
            }

            // 6th should fail
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimitConfig.checkRateLimit(userId, endpoint, testBucketConfig));
        }
    }
}
