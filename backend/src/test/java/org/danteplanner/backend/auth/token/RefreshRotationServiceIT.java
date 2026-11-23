package org.danteplanner.backend.auth.token;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.support.AuthReads;
import org.danteplanner.backend.support.Hang;
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


import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.danteplanner.backend.user.api.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

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
        JwtProperties sharedKeys = new TestJwt().properties();

        byte[] aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);

        jwtProperties = new JwtProperties(sharedKeys.getPrivateKey(), sharedKeys.getPublicKey(),
                aesKey, 900000L, REFRESH_TOKEN_EXPIRY);

        tokenService = new JwtTokenService(jwtProperties, Clock.systemUTC());
        meterRegistry = new SimpleMeterRegistry();

        rotationService = serviceWithReuseWindow(RETRY_REUSE_WINDOW_MS);
        userId = TestDataFactory.nextUserId();
    }

    private RefreshRotationService serviceWithReuseWindow(long reuseWindowMs) {
        return new RefreshRotationService(
                AuthReads.writesTo(sharedTemplate), tokenService, tokenService, jwtProperties, meterRegistry,
                Clock.systemUTC(), reuseWindowMs);
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
            String r1Jti = TestJwt.jtiOf(tokenService.validateRefreshToken(r1));
            String familyId = TestJwt.familyIdOf(tokenService.validateRefreshToken(r1));

            RotationOutcome result = rotationService.rotate(r1);

            assertThat(result).isInstanceOf(RotationOutcome.Rotated.class);
            RotationOutcome.Rotated rotated = (RotationOutcome.Rotated) result;

            assertThat(stateIn(familyId, r1Jti)).isEqualTo(RotationState.PENDING);
            assertThat(stateIn(familyId, TestJwt.jtiOf(rotated.claims()))).isEqualTo(RotationState.UNUSED_LATEST);
            assertThat(TestJwt.familyIdOf(rotated.claims())).isEqualTo(familyId);
            assertThat(TestJwt.parentJtiOf(rotated.claims())).isEqualTo(r1Jti);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenSuccessorFirstUse_MarksParentRetiredAndDropsItsMemoizedSuccessor() {
            String r1 = freshLoginToken();
            String r1Jti = TestJwt.jtiOf(tokenService.validateRefreshToken(r1));

            RotationOutcome.Rotated first = (RotationOutcome.Rotated) rotationService.rotate(r1);
            String r2 = first.newRefreshJwt();
            String r2Jti = TestJwt.jtiOf(first.claims());
            String familyKey = RefreshRotationService.familyKey(TestJwt.familyIdOf(first.claims()));
            assertThat(sharedTemplate.opsForHash().hasKey(familyKey, "succjwt:" + r1Jti)).isTrue();

            RotationOutcome.Rotated second = (RotationOutcome.Rotated) rotationService.rotate(r2);

            assertThat(stateIn(TestJwt.familyIdOf(first.claims()), r1Jti)).isEqualTo(RotationState.RETIRED);
            assertThat(stateIn(TestJwt.familyIdOf(first.claims()), r2Jti)).isEqualTo(RotationState.PENDING);
            assertThat(TestJwt.parentJtiOf(second.claims())).isEqualTo(r2Jti);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(2.0);
            assertThat(sharedTemplate.opsForHash().hasKey(familyKey, "succjwt:" + r1Jti)).isFalse();
        }

        @Test
        void rotate_WhenRotated_CarriesSuccessorJwt() {
            String r1 = freshLoginToken();

            RotationOutcome.Rotated rotated = (RotationOutcome.Rotated) rotationService.rotate(r1);

            // The caller writes this value as the refresh cookie, so it has to be the token the
            // reported claims belong to.
            assertThat(TestJwt.jtiOf(tokenService.validateRefreshToken(rotated.newRefreshJwt()))).isEqualTo(TestJwt.jtiOf(rotated.claims()));
        }
    }

    @Nested
    class Retry {

        @Test
        void rotate_WhenParentRepresentedWithinWindow_ReusesSuccessor() {
            String r1 = freshLoginToken();
            String familyId = TestJwt.familyIdOf(tokenService.validateRefreshToken(r1));

            RotationOutcome.Rotated firstAttempt = (RotationOutcome.Rotated) rotationService.rotate(r1);

            RotationOutcome.Rotated retry = (RotationOutcome.Rotated) rotationService.rotate(r1);

            assertThat(TestJwt.jtiOf(retry.claims())).isEqualTo(TestJwt.jtiOf(firstAttempt.claims()));
            assertThat(retry.newRefreshJwt()).isEqualTo(firstAttempt.newRefreshJwt());
            assertThat(stateIn(familyId, TestJwt.jtiOf(retry.claims()))).isEqualTo(RotationState.UNUSED_LATEST);
            assertThat(stateIn(familyId, TestJwt.jtiOf(tokenService.validateRefreshToken(r1)))).isEqualTo(RotationState.PENDING);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(1.0);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_RETRY_REUSED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenParentRepresentedOutsideWindow_SupersedesPredecessor() {
            RefreshRotationService noReuse = serviceWithReuseWindow(0L);
            String r1 = freshLoginToken();
            String familyId = TestJwt.familyIdOf(tokenService.validateRefreshToken(r1));

            RotationOutcome.Rotated firstAttempt = (RotationOutcome.Rotated) noReuse.rotate(r1);
            String r2First = TestJwt.jtiOf(firstAttempt.claims());

            RotationOutcome.Rotated retry = (RotationOutcome.Rotated) noReuse.rotate(r1);
            String r2Retry = TestJwt.jtiOf(retry.claims());

            assertThat(r2Retry).isNotEqualTo(r2First);
            assertThat(stateIn(familyId, r2First)).isEqualTo(RotationState.SUPERSEDED);
            assertThat(stateIn(familyId, r2Retry)).isEqualTo(RotationState.UNUSED_LATEST);
            assertThat(stateIn(familyId, TestJwt.jtiOf(tokenService.validateRefreshToken(r1)))).isEqualTo(RotationState.PENDING);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(1.0);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_RETRY_SUPERSEDED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenRetrySuccessor_RotatesNormally() {
            String r1 = freshLoginToken();
            rotationService.rotate(r1);
            RotationOutcome.Rotated retry = (RotationOutcome.Rotated) rotationService.rotate(r1);

            RotationOutcome result = rotationService.rotate(retry.newRefreshJwt());

            assertThat(result).isInstanceOf(RotationOutcome.Rotated.class);
        }
    }

    @Nested
    class TheftDetection {

        @Test
        void rotate_WhenRetiredToken_RevokesFamily() {
            String r1 = freshLoginToken();
            String familyId = TestJwt.familyIdOf(tokenService.validateRefreshToken(r1));

            RotationOutcome.Rotated first = (RotationOutcome.Rotated) rotationService.rotate(r1);
            rotationService.rotate(first.newRefreshJwt()); // R1 -> RETIRED

            RotationOutcome result = rotationService.rotate(r1);

            assertThat(result).isInstanceOf(RotationOutcome.Revoked.class);
            assertThat(((RotationOutcome.Revoked) result).familyId()).isEqualTo(familyId);
            assertThat(TokenStoreKeys.isFamilyRevoked(sharedTemplate, familyId)).isTrue();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenLegacyUsedSpelling_RevokesFamily() {
            String r1 = freshLoginToken();
            RefreshClaims claims = tokenService.validateRefreshToken(r1);

            sharedTemplate.opsForHash().put(
                    "rt:fam:{" + TestJwt.familyIdOf(claims) + "}", TestJwt.jtiOf(claims),
                    "USED||" + claims.expiration().getTime());

            RotationOutcome result = rotationService.rotate(r1);

            assertThat(result).isInstanceOf(RotationOutcome.Revoked.class);
            assertThat(TokenStoreKeys.isFamilyRevoked(sharedTemplate, TestJwt.familyIdOf(claims))).isTrue();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenSupersededToken_RevokesFamily() {
            RefreshRotationService noReuse = serviceWithReuseWindow(0L);
            String r1 = freshLoginToken();
            String familyId = TestJwt.familyIdOf(tokenService.validateRefreshToken(r1));

            RotationOutcome.Rotated attempt1 = (RotationOutcome.Rotated) noReuse.rotate(r1);
            String r2Superseded = attempt1.newRefreshJwt();
            noReuse.rotate(r1); // outside the window: supersedes r2Superseded

            RotationOutcome result = noReuse.rotate(r2Superseded);

            assertThat(result).isInstanceOf(RotationOutcome.Revoked.class);
            assertThat(TokenStoreKeys.isFamilyRevoked(sharedTemplate, familyId)).isTrue();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED)).isEqualTo(1.0);
        }
    }

    @Nested
    class FamilyRevocation {

        @Test
        void rotate_WhenRevokedFamily_RejectsAllTokens() {
            String r1 = freshLoginToken();
            String familyId = TestJwt.familyIdOf(tokenService.validateRefreshToken(r1));
            RotationOutcome.Rotated rotated = (RotationOutcome.Rotated) rotationService.rotate(r1);
            String r2 = rotated.newRefreshJwt();

            rotationService.revokeFamily(familyId);

            RotationOutcome result = rotationService.rotate(r2);

            assertThat(result).isInstanceOf(RotationOutcome.Rejected.class);
            assertThat(((RotationOutcome.Rejected) result).reason())
                    .isEqualTo(RotationOutcome.Rejected.Reason.REVOKED_FAMILY);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_REJECTED_REVOKED_FAMILY)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenInvalidToken_Rejected() {
            RotationOutcome result = rotationService.rotate("not.a.jwt");

            assertThat(result).isInstanceOf(RotationOutcome.Rejected.class);
            assertThat(((RotationOutcome.Rejected) result).reason()).isEqualTo(RotationOutcome.Rejected.Reason.INVALID);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenAccessToken_Rejected() {
            String accessToken = tokenService.generateAccessToken(userId, UserRole.NORMAL);

            RotationOutcome result = rotationService.rotate(accessToken);

            assertThat(result).isInstanceOf(RotationOutcome.Rejected.class);
            assertThat(((RotationOutcome.Rejected) result).reason()).isEqualTo(RotationOutcome.Rejected.Reason.INVALID);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID)).isEqualTo(1.0);
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

            RotationOutcome result = rotationService.rotate(token);

            assertThat(result).isInstanceOf(RotationOutcome.Rejected.class);
            assertThat(((RotationOutcome.Rejected) result).reason())
                    .isEqualTo(RotationOutcome.Rejected.Reason.REVOKED_FAMILY);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_REJECTED_USER_INVALIDATED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenIssuedAfterInvalidation_Rotates() {
            sharedTemplate.opsForValue().set(
                    uinvKey(), String.valueOf(System.currentTimeMillis() - 60_000));
            String token = freshLoginToken();

            RotationOutcome result = rotationService.rotate(token);

            assertThat(result).isInstanceOf(RotationOutcome.Rotated.class);
        }

        @Test
        void rotate_WhenFamilyHealthyButUserInvalidated_Rejected() {
            String token = freshLoginToken();
            RotationOutcome.Rotated first =
                    (RotationOutcome.Rotated) rotationService.rotate(token);
            sharedTemplate.opsForValue().set(
                    uinvKey(), String.valueOf(System.currentTimeMillis() + 60_000));

            RotationOutcome result = rotationService.rotate(first.newRefreshJwt());

            assertThat(result).isInstanceOf(RotationOutcome.Rejected.class);
        }
    }

    @Nested
    class ConcurrentRotation {

        @Test
        void rotate_WhenConcurrentSameParent_ConvergesOnOneSuccessor() throws Exception {
            String r1 = freshLoginToken();
            String r1Jti = TestJwt.jtiOf(tokenService.validateRefreshToken(r1));
            String familyId = TestJwt.familyIdOf(tokenService.validateRefreshToken(r1));

            int threads = 2;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CyclicBarrier barrier = new CyclicBarrier(threads);
            CountDownLatch done = new CountDownLatch(threads);
            List<RotationOutcome.Rotated> results = new ArrayList<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await();
                        RotationOutcome result = rotationService.rotate(r1);
                        synchronized (results) {
                            results.add((RotationOutcome.Rotated) result);
                        }
                    } catch (Throwable t) {
                        failure.set(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            Hang.await(done, "both concurrent rotations");
            pool.shutdownNow();
            assertThat(failure.get()).as(() -> "thread failed: " + failure.get()).isNull();

            assertThat(results.size()).isEqualTo(2);

            // Parent ends PENDING pointing at the single shared successor.
            assertThat(stateIn(familyId, r1Jti)).isEqualTo(RotationState.PENDING);

            String jtiA = TestJwt.jtiOf(results.get(0).claims());
            String jtiB = TestJwt.jtiOf(results.get(1).claims());
            assertThat(jtiB).isEqualTo(jtiA);
            assertThat(results.get(1).newRefreshJwt()).isEqualTo(results.get(0).newRefreshJwt());
            assertThat(stateIn(familyId, jtiA)).isEqualTo(RotationState.UNUSED_LATEST);

            // One racer rotated, the other replayed the memoized successor; no tombstones.
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(1.0);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_RETRY_REUSED)).isEqualTo(1.0);
            assertThat(rotationEntriesIn(familyId)).isEqualTo(2);
        }
    }

    @Nested
    class CrossInstanceAtomicity {

        @Test
        void rotate_WhenTokenRetiredOnInstanceA_ReplayOnInstanceB_DetectsTheftOverSharedRedis() {
            StringRedisTemplate templateA = buildTemplate(SharedRedisContainerSupport.host(), SharedRedisContainerSupport.port());
            StringRedisTemplate templateB = buildTemplate(SharedRedisContainerSupport.host(), SharedRedisContainerSupport.port());

            RefreshRotationService instanceA = new RefreshRotationService(
                    AuthReads.writesTo(templateA), tokenService, tokenService, jwtProperties, new SimpleMeterRegistry(),
                    Clock.systemUTC(), RETRY_REUSE_WINDOW_MS);
            RefreshRotationService instanceB = new RefreshRotationService(
                    AuthReads.writesTo(templateB), tokenService, tokenService, jwtProperties, new SimpleMeterRegistry(),
                    Clock.systemUTC(), RETRY_REUSE_WINDOW_MS);

            String r1 = freshLoginToken();
            String familyId = TestJwt.familyIdOf(tokenService.validateRefreshToken(r1));

            RotationOutcome.Rotated first = (RotationOutcome.Rotated) instanceA.rotate(r1);
            instanceA.rotate(first.newRefreshJwt()); // r1 -> RETIRED on A

            RotationOutcome replay = instanceB.rotate(r1);

            assertThat(replay).isInstanceOf(RotationOutcome.Revoked.class);
            assertThat(((RotationOutcome.Revoked) replay).familyId()).isEqualTo(familyId);
            assertThat(TokenStoreKeys.isFamilyRevoked(templateB, familyId)).isTrue();
        }
    }
}
