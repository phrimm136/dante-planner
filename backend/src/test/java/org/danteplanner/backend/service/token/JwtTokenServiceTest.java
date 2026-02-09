package org.danteplanner.backend.service.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.config.JwtProperties;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenService.
 *
 * <p>Tests JWT token generation and validation including
 * claims extraction, expiration handling, and signature verification.</p>
 */
class JwtTokenServiceTest {

    private static final Long ACCESS_TOKEN_EXPIRY = 900000L; // 15 minutes
    private static final Long REFRESH_TOKEN_EXPIRY = 604800000L; // 7 days

    private JwtTokenService tokenService;
    private JwtProperties jwtProperties;
    private KeyPair testKeyPair;
    private byte[] testAesKey;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        // Generate RSA keypair for testing
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        testKeyPair = keyPairGenerator.generateKeyPair();

        // Generate AES-256 key for testing
        testAesKey = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(testAesKey);

        // Setup JWT properties
        jwtProperties = new JwtProperties();
        jwtProperties.setPrivateKey(testKeyPair.getPrivate());
        jwtProperties.setPublicKey(testKeyPair.getPublic());
        jwtProperties.setEncryptionKeyBytes(testAesKey);
        jwtProperties.setAccessTokenExpiry(ACCESS_TOKEN_EXPIRY);
        jwtProperties.setRefreshTokenExpiry(REFRESH_TOKEN_EXPIRY);

