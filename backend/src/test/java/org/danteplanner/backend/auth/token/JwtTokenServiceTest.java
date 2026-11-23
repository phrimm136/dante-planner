package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private static final JwtProperties SHARED_KEYS = new TestJwt().properties();
    private static final KeyPair DIFFERENT_KEY_PAIR = generateKeyPair();

    private JwtTokenService tokenService;
    private JwtProperties jwtProperties;
    private byte[] testAesKey;

    @BeforeEach
    void setUp() throws Exception {
        // Generate AES-256 key for testing
        testAesKey = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(testAesKey);

        // Setup JWT properties
        jwtProperties = new JwtProperties(SHARED_KEYS.getPrivateKey(), SHARED_KEYS.getPublicKey(),
                testAesKey, ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY);

        tokenService = new JwtTokenService(jwtProperties, Clock.systemUTC());
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
            AccessClaims claims = tokenService.validateAccessToken(token);
            assertThat(claims.userId()).isEqualTo(userId);
            assertThat(claims.role()).isEqualTo(UserRole.NORMAL);
        }

        @Test
        void givenValidInput_WhenGenerateAccessToken_ThenHasCorrectExpiration() {
            // Arrange
            Long userId = 123L;

            // Act
            String token = tokenService.generateAccessToken(userId, UserRole.NORMAL);

            // Assert
            AccessClaims claims = tokenService.validateAccessToken(token);

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
            AccessClaims claims = tokenService.validateAccessToken(token);
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
            RefreshClaims claims = tokenService.validateRefreshToken(token);
            assertThat(claims.userId()).isEqualTo(userId);
        }

        @Test
        void givenAccessAndRefreshTokens_WhenCompareExpiration_ThenRefreshLonger() {
            // Arrange
            Long userId = 123L;

            // Act
            String accessToken = tokenService.generateAccessToken(userId, UserRole.NORMAL);
            String refreshToken = tokenService.generateRefreshToken(userId);

            // Assert
            AccessClaims accessClaims = tokenService.validateAccessToken(accessToken);
            RefreshClaims refreshClaims = tokenService.validateRefreshToken(refreshToken);

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

            RefreshClaims claims = tokenService.validateRefreshToken(token);
            assertThat(claims.lineage()).isInstanceOfSatisfying(RefreshLineage.Child.class, child -> {
                assertThat(child.jti()).isNotNull();
                assertThat(child.familyId()).isEqualTo(familyId);
                assertThat(child.parentJti()).isEqualTo(parentJti);
            });
        }

        @Test
        void givenLoginSignature_WhenGenerateRefreshToken_ThenCarriesJtiAndFamilyButNoParent() {
            String token = tokenService.generateRefreshToken(222L);

            RefreshClaims claims = tokenService.validateRefreshToken(token);
            assertThat(claims.lineage()).isInstanceOfSatisfying(RefreshLineage.Root.class, root -> {
                assertThat(root.jti()).isNotNull();
                assertThat(root.familyId()).isNotNull();
            });
        }

        @Test
        void givenJtiFamilyAndParent_WhenLineageIsRead_ThenItIsAChildKeepingItsParent() {
            assertThat(RefreshLineage.of("j2", "f1", "j1")).isEqualTo(new RefreshLineage.Child("j2", "f1", "j1"));
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
            AccessClaims claims = tokenService.validateAccessToken(token);

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
            assertRejected(() -> laterValidator.validateAccessToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.EXPIRED));
        }

        @Test
        void givenInvalidSignature_WhenValidate_ThenThrowsInvalidTokenException() throws Exception {
            // Arrange - create token with different RSA keypair
            JwtProperties differentKeyProps = new JwtProperties(DIFFERENT_KEY_PAIR.getPrivate(), DIFFERENT_KEY_PAIR.getPublic(),
                    testAesKey, ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY);
            JwtTokenService differentService = new JwtTokenService(differentKeyProps, Clock.systemUTC());

            String tokenWithDifferentSignature = differentService.generateAccessToken(123L, UserRole.NORMAL);

            // Act & Assert
            assertRejected(() -> tokenService.validateAccessToken(tokenWithDifferentSignature))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.INVALID_SIGNATURE));
        }

        @Test
        void givenMalformedToken_WhenValidate_ThenThrowsInvalidTokenException() {
            // Arrange
            String malformedToken = "not.a.valid.jwt.token";

            // Act & Assert
            assertRejected(() -> tokenService.validateAccessToken(malformedToken))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.MALFORMED));
        }

        @Test
        void givenGarbageData_WhenValidate_ThenThrowsInvalidTokenException() {
            // Arrange
            String invalidToken = "garbage-data";

            // Act & Assert
            assertRejected(() -> tokenService.validateAccessToken(invalidToken))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.MALFORMED));
        }

        @Test
        void givenNonNumericSubject_WhenValidate_ThenThrowsMalformed() {
            // Arrange - sign a token whose subject is not a numeric userId
            String token = io.jsonwebtoken.Jwts.builder()
                    .subject("not-a-number")
                    .claim("type", "access")
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                    .signWith(SHARED_KEYS.getPrivateKey(), io.jsonwebtoken.SignatureAlgorithm.RS256)
                    .compact();

            // Act & Assert
            assertRejected(() -> tokenService.validateAccessToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.MALFORMED));
        }

        @Test
        void givenAbsentSubject_WhenValidate_ThenThrowsMissingClaims() {
            // Arrange - sign a token with no subject
            String token = io.jsonwebtoken.Jwts.builder()
                    .claim("type", "access")
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                    .signWith(SHARED_KEYS.getPrivateKey(), io.jsonwebtoken.SignatureAlgorithm.RS256)
                    .compact();

            // Act & Assert
            assertRejected(() -> tokenService.validateAccessToken(token))
                    .withCode("UNAUTHORIZED")
                    .isInstanceOfSatisfying(InvalidTokenException.class, rejection -> assertThat(rejection.getReason())
                            .isEqualTo(InvalidTokenException.Reason.MISSING_CLAIMS));
        }
    }

    @Nested
    class TypedValidatorTypeEnforcementTests {

        @Test
        void validateAccessToken_WhenAccessToken_ReturnsClaims() {
            String token = tokenService.generateAccessToken(1001L, UserRole.NORMAL);

            AccessClaims claims = tokenService.validateAccessToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.userId()).isEqualTo(1001L);
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

            RefreshClaims claims = tokenService.validateRefreshToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.userId()).isEqualTo(1003L);
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

            AccessClaims accessClaims = tokenService.validateAccessToken(accessToken);
            RefreshClaims refreshClaims = tokenService.validateRefreshToken(refreshToken);

            assertThat(accessClaims.userId()).isEqualTo(1005L);
            assertThat(refreshClaims.userId()).isEqualTo(1005L);
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
                    .signWith(SHARED_KEYS.getPrivateKey(), io.jsonwebtoken.SignatureAlgorithm.RS256)
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
    class PayloadTests {

        private static final Instant ISSUED_AT = Instant.parse("2026-11-23T00:00:00Z");

        private final TestJwt jwt = new TestJwt(ISSUED_AT);

        private static void assertReason(ThrowingCallable parse, InvalidTokenException.Reason reason) {
            assertThatThrownBy(parse).isInstanceOfSatisfying(InvalidTokenException.class,
                    rejection -> assertThat(rejection.getReason()).isEqualTo(reason));
        }

        @Test
        void validateAccessToken_WhenThePayloadIsComplete_ReadsEveryClaim() {
            assertThat(jwt.service().validateAccessToken(jwt.sign(jwt.accessTokenPayload(1L))))
                    .isEqualTo(new AccessClaims(1L, UserRole.NORMAL, Date.from(ISSUED_AT),
                            Date.from(ISSUED_AT.plusMillis(ACCESS_TOKEN_EXPIRY))));
        }

        @Test
        void validateRefreshToken_WhenThePayloadHasNoParent_ReadsARoot() {
            assertThat(jwt.service().validateRefreshToken(jwt.sign(jwt.refreshTokenPayload(1L))))
                    .isEqualTo(new RefreshClaims(1L, Date.from(ISSUED_AT),
                            Date.from(ISSUED_AT.plusMillis(REFRESH_TOKEN_EXPIRY)),
                            new RefreshLineage.Root("jti-1", "fam-1")));
        }

        @Test
        void validateRefreshToken_WhenThePayloadHasAParent_ReadsAChild() {
            Map<String, Object> payload = jwt.refreshTokenPayload(1L);
            payload.put("parent_jti", "jti-0");

            assertThat(jwt.service().validateRefreshToken(jwt.sign(payload)).lineage())
                    .isEqualTo(new RefreshLineage.Child("jti-1", "fam-1", "jti-0"));
        }

        @Test
        void validators_WhenTheGeneratorMintsAtAFixedInstant_ReadItBack() {
            assertThat(jwt.service().validateAccessToken(jwt.service().generateAccessToken(1L, UserRole.ADMIN)))
                    .isEqualTo(new AccessClaims(1L, UserRole.ADMIN, Date.from(ISSUED_AT),
                            Date.from(ISSUED_AT.plusMillis(ACCESS_TOKEN_EXPIRY))));
            RefreshClaims refresh =
                    jwt.service().validateRefreshToken(jwt.service().generateRefreshToken(1L, "fam-1", "jti-0"));
            assertThat(refresh).isEqualTo(new RefreshClaims(1L, Date.from(ISSUED_AT),
                    Date.from(ISSUED_AT.plusMillis(REFRESH_TOKEN_EXPIRY)),
                    new RefreshLineage.Child(refresh.lineage().jti(), "fam-1", "jti-0")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"sub", "iat", "exp"})
        void validateAccessToken_WhenAClaimIsAbsent_ThrowsMissingClaims(String claim) {
            Map<String, Object> payload = jwt.accessTokenPayload(1L);
            payload.remove(claim);

            assertReason(() -> jwt.service().validateAccessToken(jwt.sign(payload)),
                    InvalidTokenException.Reason.MISSING_CLAIMS);
        }

        @ParameterizedTest
        @ValueSource(strings = {"sub", "iat", "exp", "jti", "family_id"})
        void validateRefreshToken_WhenAClaimIsAbsent_ThrowsMissingClaims(String claim) {
            Map<String, Object> payload = jwt.refreshTokenPayload(1L);
            payload.remove(claim);

            assertReason(() -> jwt.service().validateRefreshToken(jwt.sign(payload)),
                    InvalidTokenException.Reason.MISSING_CLAIMS);
        }

        @Test
        void validateRefreshToken_WhenTheFamilyIsNotAString_ThrowsMalformed() {
            Map<String, Object> payload = jwt.refreshTokenPayload(1L);
            payload.put("family_id", 7);

            assertReason(() -> jwt.service().validateRefreshToken(jwt.sign(payload)),
                    InvalidTokenException.Reason.MALFORMED);
        }

        @Test
        void validateAccessToken_WhenItExpiresBeforeItsIssue_ThrowsMalformed() {
            Map<String, Object> payload = jwt.accessTokenPayload(1L);
            payload.put("iat", Date.from(ISSUED_AT.plusSeconds(3600)));

            assertReason(() -> jwt.service().validateAccessToken(jwt.sign(payload)),
                    InvalidTokenException.Reason.MALFORMED);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = "UNKNOWN")
        void validateAccessToken_WhenTheRoleIsAbsentOrUnknown_ReadsNormal(String role) {
            Map<String, Object> payload = jwt.accessTokenPayload(1L);
            payload.put("role", role);

            assertThat(jwt.service().validateAccessToken(jwt.sign(payload)).role()).isEqualTo(UserRole.NORMAL);
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
            RefreshClaims claims = tokenService.validateRefreshToken(token);

            // Assert
            assertThat(payload).as("sub should be the numeric userId").contains("\"sub\":\"67890\"");
            assertThat(payload).as("Payload must not contain an encrypted claim blob").doesNotContain("enc");
            assertThat(payload).as("Payload must not contain email").doesNotContain("email");
            assertThat(claims.lineage()).isInstanceOfSatisfying(RefreshLineage.Child.class, child -> {
                assertThat(child.jti()).isNotNull();
                assertThat(child.familyId()).isEqualTo("fam-1");
                assertThat(child.parentJti()).isEqualTo("parent-jti");
            });
        }
    }
}
