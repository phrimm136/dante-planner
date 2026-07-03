package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenService.
 *
 * <p>Tests JWT token generation and validation including
 * claims extraction, expiration handling, and signature verification.
 * Tokens carry minimized cleartext claims with {@code sub}=userId; no
 * email and no encrypted claim blob.</p>
 */
class JwtTokenServiceTest {

    private static final Long ACCESS_TOKEN_EXPIRY = 900000L; // 15 minutes
    private static final Long REFRESH_TOKEN_EXPIRY = 604800000L; // 7 days

    private JwtTokenService tokenService;
    private JwtProperties jwtProperties;
    private KeyPair testKeyPair;
    private byte[] testAesKey;

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

        tokenService = new JwtTokenService(jwtProperties);
    }

    private static String decodePayload(String token) {
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts");
        return new String(Base64.getUrlDecoder().decode(parts[1]));
    }

    @Nested
    @DisplayName("generateAccessToken Tests")
    class GenerateAccessTokenTests {

        @Test
        @DisplayName("Should contain correct claims")
        void givenValidInput_whenGenerateAccessToken_thenContainsCorrectClaims() {
            // Arrange
            Long userId = 123L;

            // Act
            String token = tokenService.generateAccessToken(userId, UserRole.NORMAL);

            // Assert
            assertNotNull(token);
            TokenClaims claims = tokenService.validateToken(token);
            assertEquals(userId, claims.userId());
            assertNull(claims.email());
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

            // Act
            String token = tokenService.generateAccessToken(userId, UserRole.NORMAL);

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
            long beforeGeneration = System.currentTimeMillis();

            // Act
            String token = tokenService.generateAccessToken(userId, UserRole.NORMAL);
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

            // Act
            String token = tokenService.generateRefreshToken(userId);

            // Assert
            assertNotNull(token);
            TokenClaims claims = tokenService.validateToken(token);
            assertEquals(userId, claims.userId());
            assertNull(claims.email());
            assertEquals(TokenClaims.TYPE_REFRESH, claims.type());
            assertTrue(claims.isRefreshToken());
            assertFalse(claims.isAccessToken());
        }

        @Test
        @DisplayName("Should have longer expiration than access token")
        void givenAccessAndRefreshTokens_whenCompareExpiration_thenRefreshLonger() {
            // Arrange
            Long userId = 123L;

            // Act
            String accessToken = tokenService.generateAccessToken(userId, UserRole.NORMAL);
            String refreshToken = tokenService.generateRefreshToken(userId);

            // Assert
            TokenClaims accessClaims = tokenService.validateToken(accessToken);
            TokenClaims refreshClaims = tokenService.validateToken(refreshToken);

            assertTrue(refreshClaims.expiration().after(accessClaims.expiration()),
                    "Refresh token should expire after access token");
        }
    }

    @Nested
    @DisplayName("Lineage Claims Tests")
    class LineageClaimsTests {

        @Test
        @DisplayName("Refresh token via overload carries jti, family_id, parent_jti")
        void givenFamilyAndParent_whenGenerateRefreshToken_thenCarriesAllLineageClaims() {
            Long userId = 111L;
            String familyId = "fam-123";
            String parentJti = "parent-jti-456";

            String token = tokenService.generateRefreshToken(userId, familyId, parentJti);

            TokenClaims claims = tokenService.validateToken(token);
            assertNotNull(claims.jti());
            assertEquals(familyId, claims.familyId());
            assertEquals(parentJti, claims.parentJti());
        }

        @Test
        @DisplayName("Refresh token via login signature carries jti and family_id, parent_jti null")
        void givenLoginSignature_whenGenerateRefreshToken_thenCarriesJtiAndFamilyButNoParent() {
            String token = tokenService.generateRefreshToken(222L);

            TokenClaims claims = tokenService.validateToken(token);
            assertNotNull(claims.jti());
            assertNotNull(claims.familyId());
            assertNull(claims.parentJti());
        }

        @Test
        @DisplayName("Access token carries no lineage claims")
        void givenAccessToken_whenValidate_thenLineageClaimsNull() {
            String token = tokenService.generateAccessToken(333L, UserRole.NORMAL);

            TokenClaims claims = tokenService.validateToken(token);
            assertNull(claims.jti());
            assertNull(claims.familyId());
            assertNull(claims.parentJti());
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
            String token = tokenService.generateAccessToken(userId, UserRole.MODERATOR);

            // Act
            TokenClaims claims = tokenService.validateToken(token);

            // Assert
            assertNotNull(claims);
            assertEquals(userId, claims.userId());
            assertNull(claims.email());
            assertNotNull(claims.issuedAt());
            assertNotNull(claims.expiration());
        }

        @Test
        @DisplayName("Should throw for expired token")
        void givenExpiredToken_whenValidate_thenThrowsInvalidTokenException() {
            // Arrange - sign at t0, validate at a fixed clock past the access-token expiry
            Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
            JwtTokenService signer = new JwtTokenService(jwtProperties, Clock.fixed(t0, ZoneOffset.UTC));
            String token = signer.generateAccessToken(123L, UserRole.NORMAL);

            JwtTokenService laterValidator = new JwtTokenService(jwtProperties,
                    Clock.fixed(t0.plusMillis(ACCESS_TOKEN_EXPIRY + 1000), ZoneOffset.UTC));

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> laterValidator.validateToken(token)
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
            JwtTokenService differentService = new JwtTokenService(differentKeyProps);

            String tokenWithDifferentSignature = differentService.generateAccessToken(123L, UserRole.NORMAL);

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

        @Test
        @DisplayName("Should throw MALFORMED for non-numeric subject")
        void givenNonNumericSubject_whenValidate_thenThrowsMalformed() {
            // Arrange - sign a token whose subject is not a numeric userId
            String token = io.jsonwebtoken.Jwts.builder()
                    .subject("not-a-number")
                    .claim("type", TokenClaims.TYPE_ACCESS)
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                    .signWith(testKeyPair.getPrivate(), io.jsonwebtoken.SignatureAlgorithm.RS256)
                    .compact();

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> tokenService.validateToken(token)
            );
            assertEquals(InvalidTokenException.Reason.MALFORMED, exception.getReason());
        }

        @Test
        @DisplayName("Should throw MISSING_CLAIMS for absent subject")
        void givenAbsentSubject_whenValidate_thenThrowsMissingClaims() {
            // Arrange - sign a token with no subject
            String token = io.jsonwebtoken.Jwts.builder()
                    .claim("type", TokenClaims.TYPE_ACCESS)
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                    .signWith(testKeyPair.getPrivate(), io.jsonwebtoken.SignatureAlgorithm.RS256)
                    .compact();

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> tokenService.validateToken(token)
            );
            assertEquals(InvalidTokenException.Reason.MISSING_CLAIMS, exception.getReason());
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
            String token = tokenService.generateAccessToken(expectedUserId, UserRole.NORMAL);

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
            String token = tokenService.generateAccessToken(123L, UserRole.NORMAL);

            // Act
            boolean expired = tokenService.isTokenExpired(token);

            // Assert
            assertFalse(expired);
        }

        @Test
        @DisplayName("Should return true for expired token")
        void givenExpiredToken_whenCheckExpired_thenReturnsTrue() {
            // Arrange - sign at t0, check with a fixed clock past the access-token expiry
            Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
            JwtTokenService signer = new JwtTokenService(jwtProperties, Clock.fixed(t0, ZoneOffset.UTC));
            String token = signer.generateAccessToken(123L, UserRole.NORMAL);

            JwtTokenService laterValidator = new JwtTokenService(jwtProperties,
                    Clock.fixed(t0.plusMillis(ACCESS_TOKEN_EXPIRY + 1000), ZoneOffset.UTC));

            // Act
            boolean expired = laterValidator.isTokenExpired(token);

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
    @DisplayName("getTokenType Tests")
    class GetTokenTypeTests {

        @Test
        @DisplayName("Should return 'access' for access token")
        void givenAccessToken_whenGetType_thenReturnsAccess() {
            // Arrange
            String token = tokenService.generateAccessToken(123L, UserRole.ADMIN);

            // Act
            String type = tokenService.getTokenType(token);

            // Assert
            assertEquals(TokenClaims.TYPE_ACCESS, type);
        }

        @Test
        @DisplayName("Should return 'refresh' for refresh token")
        void givenRefreshToken_whenGetType_thenReturnsRefresh() {
            // Arrange
            String token = tokenService.generateRefreshToken(123L);

            // Act
            String type = tokenService.getTokenType(token);

            // Assert
            assertEquals(TokenClaims.TYPE_REFRESH, type);
        }
    }

    @Nested
    @DisplayName("Minimized Claims Security Tests")
    class MinimizedClaimsSecurityTests {

        @Test
        @DisplayName("Access token sub is the numeric userId in plaintext, no email, no enc")
        void givenAccessToken_whenInspectPayload_thenSubIsUserIdAndNoEmailOrEnc() {
            // Arrange
            String token = tokenService.generateAccessToken(12345L, UserRole.ADMIN);

            // Act
            String payload = decodePayload(token);

            // Assert
            assertTrue(payload.contains("\"sub\":\"12345\""), "sub should be the numeric userId");
            assertFalse(payload.contains("enc"), "Payload must not contain an encrypted claim blob");
            assertFalse(payload.contains("email"), "Payload must not contain email");
            assertFalse(payload.contains("@"), "Payload must not contain an email address");
            assertTrue(payload.contains(UserRole.ADMIN.getValue()), "role should be present in cleartext");
        }

        @Test
        @DisplayName("Refresh token sub is userId, carries lineage claims, no email, no enc")
        void givenRefreshToken_whenInspectPayload_thenSubIsUserIdAndNoEmailOrEnc() {
            // Arrange
            String token = tokenService.generateRefreshToken(67890L, "fam-1", "parent-jti");

            // Act
            String payload = decodePayload(token);
            TokenClaims claims = tokenService.validateToken(token);

            // Assert
            assertTrue(payload.contains("\"sub\":\"67890\""), "sub should be the numeric userId");
            assertFalse(payload.contains("enc"), "Payload must not contain an encrypted claim blob");
            assertFalse(payload.contains("email"), "Payload must not contain email");
            assertNotNull(claims.jti());
            assertEquals("fam-1", claims.familyId());
            assertEquals("parent-jti", claims.parentJti());
        }
    }
}
