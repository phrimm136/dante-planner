package org.danteplanner.backend.shared.redis;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.PongRedis;
import org.danteplanner.backend.support.RedisEndpoints;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRoleProbeHeldConnectionTest {

    private static final String REFUSED_HOST = "127.0.0.1";
    private static final int REFUSED_PORT = 1;
    private static final RedisRole ROLE = RedisRole.RATE_LIMIT;
    private static final int MANY_ROUNDS = 5;

    private final ClientResources clientResources = DefaultClientResources.create();
    private final RedisRoleHealth health = new RedisRoleHealth();
    private PongRedis redis;
    private RedisRoleProbe probe;

    @BeforeEach
    void setUp() throws IOException {
        redis = new PongRedis();
        RedisConnectionConfig connections = RedisEndpoints.everyRoleAt(REFUSED_HOST, REFUSED_PORT);
        RedisEndpoints.moveRole(connections, ROLE, redis.host(), redis.port());
        probe = new RedisRoleProbe(clientResources, connections, health, List.of());
    }

    @AfterEach
    void tearDown() throws IOException {
        probe.close();
        redis.close();
        var unused = clientResources.shutdown();
    }

    @Test
    void probe_WhenNewConnectionsAreSlowButTheHeldOneAnswers_KeepsTheRoleAvailableOnOneConnection() {
        probe.probeAtStartup();
        redis.holdNewConnections();

        for (int round = 0; round < MANY_ROUNDS; round++) {
            probe.probe();

            assertThat(health.isAvailable(ROLE)).as("round %d", round).isTrue();
        }
        assertThat(redis.acceptedConnections()).isEqualTo(1);
    }

    @Test
    void probe_WhenTheRoleIsHealthy_PingsEveryRoundOnOneConnection() {
        probe.probeAtStartup();
        int pingsBeforeTheRounds = redis.answeredPings();

        for (int round = 0; round < MANY_ROUNDS; round++) {
            probe.probe();
        }

        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(1);
        assertThat(redis.answeredPings() - pingsBeforeTheRounds).isEqualTo(MANY_ROUNDS);
    }

    @Test
    void probeAtStartup_WhenTheRoleAnswers_LeavesItsConnectionOpenForTheFirstRuntimeRound() {
        probe.probeAtStartup();
        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(1);

        redis.holdNewConnections();
        probe.probe();

        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(1);
    }

    @Test
    void probe_WhenTheServerDropsTheHeldConnection_ReconnectsWithoutOpeningTheBreaker() throws IOException {
        probe.probeAtStartup();
        List<CircuitBreaker.StateTransition> transitions = RoleOutages.transitionsFrom(health, ROLE);

        redis.dropClients();
        for (int round = 0; round < RedisRoleProbe.FAILURE_THRESHOLD; round++) {
            probe.probe();
        }

        assertThat(transitions).isEmpty();
        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(2);
    }

    @Test
    void probe_WhenTheHeldConnectionIsLostAndReconnectsAreRefused_OpensAfterTwoSilentRounds() throws IOException {
        probe.probeAtStartup();

        redis.close();
        probe.probe();
        assertThat(health.isAvailable(ROLE)).isTrue();
        probe.probe();

        assertThat(health.isAvailable(ROLE)).isFalse();
    }

    @Test
    void probe_WhenTheHeldConnectionGoesSilent_OpensAfterTwoRoundsAndReachesTheHealedServerOnANewConnection() {
        probe.probeAtStartup();

        redis.blackholeEstablished();
        redis.holdNewConnections();
        probe.probe();
        assertThat(health.isAvailable(ROLE)).isTrue();
        probe.probe();
        assertThat(health.isAvailable(ROLE)).isFalse();
        Hang.until(() -> redis.blackholedConnectionsClosedByClients() == 1, "the probe closing its silent connection");

        redis.releaseNewConnections();
        Hang.until(() -> {
            probe.probe();
            return health.isAvailable(ROLE);
        }, "the probe reaching the healed server on a new connection");
    }

    @Test
    void probeAtStartup_WhenRolesShareAHost_HoldsOneConnectionPerRole() throws IOException {
        try (PongRedis shared = new PongRedis();
                RedisRoleProbe sharing = new RedisRoleProbe(clientResources,
                        RedisEndpoints.everyRoleAt(shared.host(), shared.port()), new RedisRoleHealth(), List.of())) {
            sharing.probeAtStartup();
            sharing.probe();

            assertThat(shared.acceptedConnections()).isEqualTo(RedisRole.values().length);
        }
    }

    @Test
    void probe_WhenRunAfterClose_FailsEveryRoleWithoutWaitingForAConnection() {
        probe.probeAtStartup();

        probe.close();
        long start = System.nanoTime();
        probe.probe();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(RedisRoleProbe.PROBE_TIMEOUT);
        assertThat(redis.acceptedConnections()).isEqualTo(1);
    }
}
