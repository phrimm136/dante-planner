package org.danteplanner.backend.service.token;
import org.danteplanner.backend.support.AuthReads;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenStoreKeys;
import org.danteplanner.backend.support.TestDataFactory;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.redis.testcontainers.RedisContainer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TokenBlacklistService.
 *
 * <p>Tests the Redis-backed token blacklist behavior including
 * add, check, and TTL expiration functionality over a live Redis container.</p>
 *
 * <p>The container is this class's own, not the run's shared one: the setup empties and the tests count
 * the blacklist and invalidation keys through {@link TokenStoreKeys}, which spans the whole keyspace, so
 * there is no id to narrow them to.</p>
 */
@Tag("containerized")
class TokenBlacklistServiceIT {

    private static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

    private static StringRedisTemplate ownTemplate;

    private TokenBlacklistService blacklistService;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        ownTemplate = buildTemplate(REDIS.getRedisHost(), REDIS.getRedisPort());
    }

    @AfterAll
    static void stopRedis() {
        ((LettuceConnectionFactory) ownTemplate.getRequiredConnectionFactory()).destroy();
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
        blacklistService = new TokenBlacklistService(AuthReads.writesTo(ownTemplate), AuthReads.from(ownTemplate), new SimpleMeterRegistry(),
                Clock.systemUTC(), TokenBlacklistService.DEFAULT_REFRESH_TOKEN_EXPIRY_MS);
        TokenStoreKeys.deleteBlacklistAndInvalidations(ownTemplate);
    }

    @Nested
    class BlacklistTokenTests {

        @Test
        void blacklistToken_WhenCalled_AddsTokenToBlacklist() {
            // Arrange
            String token = "test.jwt.token";
            Date expiry = new Date(System.currentTimeMillis() + 60000); // 1 minute from now

            // Act
            blacklistService.blacklistToken(token, expiry);

            // Assert
            assertThat(blacklistService.isBlacklisted(token)).isTrue();
            assertThat(TokenStoreKeys.blacklistSize(ownTemplate)).isEqualTo(1);
        }

        @Test
        void blacklistToken_WhenSameToken_Overwrites() {
            // Arrange
            String token = "test.jwt.token";
            Date expiry1 = new Date(System.currentTimeMillis() + 60000);
            Date expiry2 = new Date(System.currentTimeMillis() + 120000);

            // Act
            blacklistService.blacklistToken(token, expiry1);
            blacklistService.blacklistToken(token, expiry2);

            // Assert - token still blacklisted, size remains 1
            assertThat(blacklistService.isBlacklisted(token)).isTrue();
            assertThat(TokenStoreKeys.blacklistSize(ownTemplate)).isEqualTo(1);
        }
    }

    @Nested
    class IsBlacklistedTests {

        @Test
        void isBlacklisted_WhenBlacklisted_ReturnsTrue() {
            // Arrange
            String token = "blacklisted.jwt.token";
            Date expiry = new Date(System.currentTimeMillis() + 60000);
            blacklistService.blacklistToken(token, expiry);

            // Act
            boolean result = blacklistService.isBlacklisted(token);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void isBlacklisted_WhenNotBlacklisted_ReturnsFalse() {
            // Arrange
            String unknownToken = "unknown.jwt.token";

            // Act
            boolean result = blacklistService.isBlacklisted(unknownToken);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        void isBlacklisted_WhenEntryExpired_ReturnsFalse() {
            // Arrange - token with expiry in the past
            String token = "expired.jwt.token";
            Date expiry = new Date(System.currentTimeMillis() - 1000); // 1 second ago
            blacklistService.blacklistToken(token, expiry);

            // Act
            boolean result = blacklistService.isBlacklisted(token);

            // Assert - should return false and remove entry (lazy cleanup)
            assertThat(result).isFalse();
            assertThat(TokenStoreKeys.blacklistSize(ownTemplate)).isEqualTo(0); // Entry should be removed
        }
    }

    @Nested
    class UtilityMethodsTests {

        @Test
        void size_WhenEntriesExist_ReturnsCorrectCount() {
            // Arrange
            Date expiry = new Date(System.currentTimeMillis() + 60000);

            // Assert initial
            assertThat(TokenStoreKeys.blacklistSize(ownTemplate)).isEqualTo(0);

            // Add tokens
            blacklistService.blacklistToken("token1", expiry);
            assertThat(TokenStoreKeys.blacklistSize(ownTemplate)).isEqualTo(1);

            blacklistService.blacklistToken("token2", expiry);
            assertThat(TokenStoreKeys.blacklistSize(ownTemplate)).isEqualTo(2);
        }
    }

    @Nested
    class ImmediateBlacklistTests {

        @Test
        void blacklistToken_WhenBlacklisted_RejectedImmediately() {
            // Arrange
            String token = "logout.refresh.token";
            Date expiry = new Date(System.currentTimeMillis() + 60000);

            // Act - blacklist immediately (logout)
            blacklistService.blacklistToken(token, expiry);

            // Assert - should be blacklisted immediately, no grace period
            assertThat(blacklistService.isBlacklisted(token)).isTrue();
        }

    }

    @Nested
    class TokenHashingTests {

        @Test
        void blacklistToken_WhenDifferentTokens_DoNotCollide() {
            // Arrange
            Date expiry = new Date(System.currentTimeMillis() + 60000);
            String token1 = "token.one.value";
            String token2 = "token.two.value";

            // Act
            blacklistService.blacklistToken(token1, expiry);

            // Assert
            assertThat(blacklistService.isBlacklisted(token1)).isTrue();
            assertThat(blacklistService.isBlacklisted(token2)).isFalse();
        }

        @Test
        void isBlacklisted_WhenSameToken_AlwaysMatches() {
            // Arrange
            Date expiry = new Date(System.currentTimeMillis() + 60000);
            String token = "consistent.token.value";

            // Act
            blacklistService.blacklistToken(token, expiry);

            // Assert - check multiple times to verify consistent hashing
            assertThat(blacklistService.isBlacklisted(token)).isTrue();
            assertThat(blacklistService.isBlacklisted(token)).isTrue();
            assertThat(blacklistService.isBlacklisted(token)).isTrue();
        }
    }

    @Nested
    class CrossInstanceTests {

        @Test
        void blacklistToken_WhenCalled_IsVisibleToASecondServiceOverTheSameRedis() {
            // Arrange
            String token = "cross.instance.token";
            Date futureExpiry = new Date(System.currentTimeMillis() + 60000);
            blacklistService.blacklistToken(token, futureExpiry);

            // Act - a second service over a fresh template pointing at the SAME container
            StringRedisTemplate secondTemplate = buildTemplate(REDIS.getRedisHost(), REDIS.getRedisPort());
            TokenBlacklistService secondService = new TokenBlacklistService(AuthReads.writesTo(secondTemplate), AuthReads.from(secondTemplate), new SimpleMeterRegistry(),
                    Clock.systemUTC(), TokenBlacklistService.DEFAULT_REFRESH_TOKEN_EXPIRY_MS);

            // Assert - externalized revocation is visible across instances
            assertThat(secondService.isBlacklisted(token)).isTrue();
        }
    }

    @Nested
    class UserInvalidationRedisTests {

        @Test
        void invalidateUserTokens_WhenCalled_IsVisibleToASecondServiceOverTheSameRedis() {
            // Arrange
            Long userId = TestDataFactory.nextUserId();
            blacklistService.invalidateUserTokens(userId);

            // Act - a second service over a fresh template pointing at the SAME container
            StringRedisTemplate secondTemplate = buildTemplate(REDIS.getRedisHost(), REDIS.getRedisPort());
            TokenBlacklistService secondService = new TokenBlacklistService(AuthReads.writesTo(secondTemplate), AuthReads.from(secondTemplate), new SimpleMeterRegistry(),
                    Clock.systemUTC(), TokenBlacklistService.DEFAULT_REFRESH_TOKEN_EXPIRY_MS);

            // Assert - externalized user invalidation is visible across instances
            assertThat(secondService.isUserTokenInvalidated(userId, 0L)).isTrue();
        }

        @Test
        void invalidateUserTokens_WhenCalled_WritesOneInvalidation() {
            Long userId = TestDataFactory.nextUserId();

            blacklistService.invalidateUserTokens(userId);

            assertThat(TokenStoreKeys.userInvalidationSize(ownTemplate)).isEqualTo(1);
        }

        @Test
        void clearUserInvalidation_WhenCalled_RemovesInvalidation() {
            // Arrange
            Long userId = TestDataFactory.nextUserId();
            blacklistService.invalidateUserTokens(userId);

            // Act
            blacklistService.clearUserInvalidation(userId);

            // Assert
            assertThat(blacklistService.isUserTokenInvalidated(userId, 0L)).isFalse();
            assertThat(TokenStoreKeys.userInvalidationSize(ownTemplate)).isEqualTo(0);
        }
    }
}
