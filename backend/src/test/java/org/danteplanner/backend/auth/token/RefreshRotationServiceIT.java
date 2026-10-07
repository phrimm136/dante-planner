package org.danteplanner.backend.auth.token;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.integration.SharedRedisContainerSupport;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;


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

import org.danteplanner.backend.user.api.UserRole;


import static org.junit.jupiter.api.Assertions.*;

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
class RefreshRotationServiceIT {
    private static final long REFRESH_TOKEN_EXPIRY = 604800000L; // 7 days
    private static final long RETRY_REUSE_WINDOW_MS = 30000L;
    private static final String SUCCESSOR_JWT_FIELD_PREFIX = "succjwt:";

    private static StringRedisTemplate sharedTemplate;

    private JwtTokenService tokenService;
    private JwtProperties jwtProperties;
    private SimpleMeterRegistry meterRegistry;
    private Long userId;
    private RefreshRotationService rotationService;

    @BeforeAll
    static void startRedis() {
        sharedTemplate = buildTemplate(SharedRedisContainerSupport.host(), SharedRedisContainerSupport.port());
    }

    @AfterAll
    static void stopRedis() {
        ((LettuceConnectionFactory) sharedTemplate.getRequiredConnectionFactory()).destroy();
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
        meterRegistry = new SimpleMeterRegistry();

        rotationService = serviceWithReuseWindow(RETRY_REUSE_WINDOW_MS);
        userId = TestDataFactory.nextUserId();
    }

    private RefreshRotationService serviceWithReuseWindow(long reuseWindowMs) {
        return new RefreshRotationService(
                sharedTemplate, tokenService, tokenService, jwtProperties, meterRegistry,
                true, reuseWindowMs);
    }

    private String freshLoginToken() {
        return tokenService.generateRefreshToken(userId, UUID.randomUUID().toString(), null);
    }

    private RotationState stateIn(String familyId, String jti) {
        Object value = sharedTemplate.opsForHash().get(RefreshRotationService.familyKey(familyId), jti);
        if (value == null) {
            return null;
        }
        String field = value.toString();
        int separator = field.indexOf('|');
        return RotationState.valueOf(separator >= 0 ? field.substring(0, separator) : field);
    }

    private int rotationEntriesIn(String familyId) {
        int total = 0;
        for (Object field : sharedTemplate.opsForHash().keys(RefreshRotationService.familyKey(familyId))) {
            if (!RefreshRotationService.REVOKED_FIELD.equals(field)
                    && !field.toString().startsWith(SUCCESSOR_JWT_FIELD_PREFIX)) {
                total++;
            }
        }
        return total;
    }

    private boolean synthesizedFamilyExists(TokenClaims presented) {
        String familyId = RefreshRotationService.legacyFamilyId(presented.userId(), presented.issuedAt().getTime());
        return Boolean.TRUE.equals(sharedTemplate.hasKey(RefreshRotationService.familyKey(familyId)));
    }

    private double outcomeCount(String outcome) {
        var counter = meterRegistry.find(RefreshRotationService.METRIC_OUTCOME)
                .tag(RefreshRotationService.TAG_OUTCOME, outcome)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Nested
    class HappyPath {

        @Test
        void rotate_WhenFreshLogin_ParentPendingSuccessorUnusedLatest() {
            String r1 = freshLoginToken();
            String r1Jti = tokenService.validateToken(r1).jti();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult result = rotationService.rotate(r1);

            assertInstanceOf(RotationResult.Rotated.class, result);
            RotationResult.Rotated rotated = (RotationResult.Rotated) result;

            assertEquals(RotationState.PENDING, stateIn(familyId, r1Jti));
            assertEquals(RotationState.UNUSED_LATEST, stateIn(familyId, rotated.claims().jti()));
            assertEquals(familyId, rotated.claims().familyId());
            assertEquals(r1Jti, rotated.claims().parentJti());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_ROTATED));
        }

