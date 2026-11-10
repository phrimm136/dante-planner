package org.danteplanner.backend.shared.ratelimit;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RedisRoleProbe;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.PongRedis;
import org.danteplanner.backend.support.RealClockBounds;
import org.danteplanner.backend.support.RedisEndpoints;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitStoreReconnectTest {

    private static final Duration BUCKET_TTL = Duration.ofMinutes(1);
    private static final Duration RECOVERY_BOUND = RedisRoleProbe.PROBE_INTERVAL
            .plus(RedisRoleProbe.PROBE_TIMEOUT)
            .plus(RealClockBounds.SCHEDULING_SLACK);

    private final ClientResources clientResources = DefaultClientResources.create();
    private final RedisRoleHealth health = new RedisRoleHealth();
    private PongRedis redis;

    @BeforeEach
    void setUp() throws IOException {
        redis = new PongRedis();
    }

    @AfterEach
    void tearDown() throws IOException {
        redis.close();
        var unused = clientResources.shutdown();
    }

    @Test
    void probe_WhenTheStoresConnectionDrops_ConnectsAFreshOneWithinTheProbeCadence() throws IOException {
        try (RateLimitStore store = new RateLimitStore(clientResources, redis.host(), redis.port(), BUCKET_TTL);
                RedisRoleProbe probe = new RedisRoleProbe(clientResources,
                        RedisEndpoints.everyRoleAt(redis.host(), redis.port()), health, List.of(store))) {
            probe.probeAtStartup();
            assertThat(health.isAvailable(RedisRole.RATE_LIMIT)).isTrue();
            assertThat(store.isConnected()).isTrue();

            redis.dropClients();
            Hang.until(() -> !store.isConnected(), "the rate-limit store noticing its dropped connection");
            long dropped = System.nanoTime();

            Hang.until(() -> {
                probe.probe();
                return store.isConnected() && health.isAvailable(RedisRole.RATE_LIMIT);
            }, "the probe connecting the rate-limit store again");

            assertThat(Duration.ofNanos(System.nanoTime() - dropped)).isLessThan(RECOVERY_BOUND);
        }
    }

    @Test
    void connect_WhenTheConnectionIsOpen_AnswersAtOnceWithoutAnotherAttempt() throws IOException {
        try (RateLimitStore store = new RateLimitStore(clientResources, redis.host(), redis.port(), BUCKET_TTL)) {
            Hang.until(() -> store.connect().isDone() && store.isConnected(), "the first connect");

            assertThat(store.connect()).isCompletedWithValue(null);
            assertThat(redis.acceptedConnections()).isEqualTo(1);
        }
    }
}