        objectMapper = new ObjectMapper();
        tokenService = new JwtTokenService(jwtProperties, objectMapper);
    }

    @Nested
    @DisplayName("generateAccessToken Tests")
    class GenerateAccessTokenTests {

        @Test
        @DisplayName("Should contain correct claims")
        void givenValidInput_whenGenerateAccessToken_thenContainsCorrectClaims() {
            // Arrange
            Long userId = 123L;
            String email = "test@example.com";

            // Act
            String token = tokenService.generateAccessToken(userId, email, UserRole.NORMAL);

            // Assert
            assertNotNull(token);
            TokenClaims claims = tokenService.validateToken(token);
            assertEquals(userId, claims.userId());
            assertEquals(email, claims.email());
            assertEquals(TokenClaims.TYPE_ACCESS, claims.type());
            assertEquals(UserRole.NORMAL, claims.role());
            assertTrue(claims.isAccessToken());
            assertFalse(claims.isRefreshToken());
        }

        @Test
        @DisplayName("Should have correct expiration time")
        void givenValidInput_whenGenerateAccessToken_thenHasCorrectExpiration() {
            // Arrange
            Long userId = 123L;
            String email = "test@example.com";

            // Act
            String token = tokenService.generateAccessToken(userId, email, UserRole.NORMAL);

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
        void givenValidInput_whenGenerateAccessToken_thenSetsIssuedAt() {
            // Arrange
            Long userId = 123L;
            String email = "test@example.com";
            long beforeGeneration = System.currentTimeMillis();

            // Act
            String token = tokenService.generateAccessToken(userId, email, UserRole.NORMAL);
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
        void givenValidInput_whenGenerateRefreshToken_thenContainsCorrectClaims() {
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
        void givenAccessAndRefreshTokens_whenCompareExpiration_thenRefreshLonger() {
            // Arrange
            Long userId = 123L;
            String email = "test@example.com";

            // Act
            String accessToken = tokenService.generateAccessToken(userId, email, UserRole.NORMAL);
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
        void givenValidToken_whenValidate_thenReturnsClaims() {
            // Arrange
            Long userId = 789L;
            String email = "valid@example.com";
            String token = tokenService.generateAccessToken(userId, email, UserRole.MODERATOR);

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
        void givenExpiredToken_whenValidate_thenThrowsInvalidTokenException() {
            // Arrange - create service with very short expiry
            JwtProperties shortExpiryProps = new JwtProperties();
            shortExpiryProps.setPrivateKey(testKeyPair.getPrivate());
            shortExpiryProps.setPublicKey(testKeyPair.getPublic());
            shortExpiryProps.setEncryptionKeyBytes(testAesKey);
            shortExpiryProps.setAccessTokenExpiry(1L); // 1ms expiry
            shortExpiryProps.setRefreshTokenExpiry(1L);
            JwtTokenService shortExpiryService = new JwtTokenService(shortExpiryProps, objectMapper);

            String token = shortExpiryService.generateAccessToken(123L, "expired@example.com", UserRole.NORMAL);

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
        void givenInvalidSignature_whenValidate_thenThrowsInvalidTokenException() throws Exception {
            // Arrange - create token with different RSA keypair
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair differentKeyPair = keyPairGenerator.generateKeyPair();

            JwtProperties differentKeyProps = new JwtProperties();
            differentKeyProps.setPrivateKey(differentKeyPair.getPrivate());
            differentKeyProps.setPublicKey(differentKeyPair.getPublic());
            differentKeyProps.setEncryptionKeyBytes(testAesKey);
            differentKeyProps.setAccessTokenExpiry(ACCESS_TOKEN_EXPIRY);
            differentKeyProps.setRefreshTokenExpiry(REFRESH_TOKEN_EXPIRY);
            JwtTokenService differentService = new JwtTokenService(differentKeyProps, objectMapper);

            String tokenWithDifferentSignature = differentService.generateAccessToken(123L, "test@example.com", UserRole.NORMAL);

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> tokenService.validateToken(tokenWithDifferentSignature)
            );
            assertEquals(InvalidTokenException.Reason.INVALID_SIGNATURE, exception.getReason());
        }

        @Test
        @DisplayName("Should throw for malformed token")
        void givenMalformedToken_whenValidate_thenThrowsInvalidTokenException() {
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
        void givenGarbageData_whenValidate_thenThrowsInvalidTokenException() {
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
        void givenValidToken_whenGetUserId_thenReturnsUserId() {
            // Arrange
            Long expectedUserId = 42L;
            String token = tokenService.generateAccessToken(expectedUserId, "user@example.com", UserRole.NORMAL);

            // Act
            Long actualUserId = tokenService.getUserIdFromToken(token);

            // Assert
            assertEquals(expectedUserId, actualUserId);
        }

        @Test
        @DisplayName("Should throw for invalid token")
        void givenInvalidToken_whenGetUserId_thenThrowsInvalidTokenException() {
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
        void givenValidToken_whenCheckExpired_thenReturnsFalse() {
            // Arrange
            String token = tokenService.generateAccessToken(123L, "test@example.com", UserRole.NORMAL);

            // Act
            boolean expired = tokenService.isTokenExpired(token);

            // Assert
            assertFalse(expired);
        }

        @Test
        @DisplayName("Should return true for expired token")
        void givenExpiredToken_whenCheckExpired_thenReturnsTrue() {
            // Arrange - create service with very short expiry
            JwtProperties shortExpiryProps = new JwtProperties();
            shortExpiryProps.setPrivateKey(testKeyPair.getPrivate());
            shortExpiryProps.setPublicKey(testKeyPair.getPublic());
            shortExpiryProps.setEncryptionKeyBytes(testAesKey);
            shortExpiryProps.setAccessTokenExpiry(1L);
            shortExpiryProps.setRefreshTokenExpiry(1L);
            JwtTokenService shortExpiryService = new JwtTokenService(shortExpiryProps, objectMapper);

            String token = shortExpiryService.generateAccessToken(123L, "test@example.com", UserRole.NORMAL);

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
        void givenInvalidToken_whenCheckExpired_thenReturnsTrue() {
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
        void givenValidToken_whenGetEmail_thenReturnsEmail() {
            // Arrange
            String expectedEmail = "email@example.com";
            String token = tokenService.generateAccessToken(123L, expectedEmail, UserRole.NORMAL);

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
        void givenAccessToken_whenGetType_thenReturnsAccess() {
            // Arrange
            String token = tokenService.generateAccessToken(123L, "test@example.com", UserRole.ADMIN);

            // Act
            String type = tokenService.getTokenType(token);

            // Assert
            assertEquals(TokenClaims.TYPE_ACCESS, type);
        }

        @Test
        @DisplayName("Should return 'refresh' for refresh token")
        void givenRefreshToken_whenGetType_thenReturnsRefresh() {
            // Arrange
            String token = tokenService.generateRefreshToken(123L, "test@example.com");

            // Act
            String type = tokenService.getTokenType(token);

            // Assert
            assertEquals(TokenClaims.TYPE_REFRESH, type);
        }
    }

    @Nested
    @DisplayName("Encryption Security Tests")
    class EncryptionSecurityTests {

        @Test
        @DisplayName("Should not expose userId in JWT payload (only encrypted)")
        void givenGeneratedToken_whenInspectPayload_thenClaimsEncrypted() {
            // Arrange
            String token = tokenService.generateAccessToken(12345L, "secret@example.com", UserRole.ADMIN);

            // Act - extract middle segment (payload) and decode
            String[] parts = token.split("\\.");
            assertEquals(3, parts.length, "JWT should have 3 parts");

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

            // Assert - payload should contain "enc" field but NOT custom claims in plaintext
            // Note: "sub" (email) is outside encryption per design (JJWT validates exp before decryption)
            assertTrue(payload.contains("enc"), "Payload should contain encrypted claims field");
            assertTrue(payload.contains("sub"), "Payload should contain subject (email) - required for JJWT exp validation");
            assertFalse(payload.contains("12345"), "Payload should not contain userId in plaintext");
            assertFalse(payload.contains("userId"), "Payload should not contain userId field name in plaintext");
            assertFalse(payload.contains("ADMIN"), "Payload should not contain role in plaintext");
        }

        @Test
        @DisplayName("Should generate unique IVs for identical claims")
        void givenIdenticalClaims_whenGenerate100Times_thenAllTokensDistinct() {
            // Arrange
            Set<String> encryptedPayloads = new HashSet<>();
            Long userId = 999L;
            String email = "test@example.com";

            // Act - generate 100 tokens with identical claims
            for (int i = 0; i < 100; i++) {
                String token = tokenService.generateAccessToken(userId, email, UserRole.NORMAL);
                String[] parts = token.split("\\.");
                String payload = parts[1]; // Base64-encoded payload
                encryptedPayloads.add(payload);
            }

            // Assert - all 100 payloads should be unique (different IVs)
            assertEquals(100, encryptedPayloads.size(),
                    "All 100 tokens should have distinct encrypted payloads due to unique IVs");
        }
    }
}