        @Test
        void rotate_WhenSuccessorFirstUse_MarksParentRetiredAndDropsItsMemoizedSuccessor() {
            String r1 = freshLoginToken();
            String r1Jti = tokenService.validateToken(r1).jti();

            RotationResult.Rotated first = (RotationResult.Rotated) rotationService.rotate(r1);
            String r2 = first.newRefreshJwt();
            String r2Jti = first.claims().jti();
            String familyKey = RefreshRotationService.familyKey(first.claims().familyId());
            assertTrue(sharedTemplate.opsForHash().hasKey(familyKey, "succjwt:" + r1Jti));

            RotationResult.Rotated second = (RotationResult.Rotated) rotationService.rotate(r2);

            assertEquals(RotationState.RETIRED, stateIn(first.claims().familyId(), r1Jti));
            assertEquals(RotationState.PENDING, stateIn(first.claims().familyId(), r2Jti));
            assertEquals(r2Jti, second.claims().parentJti());
            assertEquals(2.0, outcomeCount(RefreshRotationService.OUTCOME_ROTATED));
            assertFalse(sharedTemplate.opsForHash().hasKey(familyKey, "succjwt:" + r1Jti));
        }

        @Test
        void rotate_WhenRotated_CarriesSuccessorJwt() {
            String r1 = freshLoginToken();

            RotationResult.Rotated rotated = (RotationResult.Rotated) rotationService.rotate(r1);

            // The caller writes this value as the refresh cookie, so it has to be the token the
            // reported claims belong to.
            assertEquals(rotated.claims().jti(),
                    tokenService.validateToken(rotated.newRefreshJwt()).jti());
        }
    }

    @Nested
    class Retry {

        @Test
        void rotate_WhenParentRepresentedWithinWindow_ReusesSuccessor() {
            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult.Rotated firstAttempt = (RotationResult.Rotated) rotationService.rotate(r1);

            RotationResult.Rotated retry = (RotationResult.Rotated) rotationService.rotate(r1);

            assertEquals(firstAttempt.claims().jti(), retry.claims().jti());
            assertEquals(firstAttempt.newRefreshJwt(), retry.newRefreshJwt());
            assertEquals(RotationState.UNUSED_LATEST, stateIn(familyId, retry.claims().jti()));
            assertEquals(RotationState.PENDING, stateIn(familyId, tokenService.validateToken(r1).jti()));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_ROTATED));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_RETRY_REUSED));
        }

        @Test
        void rotate_WhenParentRepresentedOutsideWindow_SupersedesPredecessor() {
            RefreshRotationService noReuse = serviceWithReuseWindow(0L);
            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult.Rotated firstAttempt = (RotationResult.Rotated) noReuse.rotate(r1);
            String r2First = firstAttempt.claims().jti();

            RotationResult.Rotated retry = (RotationResult.Rotated) noReuse.rotate(r1);
            String r2Retry = retry.claims().jti();

            assertNotEquals(r2First, r2Retry);
            assertEquals(RotationState.SUPERSEDED, stateIn(familyId, r2First));
            assertEquals(RotationState.UNUSED_LATEST, stateIn(familyId, r2Retry));
            assertEquals(RotationState.PENDING, stateIn(familyId, tokenService.validateToken(r1).jti()));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_ROTATED));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_RETRY_SUPERSEDED));
        }

        @Test
        void rotate_WhenRetrySuccessor_RotatesNormally() {
            String r1 = freshLoginToken();
            rotationService.rotate(r1);
            RotationResult.Rotated retry = (RotationResult.Rotated) rotationService.rotate(r1);

            RotationResult result = rotationService.rotate(retry.newRefreshJwt());

            assertInstanceOf(RotationResult.Rotated.class, result);
        }
    }

    @Nested
    class TheftDetection {

        @Test
        void rotate_WhenRetiredToken_RevokesFamily() {
            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult.Rotated first = (RotationResult.Rotated) rotationService.rotate(r1);
            rotationService.rotate(first.newRefreshJwt()); // R1 -> RETIRED

            RotationResult result = rotationService.rotate(r1);

            assertInstanceOf(RotationResult.Revoked.class, result);
            assertEquals(familyId, ((RotationResult.Revoked) result).familyId());
            assertTrue(rotationService.isFamilyRevoked(familyId));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED));
        }

        @Test
        void rotate_WhenLegacyUsedSpelling_RevokesFamily() {
            String r1 = freshLoginToken();
            TokenClaims claims = tokenService.validateToken(r1);

            sharedTemplate.opsForHash().put(
                    "rt:fam:{" + claims.familyId() + "}", claims.jti(),
                    "USED||" + claims.expiration().getTime());

            RotationResult result = rotationService.rotate(r1);

            assertInstanceOf(RotationResult.Revoked.class, result);
            assertTrue(rotationService.isFamilyRevoked(claims.familyId()));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED));
        }

        @Test
        void rotate_WhenSupersededToken_RevokesFamily() {
            RefreshRotationService noReuse = serviceWithReuseWindow(0L);
            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult.Rotated attempt1 = (RotationResult.Rotated) noReuse.rotate(r1);
            String r2Superseded = attempt1.newRefreshJwt();
            noReuse.rotate(r1); // outside the window: supersedes r2Superseded

            RotationResult result = noReuse.rotate(r2Superseded);

            assertInstanceOf(RotationResult.Revoked.class, result);
            assertTrue(noReuse.isFamilyRevoked(familyId));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED));
        }
    }

    @Nested
    class FamilyRevocation {

        @Test
        void rotate_WhenRevokedFamily_RejectsAllTokens() {
            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();
            RotationResult.Rotated rotated = (RotationResult.Rotated) rotationService.rotate(r1);
            String r2 = rotated.newRefreshJwt();

            rotationService.revokeFamily(familyId);

            RotationResult result = rotationService.rotate(r2);

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.REVOKED_FAMILY,
                    ((RotationResult.Rejected) result).reason());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_REJECTED_REVOKED_FAMILY));
        }

        @Test
        void rotate_WhenInvalidToken_Rejected() {
            RotationResult result = rotationService.rotate("not.a.jwt");

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.INVALID,
                    ((RotationResult.Rejected) result).reason());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID));
        }

        @Test
        void rotate_WhenAccessToken_RejectedEvenWithLegacyAdmit() {
            String accessToken = tokenService.generateAccessToken(userId, UserRole.NORMAL);

            RotationResult result = rotationService.rotate(accessToken);

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.INVALID,
                    ((RotationResult.Rejected) result).reason());
            assertFalse(synthesizedFamilyExists(tokenService.validateToken(accessToken)));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID));
        }

        @Test
        void rotate_WhenAccessTokenClaims_RejectedAndFamilyUntouched() {
            RefreshRotationService svc = new RefreshRotationService(
                    sharedTemplate, tokenService, tokenService, jwtProperties, meterRegistry,
                    true, RETRY_REUSE_WINDOW_MS);
            String accessToken = tokenService.generateAccessToken(userId, UserRole.NORMAL);

            RotationResult result = svc.rotate(accessToken);

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.INVALID,
                    ((RotationResult.Rejected) result).reason());
            assertFalse(synthesizedFamilyExists(tokenService.validateToken(accessToken)));
            assertEquals(0.0, outcomeCount(RefreshRotationService.OUTCOME_ROTATED));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID));
        }
    }

    @Nested
    class UserInvalidation {

        private String uinvKey() {
            return "uinv:" + userId;
        }

        @AfterEach
        void clearInvalidation() {
            sharedTemplate.delete(uinvKey());
        }

        @Test
        void rotate_WhenIssuedBeforeInvalidation_Rejected() {
            String token = freshLoginToken();
            sharedTemplate.opsForValue().set(
                    uinvKey(), String.valueOf(System.currentTimeMillis() + 60_000));

            RotationResult result = rotationService.rotate(token);

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.REVOKED_FAMILY,
                    ((RotationResult.Rejected) result).reason());
            assertEquals(1.0,
                    outcomeCount(RefreshRotationService.OUTCOME_REJECTED_USER_INVALIDATED));
        }

        @Test
        void rotate_WhenIssuedAfterInvalidation_Rotates() {
            sharedTemplate.opsForValue().set(
                    uinvKey(), String.valueOf(System.currentTimeMillis() - 60_000));
            String token = freshLoginToken();

            RotationResult result = rotationService.rotate(token);

            assertInstanceOf(RotationResult.Rotated.class, result);
        }

        @Test
        void rotate_WhenFamilyHealthyButUserInvalidated_Rejected() {
            String token = freshLoginToken();
            RotationResult.Rotated first =
                    (RotationResult.Rotated) rotationService.rotate(token);
            sharedTemplate.opsForValue().set(
                    uinvKey(), String.valueOf(System.currentTimeMillis() + 60_000));

            RotationResult result = rotationService.rotate(first.newRefreshJwt());

            assertInstanceOf(RotationResult.Rejected.class, result);
        }
    }

    @Nested
    class ConcurrentRotation {

        @Test
        void rotate_WhenConcurrentSameParent_ConvergesOnOneSuccessor() throws Exception {
            String r1 = freshLoginToken();
            String r1Jti = tokenService.validateToken(r1).jti();
            String familyId = tokenService.validateToken(r1).familyId();

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
                        RotationResult result = rotationService.rotate(r1);
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

            // Parent ends PENDING pointing at the single shared successor.
            assertEquals(RotationState.PENDING, stateIn(familyId, r1Jti));

            String jtiA = results.get(0).claims().jti();
            String jtiB = results.get(1).claims().jti();
            assertEquals(jtiA, jtiB);
            assertEquals(results.get(0).newRefreshJwt(), results.get(1).newRefreshJwt());
            assertEquals(RotationState.UNUSED_LATEST, stateIn(familyId, jtiA));

            // One racer rotated, the other replayed the memoized successor; no tombstones.
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_ROTATED));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_RETRY_REUSED));
            assertEquals(2, rotationEntriesIn(familyId));
        }
    }

    @Nested
    class CrossInstanceAtomicity {

        @Test
        void rotate_WhenTokenRetiredOnInstanceA_ReplayOnInstanceB_DetectsTheftOverSharedRedis() {
            StringRedisTemplate templateA = buildTemplate(SharedRedisContainerSupport.host(), SharedRedisContainerSupport.port());
            StringRedisTemplate templateB = buildTemplate(SharedRedisContainerSupport.host(), SharedRedisContainerSupport.port());

            RefreshRotationService instanceA = new RefreshRotationService(
                    templateA, tokenService, tokenService, jwtProperties, new SimpleMeterRegistry(),
                    true, RETRY_REUSE_WINDOW_MS);
            RefreshRotationService instanceB = new RefreshRotationService(
                    templateB, tokenService, tokenService, jwtProperties, new SimpleMeterRegistry(),
                    true, RETRY_REUSE_WINDOW_MS);

            String r1 = freshLoginToken();
            String familyId = tokenService.validateToken(r1).familyId();

            RotationResult.Rotated first = (RotationResult.Rotated) instanceA.rotate(r1);
            instanceA.rotate(first.newRefreshJwt()); // r1 -> RETIRED on A

            RotationResult replay = instanceB.rotate(r1);

            assertInstanceOf(RotationResult.Revoked.class, replay);
            assertEquals(familyId, ((RotationResult.Revoked) replay).familyId());
            assertTrue(instanceB.isFamilyRevoked(familyId));
        }
    }

    @Nested
    class LegacyAdmission {

        private final TestJwt legacyIssuer = new TestJwt();

        private RefreshRotationService serviceWith(boolean legacyAdmit) {
            return new RefreshRotationService(
                    sharedTemplate, legacyIssuer.service(), legacyIssuer.service(), jwtProperties, meterRegistry,
                    legacyAdmit, RETRY_REUSE_WINDOW_MS);
        }

        @Test
        void rotate_WhenLegacyAdmitOn_AdmitsAndRotates() {
            RefreshRotationService svc = serviceWith(true);

            RotationResult result = svc.rotate(legacyIssuer.legacyRefreshToken(userId));

            assertInstanceOf(RotationResult.Rotated.class, result);
            RotationResult.Rotated rotated = (RotationResult.Rotated) result;
            assertNotNull(rotated.claims().jti());
            assertNotNull(rotated.claims().familyId());
            assertNotNull(rotated.claims().parentJti());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_LEGACY_ADMITTED));
        }

        @Test
        void rotate_WhenLegacyAdmitOff_Rejects() {
            RefreshRotationService svc = serviceWith(false);

            String legacyToken = legacyIssuer.legacyRefreshToken(userId);

            RotationResult result = svc.rotate(legacyToken);

            assertInstanceOf(RotationResult.Rejected.class, result);
            assertEquals(RotationResult.Rejected.Reason.INVALID,
                    ((RotationResult.Rejected) result).reason());
            assertFalse(synthesizedFamilyExists(legacyIssuer.service().validateToken(legacyToken)));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID));
        }

        @Test
        void rotate_WhenSameLegacyTokenTwice_DeterministicFamily() {
            String legacyToken = legacyIssuer.legacyRefreshToken(userId);
            RefreshRotationService svc = serviceWith(true);

            RotationResult.Rotated first = (RotationResult.Rotated) svc.rotate(legacyToken);
            RotationResult.Rotated second = (RotationResult.Rotated) svc.rotate(legacyToken);

            assertEquals(first.claims().familyId(), second.claims().familyId());
            assertEquals(first.claims().jti(), second.claims().jti());
            assertEquals(first.newRefreshJwt(), second.newRefreshJwt());
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_LEGACY_ADMITTED));
            assertEquals(1.0, outcomeCount(RefreshRotationService.OUTCOME_RETRY_REUSED));
        }
    }
}
