package org.danteplanner.backend.auth.token;

import java.time.Instant;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.support.AuthReads;
import org.danteplanner.backend.support.TestJwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshRotationServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private StringRedisTemplate authStore;

    private final TestJwt jwt = new TestJwt(Instant.ofEpochMilli(1_790_000_000_000L));
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private RefreshRotationService rotation() {
        return new RefreshRotationService(AuthReads.writesTo(authStore), jwt.service(), jwt.service(),
                jwt.properties(), meterRegistry, jwt.clock(), 30_000L);
    }

    private void scriptAnswers(String result) {
        when(authStore.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(result);
    }

    private double outcomes(String outcome) {
        return meterRegistry.get("jwt_rotation_outcome_total").tag("outcome", outcome).counter().count();
    }

    private void assertScriptCalledWith(RefreshClaims presented, String parentArgument, RotationOutcome outcome) {
        RotationOutcome.Rotated rotated = (RotationOutcome.Rotated) outcome;
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.captor();
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(authStore).execute(any(RedisScript.class), keys.capture(), args.capture());

        assertThat(keys.getValue()).containsExactly(
                "rt:fam:{" + TestJwt.familyIdOf(presented) + "}", "uinv:7");
        assertThat(args.getValue()).containsExactly(
                TestJwt.jtiOf(presented), parentArgument, TestJwt.jtiOf(rotated.claims()),
                "1790604800000", "1790000000000", "604800000",
                rotated.newRefreshJwt(), "30000", "1790000000000");
        assertThat(rotated.claims().lineage()).isEqualTo(new RefreshLineage.Child(
                TestJwt.jtiOf(rotated.claims()), TestJwt.familyIdOf(presented), TestJwt.jtiOf(presented)));
    }

    @Test
    void rotate_WhenARootIsPresented_CallsTheScriptWithoutAParent() {
        scriptAnswers("ROTATED");
        String root = jwt.service().generateRefreshToken(USER_ID);

        RotationOutcome outcome = rotation().rotate(root);

        assertScriptCalledWith(jwt.service().validateRefreshToken(root), "", outcome);
        assertThat(outcomes("rotated")).isEqualTo(1.0);
    }

    @Test
    void rotate_WhenAChildIsPresented_CallsTheScriptWithItsParent() {
        scriptAnswers("SUPERSEDED");
        String child = jwt.service().generateRefreshToken(USER_ID, "fam-1", "jti-0");

        RotationOutcome outcome = rotation().rotate(child);

        assertScriptCalledWith(jwt.service().validateRefreshToken(child), "jti-0", outcome);
        assertThat(outcomes("retry_superseded")).isEqualTo(1.0);
    }

    @Test
    void rotate_WhenTheTokenHasNoFamily_RejectsItWithoutTouchingRedis() {
        RotationOutcome outcome = rotation().rotate(jwt.refreshTokenWithoutFamily(USER_ID));

        assertThat(outcome).isEqualTo(new RotationOutcome.Rejected(RotationOutcome.Rejected.Reason.INVALID));
        assertThat(outcomes("rejected_invalid")).isEqualTo(1.0);
        verifyNoInteractions(authStore);
    }
}
