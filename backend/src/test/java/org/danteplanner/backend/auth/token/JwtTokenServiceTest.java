package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import java.nio.charset.StandardCharsets;

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
        assertThat(parts.length).as("JWT should have 3 parts").isEqualTo(3);
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    @Nested
    class GenerateAccessTokenTests {

        @Test
        void givenValidInput_WhenGenerateAccessToken_ThenContainsCorrectClaims() {
            // Arrange
            Long userId = 123L;

            // Act
            String token = tokenService.generateAccessToken(userId, UserRole.NORMAL);

            // Assert
            assertThat(token).isNotNull();
            TokenClaims claims = tokenService.validateToken(token);
            assertThat(claims.userId()).isEqualTo(userId);
            assertThat(claims.type()).isEqualTo(TokenClaims.TYPE_ACCESS);
            assertThat(claims.role()).isEqualTo(UserRole.NORMAL);
            assertThat(claims.isAccessToken()).isTrue();
            assertThat(claims.isRefreshToken()).isFalse();
        }

        @Test
        void givenValidInput_WhenGenerateAccessToken_ThenHasCorrectExpiration() {
            // Arrange
            Long userId = 123L;

            // Act
            String token = tokenService.generateAccessToken(userId, UserRole.NORMAL);

            // Assert - expiration should be in the future
            TokenClaims claims = tokenService.validateToken(token);
            assertThat(claims.isExpired()).as("Token should not be expired immediately after creation").isFalse();

            // Verify expiration is approximately ACCESS_TOKEN_EXPIRY from now
            long now = System.currentTimeMillis();
            long expirationTime = claims.expiration().getTime();
            long timeDiff = expirationTime - now;

            // Allow 5 second tolerance for timing variations
            long tolerance = 5000;
            assertThat(timeDiff)
                    .as("ms from now to expiration")
                    .isBetween(ACCESS_TOKEN_EXPIRY - tolerance, ACCESS_TOKEN_EXPIRY + tolerance);
        }

        @Test
        void givenValidInput_WhenGenerateAccessToken_ThenSetsIssuedAt() {
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
            assertThat(issuedAt).as("issuedAt").isBetween(beforeGeneration - tolerance, afterGeneration + tolerance);
        }
    }

    @Nested
    class GenerateRefreshTokenTests {

        @Test
        void givenValidInput_WhenGenerateRefreshToken_ThenContainsCorrectClaims() {
            // Arrange
            Long userId = 456L;

            // Act
            String token = tokenService.generateRefreshToken(userId);

            // Assert
            assertThat(token).isNotNull();
            TokenClaims claims = tokenService.validateToken(token);
            assertThat(claims.userId()).isEqualTo(userId);
            assertThat(claims.type()).isEqualTo(TokenClaims.TYPE_REFRESH);
            assertThat(claims.isRefreshToken()).isTrue();
            assertThat(claims.isAccessToken()).isFalse();
        }

        @Test
        void givenAccessAndRefreshTokens_WhenCompareExpiration_ThenRefreshLonger() {
            // Arrange
            Long userId = 123L;

            // Act
            String accessToken = tokenService.generateAccessToken(userId, UserRole.NORMAL);
            String refreshToken = tokenService.generateRefreshToken(userId);

            // Assert
            TokenClaims accessClaims = tokenService.validateToken(accessToken);
            TokenClaims refreshClaims = tokenService.validateToken(refreshToken);

            assertThat(refreshClaims.expiration().after(accessClaims.expiration()))
                    .as("Refresh token should expire after access token")
                    .isTrue();
        }
    }

    @Nested
    class LineageClaimsTests {

        @Test
        void givenFamilyAndParent_WhenGenerateRefreshToken_ThenCarriesAllLineageClaims() {
            Long userId = 111L;
            String familyId = "fam-123";
            String parentJti = "parent-jti-456";

            String token = tokenService.generateRefreshToken(userId, familyId, parentJti);

            TokenClaims claims = tokenService.validateToken(token);
            assertThat(claims.jti()).isNotNull();
            assertThat(claims.familyId()).isEqualTo(familyId);
            assertThat(claims.parentJti()).isEqualTo(parentJti);
        }

        @Test
        void givenLoginSignature_WhenGenerateRefreshToken_ThenCarriesJtiAndFamilyButNoParent() {
            String token = tokenService.generateRefreshToken(222L);

            TokenClaims claims = tokenService.validateToken(token);
            assertThat(claims.jti()).isNotNull();
            assertThat(claims.familyId()).isNotNull();
            assertThat(claims.parentJti()).isNull();
        }

        @Test
        void givenAccessToken_WhenValidate_ThenLineageClaimsNull() {
            String token = tokenService.generateAccessToken(333L, UserRole.NORMAL);

            TokenClaims claims = tokenService.validateToken(token);
            assertThat(claims.jti()).isNull();
            assertThat(claims.familyId()).isNull();
            assertThat(claims.parentJti()).isNull();
        }
    }

    @Nested
    class ValidateTokenTests {

        @Test
        void givenValidToken_WhenValidate_ThenReturnsClaims() {
            // Arrange
            Long userId = 789L;
            String token = tokenService.generateAccessToken(userId, UserRole.MODERATOR);

            // Act
            TokenClaims claims = tokenService.validateToken(token);

            // Assert
            assertThat(claims).isNotNull();
            assertThat(claims.userId()).isEqualTo(userId);
            assertThat(claims.issuedAt()).isNotNull();
            assertThat(claims.expiration()).isNotNull();
        }

        @Test
        void givenExpiredToken_WhenValidate_ThenThrowsInvalidTokenException() {
            // Arrange - sign at t0, validate at a fixed clock past the access-token expiry
            Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
            JwtTokenService signer = new JwtTokenService(jwtProperties, Clock.fixed(t0, ZoneOffset.UTC));
            String token = signer.generateAccessToken(123L, UserRole.NORMAL);

            JwtTokenService laterValidator = new JwtTokenService(jwtProperties,
                    Clock.fixed(t0.plusMillis(ACCESS_TOKEN_EXPIRY + 1000), ZoneOffset.UTC));

            // Act & Assert
            assertRejected(() -> laterValidator.validateToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.EXPIRED));
        }

        @Test
        void givenInvalidSignature_WhenValidate_ThenThrowsInvalidTokenException() throws Exception {
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
            assertRejected(() -> tokenService.validateToken(tokenWithDifferentSignature))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.INVALID_SIGNATURE));
        }

        @Test
        void givenMalformedToken_WhenValidate_ThenThrowsInvalidTokenException() {
            // Arrange
            String malformedToken = "not.a.valid.jwt.token";

            // Act & Assert
            assertRejected(() -> tokenService.validateToken(malformedToken))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.MALFORMED));
        }

        @Test
        void givenGarbageData_WhenValidate_ThenThrowsInvalidTokenException() {
            // Arrange
            String invalidToken = "garbage-data";

            // Act & Assert
            assertRejected(() -> tokenService.validateToken(invalidToken))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.MALFORMED));
        }

        @Test
        void givenNonNumericSubject_WhenValidate_ThenThrowsMalformed() {
            // Arrange - sign a token whose subject is not a numeric userId
            String token = io.jsonwebtoken.Jwts.builder()
                    .subject("not-a-number")
                    .claim("type", TokenClaims.TYPE_ACCESS)
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                    .signWith(testKeyPair.getPrivate(), io.jsonwebtoken.SignatureAlgorithm.RS256)
                    .compact();

            // Act & Assert
            assertRejected(() -> tokenService.validateToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.MALFORMED));
        }

        @Test
        void givenAbsentSubject_WhenValidate_ThenThrowsMissingClaims() {
            // Arrange - sign a token with no subject
            String token = io.jsonwebtoken.Jwts.builder()
                    .claim("type", TokenClaims.TYPE_ACCESS)
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                    .signWith(testKeyPair.getPrivate(), io.jsonwebtoken.SignatureAlgorithm.RS256)
                    .compact();

            // Act & Assert
            assertRejected(() -> tokenService.validateToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.MISSING_CLAIMS));
        }
    }

    @Nested
    class NeutralValidateTokenTypeAgnosticTests {

        @Test
        void validateToken_WhenAccessToken_ReturnsClaimsWithAccessType() {
            // B8 preserved: neutral parser accepts any validly-signed unexpired JWT regardless of type
            String token = tokenService.generateAccessToken(555L, UserRole.NORMAL);

            TokenClaims claims = tokenService.validateToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.userId()).isEqualTo(555L);
            assertThat(claims.type()).isEqualTo(TokenClaims.TYPE_ACCESS);
        }

        @Test
        void validateToken_WhenRefreshToken_ReturnsClaimsWithRefreshType() {
            // B8 preserved: neutral parser (backs getTokenType) still accepts refresh tokens
            String token = tokenService.generateRefreshToken(555L);

            TokenClaims claims = tokenService.validateToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.userId()).isEqualTo(555L);
            assertThat(claims.type()).isEqualTo(TokenClaims.TYPE_REFRESH);
        }
    }

    @Nested
    class TypedValidatorTypeEnforcementTests {

        @Test
        void validateAccessToken_WhenAccessToken_ReturnsClaims() {
            String token = tokenService.generateAccessToken(1001L, UserRole.NORMAL);

            TokenClaims claims = tokenService.validateAccessToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.userId()).isEqualTo(1001L);
            assertThat(claims.type()).isEqualTo(TokenClaims.TYPE_ACCESS);
        }

        @Test
        void validateAccessToken_WhenRefreshToken_ThrowsInvalidType() {
            String token = tokenService.generateRefreshToken(1002L);

            assertRejected(() -> tokenService.validateAccessToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.INVALID_TYPE));
        }

        @Test
        void validateRefreshToken_WhenRefreshToken_ReturnsClaims() {
            String token = tokenService.generateRefreshToken(1003L);

            TokenClaims claims = tokenService.validateRefreshToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.userId()).isEqualTo(1003L);
            assertThat(claims.type()).isEqualTo(TokenClaims.TYPE_REFRESH);
        }

        @Test
        void validateRefreshToken_WhenAccessToken_ThrowsInvalidType() {
            String token = tokenService.generateAccessToken(1004L, UserRole.NORMAL);

            assertRejected(() -> tokenService.validateRefreshToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.INVALID_TYPE));
        }

        @Test
        void typedValidators_WhenGeneratedTokens_RoundTripWithCorrectClaims() {
            String accessToken = tokenService.generateAccessToken(1005L, UserRole.NORMAL);
            String refreshToken = tokenService.generateRefreshToken(1005L);

            TokenClaims accessClaims = tokenService.validateAccessToken(accessToken);
            TokenClaims refreshClaims = tokenService.validateRefreshToken(refreshToken);

            assertThat(accessClaims.userId()).isEqualTo(1005L);
            assertThat(accessClaims.type()).isEqualTo(TokenClaims.TYPE_ACCESS);
            assertThat(refreshClaims.userId()).isEqualTo(1005L);
            assertThat(refreshClaims.type()).isEqualTo(TokenClaims.TYPE_REFRESH);
        }

        @Test
        void acceptance_WhenWrongTypeToken_MapsToInvalidTypeNotMalformed() {
            String refreshToken = tokenService.generateRefreshToken(2001L);

            assertRejected(() -> tokenService.validateAccessToken(refreshToken))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.INVALID_TYPE));
        }

        @Test
        void typedValidators_WhenTypeClaimAbsent_ThrowInvalidType() {
            String token = io.jsonwebtoken.Jwts.builder()
                    .subject("3001")
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                    .signWith(testKeyPair.getPrivate(), io.jsonwebtoken.SignatureAlgorithm.RS256)
                    .compact();

            assertRejected(() -> tokenService.validateAccessToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.INVALID_TYPE));

            assertRejected(() -> tokenService.validateRefreshToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.INVALID_TYPE));
        }
    }

    @Nested
    class GetUserIdFromTokenTests {

        @Test
        void givenValidToken_WhenGetUserId_ThenReturnsUserId() {
            // Arrange
            Long expectedUserId = 42L;
            String token = tokenService.generateAccessToken(expectedUserId, UserRole.NORMAL);

            // Act
            Long actualUserId = tokenService.getUserIdFromToken(token);

            // Assert
            assertThat(actualUserId).isEqualTo(expectedUserId);
        }

        @Test
        void givenInvalidToken_WhenGetUserId_ThenThrowsInvalidTokenException() {
            // Arrange
            String invalidToken = "invalid.token";

            // Act & Assert
            assertRejected(() -> tokenService.getUserIdFromToken(invalidToken))
                    .withCode("UNAUTHORIZED")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Token is malformed"));
        }
    }

    @Nested
    class IsTokenExpiredTests {

        @Test
        void givenValidToken_WhenCheckExpired_ThenReturnsFalse() {
            // Arrange
            String token = tokenService.generateAccessToken(123L, UserRole.NORMAL);

            // Act
            boolean expired = tokenService.isTokenExpired(token);

            // Assert
            assertThat(expired).isFalse();
        }

        @Test
        void givenExpiredToken_WhenCheckExpired_ThenReturnsTrue() {
            // Arrange - sign at t0, check with a fixed clock past the access-token expiry
            Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
            JwtTokenService signer = new JwtTokenService(jwtProperties, Clock.fixed(t0, ZoneOffset.UTC));
            String token = signer.generateAccessToken(123L, UserRole.NORMAL);

            JwtTokenService laterValidator = new JwtTokenService(jwtProperties,
                    Clock.fixed(t0.plusMillis(ACCESS_TOKEN_EXPIRY + 1000), ZoneOffset.UTC));

            // Act
            boolean expired = laterValidator.isTokenExpired(token);

            // Assert
            assertThat(expired).isTrue();
        }

        @Test
        void givenInvalidToken_WhenCheckExpired_ThenReturnsTrue() {
            // Arrange
            String invalidToken = "invalid.jwt.token";

            // Act
            boolean expired = tokenService.isTokenExpired(invalidToken);

            // Assert
            assertThat(expired).isTrue();
        }
    }

    @Nested
    class GetTokenTypeTests {

        @Test
        void givenAccessToken_WhenGetType_ThenReturnsAccess() {
            // Arrange
            String token = tokenService.generateAccessToken(123L, UserRole.ADMIN);

            // Act
            String type = tokenService.getTokenType(token);

            // Assert
            assertThat(type).isEqualTo(TokenClaims.TYPE_ACCESS);
        }

        @Test
        void givenRefreshToken_WhenGetType_ThenReturnsRefresh() {
            // Arrange
            String token = tokenService.generateRefreshToken(123L);

            // Act
            String type = tokenService.getTokenType(token);

            // Assert
            assertThat(type).isEqualTo(TokenClaims.TYPE_REFRESH);
        }
    }

    @Nested
    class MinimizedClaimsSecurityTests {

        @Test
        void givenAccessToken_WhenInspectPayload_ThenSubIsUserIdAndNoEmailOrEnc() {
            // Arrange
            String token = tokenService.generateAccessToken(12345L, UserRole.ADMIN);

            // Act
            String payload = decodePayload(token);

            // Assert
            assertThat(payload).as("sub should be the numeric userId").contains("\"sub\":\"12345\"");
            assertThat(payload).as("Payload must not contain an encrypted claim blob").doesNotContain("enc");
            assertThat(payload).as("Payload must not contain email").doesNotContain("email");
            assertThat(payload).as("Payload must not contain an email address").doesNotContain("@");
            assertThat(payload).as("role should be present in cleartext").contains(UserRole.ADMIN.getValue());
        }

        @Test
        void givenRefreshToken_WhenInspectPayload_ThenSubIsUserIdAndNoEmailOrEnc() {
            // Arrange
            String token = tokenService.generateRefreshToken(67890L, "fam-1", "parent-jti");

            // Act
            String payload = decodePayload(token);
            TokenClaims claims = tokenService.validateToken(token);

            // Assert
            assertThat(payload).as("sub should be the numeric userId").contains("\"sub\":\"67890\"");
            assertThat(payload).as("Payload must not contain an encrypted claim blob").doesNotContain("enc");
            assertThat(payload).as("Payload must not contain email").doesNotContain("email");
            assertThat(claims.jti()).isNotNull();
            assertThat(claims.familyId()).isEqualTo("fam-1");
            assertThat(claims.parentJti()).isEqualTo("parent-jti");
        }
    }
}
