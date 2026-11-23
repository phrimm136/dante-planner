package org.danteplanner.backend.auth.token;

import java.util.UUID;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RoleOutages;
import org.danteplanner.backend.support.AuthReads;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.TestJwt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RefreshRotationServiceOpenRoleTest {

    private static final long RETRY_REUSE_WINDOW_MS = 30000L;

    @Mock
    private StringRedisTemplate authStore;

    private final TestJwt jwt = new TestJwt();

    @Test
    void rotate_WhenTheAuthRoleIsOpen_FailsAtOnceWithAConnectionFailureWithoutTouchingTheStore() {
        RefreshRotationService refused = new RefreshRotationService(
                AuthReads.writesTo(authStore, RoleOutages.withOpen(RedisRole.AUTH)), jwt.service(), jwt.service(),
                jwt.properties(), new SimpleMeterRegistry(), jwt.clock(), RETRY_REUSE_WINDOW_MS);
        String refreshToken = jwt.service().generateRefreshToken(
                TestDataFactory.nextUserId(), UUID.randomUUID().toString(), null);

        assertThatThrownBy(() -> refused.rotate(refreshToken))
                .isExactlyInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining("breaker open");
        verifyNoInteractions(authStore);
    }
}
