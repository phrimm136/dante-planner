package org.danteplanner.backend.auth.token;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.redis.testcontainers.RedisContainer;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.danteplanner.backend.user.entity.UserRole;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Unit tests for {@link RefreshRotationService}.
 *
 * <p>Exercises the full lineage state machine against a real {@link JwtTokenService}
 * so generated successors carry real lineage claims, plus a {@link SimpleMeterRegistry}
 * to assert outcome metrics. Family state is externalized to a live Redis container so
 * theft detection survives across service instances. Covers theft scenarios A–D from
 * the spec plus cross-instance atomicity.</p>
 */
@Tag("containerized")
class RefreshRotationServiceTest {

    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final long REFRESH_TOKEN_EXPIRY = 604800000L; // 7 days
    private static final Long USER_ID = 42L;
    private static final String EMAIL = "rotation@example.com";

    private static final RedisContainer REDIS = new RedisContainer(REDIS_IMAGE);

    private static StringRedisTemplate sharedTemplate;

    private JwtTokenService tokenService;
    private JwtProperties jwtProperties;
    private CookieUtils cookieUtils;
    private SimpleMeterRegistry meterRegistry;
    private RefreshRotationService rotationService;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        sharedTemplate = buildTemplate(REDIS.getRedisHost(), REDIS.getRedisPort());
    }

    private static StringRedisTemplate buildTemplate(String host, int port) {
        LettuceConnectionFactory f = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        f.afterPropertiesSet();
        StringRedisTemplate t = new StringRedisTemplate(f);
        t.afterPropertiesSet();
        return t;
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        byte[] aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);

        jwtProperties = new JwtProperties();
        jwtProperties.setPrivateKey(keyPair.getPrivate());
        jwtProperties.setPublicKey(keyPair.getPublic());
        jwtProperties.setEncryptionKeyBytes(aesKey);
        jwtProperties.setAccessTokenExpiry(900000L);
        jwtProperties.setRefreshTokenExpiry(REFRESH_TOKEN_EXPIRY);

        tokenService = new JwtTokenService(jwtProperties);
        cookieUtils = new CookieUtils(true, "", "Lax");
        meterRegistry = new SimpleMeterRegistry();

        rotationService = new RefreshRotationService(
                sharedTemplate, tokenService, tokenService, cookieUtils, jwtProperties, meterRegistry, true);
        rotationService.clear();
    }

    private String freshLoginToken() {
        return tokenService.generateRefreshToken(USER_ID, UUID.randomUUID().toString(), null);
    }

    private MockHttpServletResponse newResponse() {
        return new MockHttpServletResponse();
    }

    private double outcomeCount(String outcome) {
        var counter = meterRegistry.find(RefreshRotationService.METRIC_OUTCOME)
                .tag(RefreshRotationService.TAG_OUTCOME, outcome)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Nested
    @DisplayName("Happy path rotation")
    class HappyPath {

        @Test
        @DisplayName("Fresh login token rotates: parent PENDING, successor UNUSED_LATEST")
        void rotate_WhenFreshLogin_ParentPendingSuccessorUnusedLatest() {
            String r1 = freshLoginToken();
            String r1Jti = tokenService.validateToken(r1).jti();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult result = rotationService.rotate(r1, newResponse());

            assertInstanceOf(RotationResult.Rotated.class, result);
            RotationResult.Rotated rotated = (RotationResult.Rotated) result;

            assertEquals(RotationState.PENDING, rotationService.stateOf(r1Jti));
            assertEquals(RotationState.UNUSED_LATEST, rotationService.stateOf(rotated.claims().jti()));
            assertEquals(familyId, rotated.claims().familyId());
            assertEquals(r1Jti, rotated.claims().parentJti());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_ROTATED));
        }

        @Test
        @DisplayName("Successor first use marks parent USED and drops pendingJwt")
        void rotate_WhenSuccessorFirstUse_MarksParentUsed() {
            String r1 = freshLoginToken();
            String r1Jti = tokenService.validateToken(r1).jti();

            RotationResult.Rotated first = (RotationResult.Rotated) rotationService.rotate(r1, newResponse());
            String r2 = first.newRefreshJwt();
            String r2Jti = first.claims().jti();

            RotationResult.Rotated second = (RotationResult.Rotated) rotationService.rotate(r2, newResponse());

            assertEquals(RotationState.USED, rotationService.stateOf(r1Jti));
            assertEquals(RotationState.PENDING, rotationService.stateOf(r2Jti));
            assertEquals(r2Jti, second.claims().parentJti());
            assertEquals(2.0, outcomeCount(RefreshRotationService.OUTCOME_ROTATED));
        }

        @Test
        @DisplayName("Rotation sets a refresh cookie carrying the new token")
        void rotate_WhenRotated_SetsRefreshCookie() {
            String r1 = freshLoginToken();
            MockHttpServletResponse response = newResponse();

            RotationResult.Rotated rotated = (RotationResult.Rotated) rotationService.rotate(r1, response);

            Cookie refreshCookie = response.getCookie(CookieConstants.REFRESH_TOKEN);
            assertNotNull(refreshCookie);
            assertEquals(rotated.newRefreshJwt(), refreshCookie.getValue());
            assertTrue(refreshCookie.getMaxAge() > 0);
        }
    }

    @Nested
    @DisplayName("Retry (Scenario C)")
    class Retry {

        @Test
        @DisplayName("Re-presenting parent supersedes prior successor and mints a fresh one")
        void rotate_WhenParentRepresented_SupersedesPredecessor() {
            String r1 = freshLoginToken();

            RotationResult.Rotated firstAttempt = (RotationResult.Rotated) rotationService.rotate(r1, newResponse());
            String r2First = firstAttempt.claims().jti();

            RotationResult.Rotated retry = (RotationResult.Rotated) rotationService.rotate(r1, newResponse());
            String r2Retry = retry.claims().jti();

            assertNotEquals(r2First, r2Retry);
            assertEquals(RotationState.SUPERSEDED, rotationService.stateOf(r2First));
            assertEquals(RotationState.UNUSED_LATEST, rotationService.stateOf(r2Retry));
            assertEquals(RotationState.PENDING, rotationService.stateOf(tokenService.validateToken(r1).jti()));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_ROTATED));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_RETRY_SUPERSEDED));
        }

        @Test
        @DisplayName("Fresh successor from retry rotates normally")
        void rotate_WhenRetrySuccessor_RotatesNormally() {
            String r1 = freshLoginToken();
            rotationService.rotate(r1, newResponse());
            RotationResult.Rotated retry = (RotationResult.Rotated) rotationService.rotate(r1, newResponse());

            RotationResult result = rotationService.rotate(retry.newRefreshJwt(), newResponse());

            assertInstanceOf(RotationResult.Rotated.class, result);
        }
    }

    @Nested
    @DisplayName("Theft detection")
    class TheftDetection {

        @Test
        @DisplayName("Scenario D: presenting a USED token revokes the family")
        void rotate_WhenUsedToken_RevokesFamily() {
            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult.Rotated first = (RotationResult.Rotated) rotationService.rotate(r1, newResponse());
            rotationService.rotate(first.newRefreshJwt(), newResponse()); // R1 -> USED

            MockHttpServletResponse response = newResponse();
            RotationResult result = rotationService.rotate(r1, response);

            assertInstanceOf(RotationResult.Revoked.class, result);
            assertEquals(familyId, ((RotationResult.Revoked) result).familyId());
            assertTrue(rotationService.isFamilyRevoked(familyId));
            assertNotNull(response.getCookie(CookieConstants.REFRESH_TOKEN));
            assertEquals(0, response.getCookie(CookieConstants.REFRESH_TOKEN).getMaxAge());
            assertEquals(0, response.getCookie(CookieConstants.ACCESS_TOKEN).getMaxAge());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED));
        }

        @Test
        @DisplayName("Scenarios A/B: presenting a SUPERSEDED token revokes the family")
        void rotate_WhenSupersededToken_RevokesFamily() {
            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult.Rotated attempt1 = (RotationResult.Rotated) rotationService.rotate(r1, newResponse());
            String r2Superseded = attempt1.newRefreshJwt();
            rotationService.rotate(r1, newResponse()); // supersedes r2Superseded

            RotationResult result = rotationService.rotate(r2Superseded, newResponse());

            assertInstanceOf(RotationResult.Revoked.class, result);
            assertTrue(rotationService.isFamilyRevoked(familyId));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED));
        }
    }

    @Nested
    @DisplayName("Family revocation")
    class FamilyRevocation {

        @Test
        @DisplayName("All tokens in a revoked family are rejected")
        void rotate_WhenRevokedFamily_RejectsAllTokens() {
            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();
            RotationResult.Rotated rotated = (RotationResult.Rotated) rotationService.rotate(r1, newResponse());
            String r2 = rotated.newRefreshJwt();

            rotationService.revokeFamily(familyId);

            MockHttpServletResponse response = newResponse();
            RotationResult result = rotationService.rotate(r2, response);

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.REVOKED_FAMILY,
                    ((RotationResult.Rejected) result).reason());
            assertEquals(0, response.getCookie(CookieConstants.REFRESH_TOKEN).getMaxAge());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_REJECTED_REVOKED_FAMILY));
        }

        @Test
        @DisplayName("Invalid token is rejected as INVALID")
        void rotate_WhenInvalidToken_Rejected() {
            RotationResult result = rotationService.rotate("not.a.jwt", newResponse());

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.INVALID,
                    ((RotationResult.Rejected) result).reason());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID));
        }

        @Test
        @DisplayName("Access token is rejected as INVALID and mints nothing even with legacy admit on")
        void rotate_WhenAccessToken_RejectedEvenWithLegacyAdmit() {
            String accessToken = tokenService.generateAccessToken(USER_ID, UserRole.NORMAL);

            RotationResult result = rotationService.rotate(accessToken, newResponse());

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.INVALID,
                    ((RotationResult.Rejected) result).reason());
            assertEquals(0, rotationService.rotationStateSize());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID));
        }
    }

    @Nested
    @DisplayName("Concurrent rotation race")
    class ConcurrentRotation {

        @Test
        @DisplayName("Two threads rotating same parent: one transition, one supersede, both valid")
        void rotate_WhenConcurrentSameParent_OneTransitionOneSupersede() throws Exception {
            String r1 = freshLoginToken();
            String r1Jti = tokenService.validateToken(r1).jti();

            int threads = 2;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CyclicBarrier barrier = new CyclicBarrier(threads);
            CountDownLatch done = new CountDownLatch(threads);
            List<RotationResult.Rotated> results = new ArrayList<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await();
                        RotationResult result = rotationService.rotate(r1, newResponse());
                        synchronized (results) {
                            results.add((RotationResult.Rotated) result);
                        }
                    } catch (Throwable t) {
                        failure.set(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(done.await(10, TimeUnit.SECONDS));
            pool.shutdownNow();
            assertNull(failure.get(), () -> "thread failed: " + failure.get());

            assertEquals(2, results.size());

            // Parent ends PENDING pointing at exactly one of the two minted successors.
            assertEquals(RotationState.PENDING, rotationService.stateOf(r1Jti));

            String jtiA = results.get(0).claims().jti();
            String jtiB = results.get(1).claims().jti();
            assertNotEquals(jtiA, jtiB);

            RotationState stateA = rotationService.stateOf(jtiA);
            RotationState stateB = rotationService.stateOf(jtiB);
            List<RotationState> states = List.of(stateA, stateB);
            assertTrue(states.contains(RotationState.UNUSED_LATEST),
                    () -> "expected one UNUSED_LATEST winner, got " + states);
            assertTrue(states.contains(RotationState.SUPERSEDED),
                    () -> "expected one SUPERSEDED loser, got " + states);

            // Both presenters received a usable distinct JWT.
            assertNotEquals(results.get(0).newRefreshJwt(), results.get(1).newRefreshJwt());
        }
    }

    @Nested
    @DisplayName("Cross-instance atomicity")
    class CrossInstanceAtomicity {

        @Test
        @DisplayName("Token driven to USED on instance A is detected as theft when replayed on instance B over shared Redis")
        void rotate_WhenTokenUsedOnInstanceA_ReplayOnInstanceB_DetectsTheftOverSharedRedis() {
            StringRedisTemplate templateA = buildTemplate(REDIS.getRedisHost(), REDIS.getRedisPort());
            StringRedisTemplate templateB = buildTemplate(REDIS.getRedisHost(), REDIS.getRedisPort());

            RefreshRotationService instanceA = new RefreshRotationService(
                    templateA, tokenService, tokenService, cookieUtils, jwtProperties, new SimpleMeterRegistry(), true);
            RefreshRotationService instanceB = new RefreshRotationService(
                    templateB, tokenService, tokenService, cookieUtils, jwtProperties, new SimpleMeterRegistry(), true);

            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult.Rotated first = (RotationResult.Rotated) instanceA.rotate(r1, newResponse());
            instanceA.rotate(first.newRefreshJwt(), newResponse()); // r1 -> USED on A

            MockHttpServletResponse responseB = newResponse();
            RotationResult replay = instanceB.rotate(r1, responseB);

            assertInstanceOf(RotationResult.Revoked.class, replay);
            assertTrue(instanceB.isFamilyRevoked(familyId));
            assertNotNull(responseB.getCookie(CookieConstants.REFRESH_TOKEN));
            assertEquals(0, responseB.getCookie(CookieConstants.REFRESH_TOKEN).getMaxAge());
        }
    }

    @Nested
    @DisplayName("Legacy token admission")
    class LegacyAdmission {

        private static final String LEGACY_TOKEN = "legacy.refresh.jwt";

        private TokenClaims legacyClaims(Date issuedAt) {
            return new TokenClaims(
                    USER_ID, EMAIL, TokenClaims.TYPE_REFRESH, UserRole.NORMAL,
                    issuedAt, new Date(issuedAt.getTime() + REFRESH_TOKEN_EXPIRY));
        }

        private RefreshRotationService serviceWith(TokenValidator validator, boolean legacyAdmit) {
            return new RefreshRotationService(
                    sharedTemplate, validator, tokenService, cookieUtils, jwtProperties, meterRegistry, legacyAdmit);
        }

        @Test
        @DisplayName("Admit on: legacy token admitted, successor carries real lineage, counter increments")
        void rotate_WhenLegacyAdmitOn_AdmitsAndRotates() {
            JwtTokenService validator = spy(tokenService);
            doReturn(legacyClaims(new Date())).when(validator).validateToken(LEGACY_TOKEN);
            RefreshRotationService svc = serviceWith(validator, true);

            RotationResult result = svc.rotate(LEGACY_TOKEN, newResponse());

            assertInstanceOf(RotationResult.Rotated.class, result);
            RotationResult.Rotated rotated = (RotationResult.Rotated) result;
            assertNotNull(rotated.claims().jti());
            assertNotNull(rotated.claims().familyId());
            assertNotNull(rotated.claims().parentJti());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_LEGACY_ADMITTED));
        }

        @Test
        @DisplayName("Admit off: legacy token rejected as INVALID, no state entry")
        void rotate_WhenLegacyAdmitOff_Rejects() {
            JwtTokenService validator = spy(tokenService);
            doReturn(legacyClaims(new Date())).when(validator).validateToken(LEGACY_TOKEN);
            RefreshRotationService svc = serviceWith(validator, false);

            RotationResult result = svc.rotate(LEGACY_TOKEN, newResponse());

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.INVALID,
                    ((RotationResult.Rejected) result).reason());
            assertEquals(0, svc.rotationStateSize());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID));
        }

        @Test
        @DisplayName("Same legacy token presented twice maps to the same synthesized family")
        void rotate_WhenSameLegacyTokenTwice_DeterministicFamily() {
            Date issuedAt = new Date();
            JwtTokenService validator = spy(tokenService);
            doReturn(legacyClaims(issuedAt)).when(validator).validateToken(LEGACY_TOKEN);
            RefreshRotationService svc = serviceWith(validator, true);

            RotationResult.Rotated first = (RotationResult.Rotated) svc.rotate(LEGACY_TOKEN, newResponse());
            RotationResult.Rotated second = (RotationResult.Rotated) svc.rotate(LEGACY_TOKEN, newResponse());

            assertEquals(first.claims().familyId(), second.claims().familyId());
            assertNotEquals(first.claims().jti(), second.claims().jti());
            assertEquals(2.0, outcomeCount(RefreshRotationService.OUTCOME_LEGACY_ADMITTED));
        }
    }
}
