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

            assertThat(result).isInstanceOf(RotationResult.Rotated.class);
            RotationResult.Rotated rotated = (RotationResult.Rotated) result;

            assertThat(stateIn(familyId, r1Jti)).isEqualTo(RotationState.PENDING);
            assertThat(stateIn(familyId, rotated.claims().jti())).isEqualTo(RotationState.UNUSED_LATEST);
            assertThat(rotated.claims().familyId()).isEqualTo(familyId);
            assertThat(rotated.claims().parentJti()).isEqualTo(r1Jti);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenSuccessorFirstUse_MarksParentRetiredAndDropsItsMemoizedSuccessor() {
            String r1 = freshLoginToken();
            String r1Jti = tokenService.validateToken(r1).jti();

            RotationResult.Rotated first = (RotationResult.Rotated) rotationService.rotate(r1);
            String r2 = first.newRefreshJwt();
            String r2Jti = first.claims().jti();
            String familyKey = RefreshRotationService.familyKey(first.claims().familyId());
            assertThat(sharedTemplate.opsForHash().hasKey(familyKey, "succjwt:" + r1Jti)).isTrue();

            RotationResult.Rotated second = (RotationResult.Rotated) rotationService.rotate(r2);

            assertThat(stateIn(first.claims().familyId(), r1Jti)).isEqualTo(RotationState.RETIRED);
            assertThat(stateIn(first.claims().familyId(), r2Jti)).isEqualTo(RotationState.PENDING);
            assertThat(second.claims().parentJti()).isEqualTo(r2Jti);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(2.0);
            assertThat(sharedTemplate.opsForHash().hasKey(familyKey, "succjwt:" + r1Jti)).isFalse();
        }

        @Test
        void rotate_WhenRotated_CarriesSuccessorJwt() {
            String r1 = freshLoginToken();

            RotationResult.Rotated rotated = (RotationResult.Rotated) rotationService.rotate(r1);

            // The caller writes this value as the refresh cookie, so it has to be the token the
            // reported claims belong to.
            assertThat(tokenService.validateToken(rotated.newRefreshJwt()).jti()).isEqualTo(rotated.claims().jti());
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

            assertThat(retry.claims().jti()).isEqualTo(firstAttempt.claims().jti());
            assertThat(retry.newRefreshJwt()).isEqualTo(firstAttempt.newRefreshJwt());
            assertThat(stateIn(familyId, retry.claims().jti())).isEqualTo(RotationState.UNUSED_LATEST);
            assertThat(stateIn(familyId, tokenService.validateToken(r1).jti())).isEqualTo(RotationState.PENDING);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(1.0);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_RETRY_REUSED)).isEqualTo(1.0);
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

            assertThat(r2Retry).isNotEqualTo(r2First);
            assertThat(stateIn(familyId, r2First)).isEqualTo(RotationState.SUPERSEDED);
            assertThat(stateIn(familyId, r2Retry)).isEqualTo(RotationState.UNUSED_LATEST);
            assertThat(stateIn(familyId, tokenService.validateToken(r1).jti())).isEqualTo(RotationState.PENDING);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(1.0);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_RETRY_SUPERSEDED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenRetrySuccessor_RotatesNormally() {
            String r1 = freshLoginToken();
            rotationService.rotate(r1);
            RotationResult.Rotated retry = (RotationResult.Rotated) rotationService.rotate(r1);

            RotationResult result = rotationService.rotate(retry.newRefreshJwt());

            assertThat(result).isInstanceOf(RotationResult.Rotated.class);
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

            assertThat(result).isInstanceOf(RotationResult.Revoked.class);
            assertThat(((RotationResult.Revoked) result).familyId()).isEqualTo(familyId);
            assertThat(rotationService.isFamilyRevoked(familyId)).isTrue();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenLegacyUsedSpelling_RevokesFamily() {
            String r1 = freshLoginToken();
            TokenClaims claims = tokenService.validateToken(r1);

            sharedTemplate.opsForHash().put(
                    "rt:fam:{" + claims.familyId() + "}", claims.jti(),
                    "USED||" + claims.expiration().getTime());

            RotationResult result = rotationService.rotate(r1);

            assertThat(result).isInstanceOf(RotationResult.Revoked.class);
            assertThat(rotationService.isFamilyRevoked(claims.familyId())).isTrue();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED)).isEqualTo(1.0);
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

            assertThat(result).isInstanceOf(RotationResult.Revoked.class);
            assertThat(noReuse.isFamilyRevoked(familyId)).isTrue();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_THEFT_REVOKED)).isEqualTo(1.0);
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

            assertThat(result).isInstanceOf(RotationResult.Rejected.class);
            assertThat(((RotationResult.Rejected) result).reason())
                    .isEqualTo(RotationResult.Rejected.Reason.REVOKED_FAMILY);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_REJECTED_REVOKED_FAMILY)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenInvalidToken_Rejected() {
            RotationResult result = rotationService.rotate("not.a.jwt");

            assertThat(result).isInstanceOf(RotationResult.Rejected.class);
            assertThat(((RotationResult.Rejected) result).reason()).isEqualTo(RotationResult.Rejected.Reason.INVALID);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenAccessToken_RejectedEvenWithLegacyAdmit() {
            String accessToken = tokenService.generateAccessToken(userId, UserRole.NORMAL);

            RotationResult result = rotationService.rotate(accessToken);

            assertThat(result).isInstanceOf(RotationResult.Rejected.class);
            assertThat(((RotationResult.Rejected) result).reason()).isEqualTo(RotationResult.Rejected.Reason.INVALID);
            assertThat(synthesizedFamilyExists(tokenService.validateToken(accessToken))).isFalse();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenAccessTokenClaims_RejectedAndFamilyUntouched() {
            RefreshRotationService svc = new RefreshRotationService(
                    sharedTemplate, tokenService, tokenService, jwtProperties, meterRegistry,
                    true, RETRY_REUSE_WINDOW_MS);
            String accessToken = tokenService.generateAccessToken(userId, UserRole.NORMAL);

            RotationResult result = svc.rotate(accessToken);

            assertThat(result).isInstanceOf(RotationResult.Rejected.class);
            assertThat(((RotationResult.Rejected) result).reason()).isEqualTo(RotationResult.Rejected.Reason.INVALID);
            assertThat(synthesizedFamilyExists(tokenService.validateToken(accessToken))).isFalse();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_ROTATED)).isEqualTo(0.0);
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

            RotationResult result = rotationService.rotate(token);

            assertThat(result).isInstanceOf(RotationResult.Rejected.class);
            assertThat(((RotationResult.Rejected) result).reason())
                    .isEqualTo(RotationResult.Rejected.Reason.REVOKED_FAMILY);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_REJECTED_USER_INVALIDATED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenIssuedAfterInvalidation_Rotates() {
            sharedTemplate.opsForValue().set(
                    uinvKey(), String.valueOf(System.currentTimeMillis() - 60_000));
            String token = freshLoginToken();

            RotationResult result = rotationService.rotate(token);

            assertThat(result).isInstanceOf(RotationResult.Rotated.class);
        }

        @Test
        void rotate_WhenFamilyHealthyButUserInvalidated_Rejected() {
            String token = freshLoginToken();
            RotationResult.Rotated first =
                    (RotationResult.Rotated) rotationService.rotate(token);
            sharedTemplate.opsForValue().set(
                    uinvKey(), String.valueOf(System.currentTimeMillis() + 60_000));

            RotationResult result = rotationService.rotate(first.newRefreshJwt());

            assertThat(result).isInstanceOf(RotationResult.Rejected.class);
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

            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(failure.get()).as(() -> "thread failed: " + failure.get()).isNull();

            assertThat(results.size()).isEqualTo(2);

            // Parent ends PENDING pointing at the single shared successor.
            assertThat(stateIn(familyId, r1Jti)).isEqualTo(RotationState.PENDING);

            String jtiA = results.get(0).claims().jti();
            String jtiB = results.get(1).claims().jti();
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

            assertThat(replay).isInstanceOf(RotationResult.Revoked.class);
            assertThat(((RotationResult.Revoked) replay).familyId()).isEqualTo(familyId);
            assertThat(instanceB.isFamilyRevoked(familyId)).isTrue();
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

            assertThat(result).isInstanceOf(RotationResult.Rotated.class);
            RotationResult.Rotated rotated = (RotationResult.Rotated) result;
            assertThat(rotated.claims().jti()).isNotNull();
            assertThat(rotated.claims().familyId()).isNotNull();
            assertThat(rotated.claims().parentJti()).isNotNull();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_LEGACY_ADMITTED)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenLegacyAdmitOff_Rejects() {
            RefreshRotationService svc = serviceWith(false);

            String legacyToken = legacyIssuer.legacyRefreshToken(userId);

            RotationResult result = svc.rotate(legacyToken);

            assertThat(result).isInstanceOf(RotationResult.Rejected.class);
            assertThat(((RotationResult.Rejected) result).reason()).isEqualTo(RotationResult.Rejected.Reason.INVALID);
            assertThat(synthesizedFamilyExists(legacyIssuer.service().validateToken(legacyToken))).isFalse();
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_REJECTED_INVALID)).isEqualTo(1.0);
        }

        @Test
        void rotate_WhenSameLegacyTokenTwice_DeterministicFamily() {
            String legacyToken = legacyIssuer.legacyRefreshToken(userId);
            RefreshRotationService svc = serviceWith(true);

            RotationResult.Rotated first = (RotationResult.Rotated) svc.rotate(legacyToken);
            RotationResult.Rotated second = (RotationResult.Rotated) svc.rotate(legacyToken);

            assertThat(second.claims().familyId()).isEqualTo(first.claims().familyId());
            assertThat(second.claims().jti()).isEqualTo(first.claims().jti());
            assertThat(second.newRefreshJwt()).isEqualTo(first.newRefreshJwt());
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_LEGACY_ADMITTED)).isEqualTo(1.0);
            assertThat(outcomeCount(RefreshRotationService.OUTCOME_RETRY_REUSED)).isEqualTo(1.0);
        }
    }
}
