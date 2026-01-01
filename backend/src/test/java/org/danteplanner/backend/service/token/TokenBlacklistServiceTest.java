package org.danteplanner.backend.service.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenBlacklistService.
 *
 * <p>Tests in-memory token blacklist behavior including
 * add, check, and TTL expiration functionality.</p>
 */
class TokenBlacklistServiceTest {

    private TokenBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        blacklistService = new TokenBlacklistService();
    }

    @Nested
    @DisplayName("blacklistToken Tests")
    class BlacklistTokenTests {

        @Test
        @DisplayName("Should add token to blacklist")
        void blacklistToken_addsTokenToBlacklist() {
            // Arrange
            String token = "test.jwt.token";
            Date expiry = new Date(System.currentTimeMillis() + 60000); // 1 minute from now

            // Act
            blacklistService.blacklistToken(token, expiry);

            // Assert
            assertTrue(blacklistService.isBlacklisted(token));
            assertEquals(1, blacklistService.size());
        }

        @Test
        @DisplayName("Should handle null token gracefully")
        void blacklistToken_nullToken_doesNothing() {
            // Arrange
            Date expiry = new Date(System.currentTimeMillis() + 60000);

            // Act
            blacklistService.blacklistToken(null, expiry);

            // Assert
            assertEquals(0, blacklistService.size());
        }

        @Test
        @DisplayName("Should handle null expiry gracefully")
        void blacklistToken_nullExpiry_doesNothing() {
            // Arrange
            String token = "test.jwt.token";

            // Act
            blacklistService.blacklistToken(token, null);

            // Assert
            assertEquals(0, blacklistService.size());
        }

        @Test
        @DisplayName("Should overwrite existing entry for same token")
        void blacklistToken_sameToken_overwrites() {
            // Arrange
            String token = "test.jwt.token";
            Date expiry1 = new Date(System.currentTimeMillis() + 60000);
            Date expiry2 = new Date(System.currentTimeMillis() + 120000);

            // Act
            blacklistService.blacklistToken(token, expiry1);
            blacklistService.blacklistToken(token, expiry2);

            // Assert - token still blacklisted, size remains 1
            assertTrue(blacklistService.isBlacklisted(token));
            assertEquals(1, blacklistService.size());
        }
    }

    @Nested
    @DisplayName("isBlacklisted Tests")
    class IsBlacklistedTests {

        @Test
        @DisplayName("Should return true for blacklisted token")
        void isBlacklisted_returnsTrueForBlacklistedToken() {
            // Arrange
            String token = "blacklisted.jwt.token";
            Date expiry = new Date(System.currentTimeMillis() + 60000);
            blacklistService.blacklistToken(token, expiry);

            // Act
            boolean result = blacklistService.isBlacklisted(token);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false for non-blacklisted token")
        void isBlacklisted_returnsFalseForNonBlacklistedToken() {
            // Arrange
            String unknownToken = "unknown.jwt.token";

            // Act
            boolean result = blacklistService.isBlacklisted(unknownToken);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false for expired blacklist entry (TTL cleanup)")
        void isBlacklisted_returnsFalseForExpiredBlacklistEntry() {
            // Arrange - token with expiry in the past
            String token = "expired.jwt.token";
            Date expiry = new Date(System.currentTimeMillis() - 1000); // 1 second ago
            blacklistService.blacklistToken(token, expiry);

            // Act
            boolean result = blacklistService.isBlacklisted(token);

            // Assert - should return false and remove entry (lazy cleanup)
            assertFalse(result);
            assertEquals(0, blacklistService.size()); // Entry should be removed
        }

        @Test
        @DisplayName("Should return false for null token")
        void isBlacklisted_nullToken_returnsFalse() {
            // Act
            boolean result = blacklistService.isBlacklisted(null);

            // Assert
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Utility Methods Tests")
    class UtilityMethodsTests {

        @Test
        @DisplayName("clear() should remove all entries")
        void clear_removesAllEntries() {
            // Arrange
            Date expiry = new Date(System.currentTimeMillis() + 60000);
            blacklistService.blacklistToken("token1", expiry);
            blacklistService.blacklistToken("token2", expiry);
            blacklistService.blacklistToken("token3", expiry);
            assertEquals(3, blacklistService.size());

            // Act
            blacklistService.clear();

            // Assert
            assertEquals(0, blacklistService.size());
            assertFalse(blacklistService.isBlacklisted("token1"));
        }

        @Test
        @DisplayName("cleanupExpired() should remove all expired entries")
        void cleanupExpired_removesExpiredEntries() {
            // Arrange
            Date pastExpiry = new Date(System.currentTimeMillis() - 1000);
            Date futureExpiry = new Date(System.currentTimeMillis() + 60000);
            blacklistService.blacklistToken("expired1", pastExpiry);
            blacklistService.blacklistToken("expired2", pastExpiry);
            blacklistService.blacklistToken("valid", futureExpiry);

            // Act
            blacklistService.cleanupExpired();

            // Assert
            assertEquals(1, blacklistService.size());
            assertTrue(blacklistService.isBlacklisted("valid"));
            assertFalse(blacklistService.isBlacklisted("expired1"));
            assertFalse(blacklistService.isBlacklisted("expired2"));
        }

        @Test
        @DisplayName("size() should return correct count")
        void size_returnsCorrectCount() {
            // Arrange
            Date expiry = new Date(System.currentTimeMillis() + 60000);

            // Assert initial
            assertEquals(0, blacklistService.size());

            // Add tokens
            blacklistService.blacklistToken("token1", expiry);
            assertEquals(1, blacklistService.size());

            blacklistService.blacklistToken("token2", expiry);
            assertEquals(2, blacklistService.size());
        }
    }

    @Nested
    @DisplayName("Token Hashing Tests")
    class TokenHashingTests {

        @Test
        @DisplayName("Different tokens should not collide")
        void differentTokens_doNotCollide() {
            // Arrange
            Date expiry = new Date(System.currentTimeMillis() + 60000);
            String token1 = "token.one.value";
            String token2 = "token.two.value";

            // Act
            blacklistService.blacklistToken(token1, expiry);

            // Assert
            assertTrue(blacklistService.isBlacklisted(token1));
            assertFalse(blacklistService.isBlacklisted(token2));
        }

        @Test
        @DisplayName("Same token value should always match")
        void sameToken_alwaysMatches() {
            // Arrange
            Date expiry = new Date(System.currentTimeMillis() + 60000);
            String token = "consistent.token.value";

            // Act
            blacklistService.blacklistToken(token, expiry);

            // Assert - check multiple times to verify consistent hashing
            assertTrue(blacklistService.isBlacklisted(token));
            assertTrue(blacklistService.isBlacklisted(token));
            assertTrue(blacklistService.isBlacklisted(token));
        }
    }
}
