package org.danteplanner.backend.service.token;

import org.danteplanner.backend.config.JwtProperties;
import org.danteplanner.backend.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenService.
 *
 * <p>Tests JWT token generation and validation including
 * claims extraction, expiration handling, and signature verification.</p>
 */
class JwtTokenServiceTest {

    private static final String TEST_SECRET = "test-secret-key-for-testing-purposes-only-min-256-bits-required";
    private static final Long ACCESS_TOKEN_EXPIRY = 900000L; // 15 minutes
    private static final Long REFRESH_TOKEN_EXPIRY = 604800000L; // 7 days

    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_SECRET);
        jwtProperties.setAccessTokenExpiry(ACCESS_TOKEN_EXPIRY);
        jwtProperties.setRefreshTokenExpiry(REFRESH_TOKEN_EXPIRY);

        tokenService = new JwtTokenService(jwtProperties);
    }

    @Nested
    @DisplayName("generateAccessToken Tests")
    class GenerateAccessTokenTests {

        @Test
        @DisplayName("Should contain correct claims")
        void generateAccessToken_containsCorrectClaims() {
            // Arrange
            Long userId = 123L;
            String email = "test@example.com";

            // Act
            String token = tokenService.generateAccessToken(userId, email);

            // Assert
            assertNotNull(token);
            TokenClaims claims = tokenService.validateToken(token);
            assertEquals(userId, claims.userId());
            assertEquals(email, claims.email());
            assertEquals(TokenClaims.TYPE_ACCESS, claims.type());
            assertTrue(claims.isAccessToken());
            assertFalse(claims.isRefreshToken());
        }

        @Test
        @DisplayName("Should have correct expiration time")
        void generateAccessToken_hasCorrectExpiration() {
            // Arrange
            Long userId = 123L;
            String email = "test@example.com";

            // Act
            String token = tokenService.generateAccessToken(userId, email);

            // Assert - expiration should be in the future
            TokenClaims claims = tokenService.validateToken(token);
            assertFalse(claims.isExpired(), "Token should not be expired immediately after creation");

            // Verify expiration is approximately ACCESS_TOKEN_EXPIRY from now
            long now = System.currentTimeMillis();
            long expirationTime = claims.expiration().getTime();
            long timeDiff = expirationTime - now;

            // Allow 5 second tolerance for timing variations
            long tolerance = 5000;
            assertTrue(timeDiff >= ACCESS_TOKEN_EXPIRY - tolerance,
                    "Expiration should be at least " + (ACCESS_TOKEN_EXPIRY - tolerance) + "ms from now (got " + timeDiff + ")");
            assertTrue(timeDiff <= ACCESS_TOKEN_EXPIRY + tolerance,
                    "Expiration should be at most " + (ACCESS_TOKEN_EXPIRY + tolerance) + "ms from now (got " + timeDiff + ")");
        }

        @Test
        @DisplayName("Should set issuedAt timestamp")
        void generateAccessToken_setsIssuedAt() {
            // Arrange
            Long userId = 123L;
            String email = "test@example.com";
            long beforeGeneration = System.currentTimeMillis();

            // Act
            String token = tokenService.generateAccessToken(userId, email);
            long afterGeneration = System.currentTimeMillis();

            // Assert
            TokenClaims claims = tokenService.validateToken(token);
            long issuedAt = claims.issuedAt().getTime();
            // Allow 100ms tolerance for timing variations (JWT truncates to seconds)
            long tolerance = 1100;
            assertTrue(issuedAt >= beforeGeneration - tolerance && issuedAt <= afterGeneration + tolerance,
                    "issuedAt (" + issuedAt + ") should be between " + (beforeGeneration - tolerance) + " and " + (afterGeneration + tolerance));
        }
    }

    @Nested
    @DisplayName("generateRefreshToken Tests")
    class GenerateRefreshTokenTests {

        @Test
        @DisplayName("Should contain correct claims")
        void generateRefreshToken_containsCorrectClaims() {
            // Arrange
            Long userId = 456L;
            String email = "refresh@example.com";

            // Act
            String token = tokenService.generateRefreshToken(userId, email);

            // Assert
            assertNotNull(token);
            TokenClaims claims = tokenService.validateToken(token);
            assertEquals(userId, claims.userId());
            assertEquals(email, claims.email());
            assertEquals(TokenClaims.TYPE_REFRESH, claims.type());
            assertTrue(claims.isRefreshToken());
            assertFalse(claims.isAccessToken());
        }

        @Test
        @DisplayName("Should have longer expiration than access token")
        void generateRefreshToken_hasLongerExpiration() {
            // Arrange
            Long userId = 123L;
            String email = "test@example.com";

            // Act
            String accessToken = tokenService.generateAccessToken(userId, email);
            String refreshToken = tokenService.generateRefreshToken(userId, email);

            // Assert
            TokenClaims accessClaims = tokenService.validateToken(accessToken);
            TokenClaims refreshClaims = tokenService.validateToken(refreshToken);

            assertTrue(refreshClaims.expiration().after(accessClaims.expiration()),
                    "Refresh token should expire after access token");
        }
    }

    @Nested
    @DisplayName("validateToken Tests")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return claims for valid token")
        void validateToken_returnsClaimsForValidToken() {
            // Arrange
            Long userId = 789L;
            String email = "valid@example.com";
            String token = tokenService.generateAccessToken(userId, email);

            // Act
            TokenClaims claims = tokenService.validateToken(token);

            // Assert
            assertNotNull(claims);
            assertEquals(userId, claims.userId());
            assertEquals(email, claims.email());
            assertNotNull(claims.issuedAt());
            assertNotNull(claims.expiration());
        }

        @Test
        @DisplayName("Should throw for expired token")
        void validateToken_throwsForExpiredToken() {
            // Arrange - create service with very short expiry
            JwtProperties shortExpiryProps = new JwtProperties();
            shortExpiryProps.setSecret(TEST_SECRET);
            shortExpiryProps.setAccessTokenExpiry(1L); // 1ms expiry
            shortExpiryProps.setRefreshTokenExpiry(1L);
            JwtTokenService shortExpiryService = new JwtTokenService(shortExpiryProps);

            String token = shortExpiryService.generateAccessToken(123L, "expired@example.com");

            // Wait for token to expire
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> shortExpiryService.validateToken(token)
            );
            assertEquals(InvalidTokenException.Reason.EXPIRED, exception.getReason());
        }

        @Test
        @DisplayName("Should throw for invalid signature")
        void validateToken_throwsForInvalidSignature() {
            // Arrange - create token with different secret
            JwtProperties differentSecretProps = new JwtProperties();
            differentSecretProps.setSecret("different-secret-key-that-is-at-least-32-characters-long");
            differentSecretProps.setAccessTokenExpiry(ACCESS_TOKEN_EXPIRY);
            differentSecretProps.setRefreshTokenExpiry(REFRESH_TOKEN_EXPIRY);
            JwtTokenService differentService = new JwtTokenService(differentSecretProps);

            String tokenWithDifferentSignature = differentService.generateAccessToken(123L, "test@example.com");

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> tokenService.validateToken(tokenWithDifferentSignature)
            );
            assertEquals(InvalidTokenException.Reason.INVALID_SIGNATURE, exception.getReason());
        }

        @Test
        @DisplayName("Should throw for malformed token")
        void validateToken_throwsForMalformedToken() {
            // Arrange
            String malformedToken = "not.a.valid.jwt.token";

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> tokenService.validateToken(malformedToken)
            );
            assertEquals(InvalidTokenException.Reason.MALFORMED, exception.getReason());
        }

        @Test
        @DisplayName("Should throw for completely invalid token")
        void validateToken_throwsForInvalidToken() {
            // Arrange
            String invalidToken = "garbage-data";

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> tokenService.validateToken(invalidToken)
            );
            assertEquals(InvalidTokenException.Reason.MALFORMED, exception.getReason());
        }
    }

    @Nested
    @DisplayName("getUserIdFromToken Tests")
    class GetUserIdFromTokenTests {

        @Test
        @DisplayName("Should return userId from valid token")
        void getUserIdFromToken_returnsUserId() {
            // Arrange
            Long expectedUserId = 42L;
            String token = tokenService.generateAccessToken(expectedUserId, "user@example.com");

            // Act
            Long actualUserId = tokenService.getUserIdFromToken(token);

            // Assert
            assertEquals(expectedUserId, actualUserId);
        }

        @Test
        @DisplayName("Should throw for invalid token")
        void getUserIdFromToken_throwsForInvalidToken() {
            // Arrange
            String invalidToken = "invalid.token";

            // Act & Assert
            assertThrows(
                    InvalidTokenException.class,
                    () -> tokenService.getUserIdFromToken(invalidToken)
            );
        }
    }

    @Nested
    @DisplayName("isTokenExpired Tests")
    class IsTokenExpiredTests {

        @Test
        @DisplayName("Should return false for valid non-expired token")
        void isTokenExpired_returnsFalseForValidToken() {
            // Arrange
            String token = tokenService.generateAccessToken(123L, "test@example.com");

            // Act
            boolean expired = tokenService.isTokenExpired(token);

            // Assert
            assertFalse(expired);
        }

        @Test
        @DisplayName("Should return true for expired token")
        void isTokenExpired_returnsTrueForExpiredToken() {
            // Arrange - create service with very short expiry
            JwtProperties shortExpiryProps = new JwtProperties();
            shortExpiryProps.setSecret(TEST_SECRET);
            shortExpiryProps.setAccessTokenExpiry(1L);
            shortExpiryProps.setRefreshTokenExpiry(1L);
            JwtTokenService shortExpiryService = new JwtTokenService(shortExpiryProps);

            String token = shortExpiryService.generateAccessToken(123L, "test@example.com");

            // Wait for expiration
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Act
            boolean expired = shortExpiryService.isTokenExpired(token);

            // Assert
            assertTrue(expired);
        }

        @Test
        @DisplayName("Should return true for invalid token")
        void isTokenExpired_returnsTrueForInvalidToken() {
            // Arrange
            String invalidToken = "invalid.jwt.token";

            // Act
            boolean expired = tokenService.isTokenExpired(invalidToken);

            // Assert
            assertTrue(expired);
        }
    }

    @Nested
    @DisplayName("getEmailFromToken Tests")
    class GetEmailFromTokenTests {

        @Test
        @DisplayName("Should return email from valid token")
        void getEmailFromToken_returnsEmail() {
            // Arrange
            String expectedEmail = "email@example.com";
            String token = tokenService.generateAccessToken(123L, expectedEmail);

            // Act
            String actualEmail = tokenService.getEmailFromToken(token);

            // Assert
            assertEquals(expectedEmail, actualEmail);
        }
    }

    @Nested
    @DisplayName("getTokenType Tests")
    class GetTokenTypeTests {

        @Test
        @DisplayName("Should return 'access' for access token")
        void getTokenType_returnsAccessForAccessToken() {
            // Arrange
            String token = tokenService.generateAccessToken(123L, "test@example.com");

            // Act
            String type = tokenService.getTokenType(token);

            // Assert
            assertEquals(TokenClaims.TYPE_ACCESS, type);
        }

        @Test
        @DisplayName("Should return 'refresh' for refresh token")
        void getTokenType_returnsRefreshForRefreshToken() {
            // Arrange
            String token = tokenService.generateRefreshToken(123L, "test@example.com");

            // Act
            String type = tokenService.getTokenType(token);

            // Assert
            assertEquals(TokenClaims.TYPE_REFRESH, type);
        }
    }
}
