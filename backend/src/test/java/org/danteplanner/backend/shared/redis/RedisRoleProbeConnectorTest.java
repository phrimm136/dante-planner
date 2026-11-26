package org.danteplanner.backend.shared.redis;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.PongRedis;
import org.danteplanner.backend.support.RedisEndpoints;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRoleProbeConnectorTest {

    private static final CompletableFuture<Void> CONNECTED = CompletableFuture.completedFuture(null);

    private final ClientResources clientResources = DefaultClientResources.create();
    private final RedisRoleHealth health = new RedisRoleHealth();
    private final AtomicReference<CompletableFuture<Void>> connection = new AtomicReference<>();
    private final AtomicInteger connects = new AtomicInteger();
    private PongRedis redis;

    @BeforeEach
    void setUp() throws IOException {
        redis = new PongRedis();
    }

    @AfterEach
    void tearDown() throws IOException {
        redis.close();
        var _ = clientResources.shutdown();
    }

    @Test
    void probeAtStartup_WhenEveryRoleAnswersAndTheConnectorHasConnected_LeavesEveryRoleAvailable() {
        connection.set(CONNECTED);

        try (RedisRoleProbe probe = probeWithRateLimitConnector(RoomyProbeTimeouts.BOTH_PHASES)) {
            probe.probeAtStartup();
        }

        assertThat(RedisRole.values()).allMatch(health::isAvailable);
        assertThat(connects).hasValue(1);
    }

    @Test
    void probeAtStartup_WhenTheRoleAnswersButItsConnectorFailed_OpensThatRoleAlone() {
        connection.set(CompletableFuture.failedFuture(new RedisConnectionException("refused")));

        try (RedisRoleProbe probe = probeWithRateLimitConnector(RoomyProbeTimeouts.BOTH_PHASES)) {
            probe.probeAtStartup();
        }

        assertThat(EnumSet.allOf(RedisRole.class)).filteredOn(role -> !health.isAvailable(role))
                .containsExactly(RedisRole.RATE_LIMIT);
    }

    @Test
    void probeAtStartup_WhenTheConnectorIsStillConnectingAtTheDeadline_OpensThatRole() {
        connection.set(new CompletableFuture<>());

        try (RedisRoleProbe probe = probeWithRateLimitConnector(RedisRoleProbe.Timeouts.PRODUCTION)) {
            probe.probeAtStartup();
            assertThat(health.isAvailable(RedisRole.RATE_LIMIT)).isFalse();

            Hang.until(() -> {
                probe.probe();
                return health.isAvailable(RedisRole.AUTH);
            }, "the auth role answering beside the pending connector");
        }

        assertThat(health.isAvailable(RedisRole.RATE_LIMIT)).isFalse();
    }

    @Test
    void probe_WhenTheConnectorConnectsAfterStartup_ClosesItsRoleOnTheNextProbe() {
        connection.set(CompletableFuture.failedFuture(new RedisConnectionException("refused")));

        try (RedisRoleProbe probe = probeWithRateLimitConnector(RoomyProbeTimeouts.BOTH_PHASES)) {
            probe.probeAtStartup();
            assertThat(health.isAvailable(RedisRole.RATE_LIMIT)).isFalse();

            connection.set(CONNECTED);
            probe.probe();
        }

        assertThat(health.isAvailable(RedisRole.RATE_LIMIT)).isTrue();
        assertThat(connects).hasValue(2);
    }

    private RedisRoleProbe probeWithRateLimitConnector(RedisRoleProbe.Timeouts timeouts) {
        RoleConnector rateLimit = new RoleConnector() {
            @Override
            public RedisRole role() {
                return RedisRole.RATE_LIMIT;
            }

            @Override
            public CompletableFuture<Void> connect() {
                connects.incrementAndGet();
                return connection.get();
            }
        };
        return new RedisRoleProbe(clientResources, RedisEndpoints.everyRoleAt(redis.host(), redis.port()), health,
                List.of(rateLimit), timeouts);
    }
}
