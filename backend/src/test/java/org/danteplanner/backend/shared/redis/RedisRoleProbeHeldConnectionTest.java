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
import org.danteplanner.backend.support.Elapsed;
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
    }

    @AfterEach
    void tearDown() throws IOException {
        if (probe != null) {
            probe.close();
        }
        redis.close();
        var unused = clientResources.shutdown();
    }

    @Test
    void probe_WhenNewConnectionsAreSlowButTheHeldOneAnswers_KeepsTheRoleAvailableOnOneConnection() {
        startWithHeldConnection(RoomyProbeTimeouts.BOTH_PHASES);
        redis.holdNewConnections();

        for (int round = 0; round < MANY_ROUNDS; round++) {
            probe.probe();

            assertThat(health.isAvailable(ROLE)).as("round %d", round).isTrue();
        }
        assertThat(redis.acceptedConnections()).isEqualTo(1);
    }

    @Test
    void probe_WhenOneReplyIsLateAndNewConnectionsAreSlow_KeepsTheHeldConnectionAndTheBreakerClosed() {
        startWithHeldConnection(RoomyProbeTimeouts.STARTUP_ONLY);
        List<CircuitBreaker.StateTransition> transitions = RoleOutages.transitionsFrom(health, ROLE);
        redis.holdNewConnections();

        redis.delayNextPingReply();
        probe.probe();
        redis.releaseDelayedPingReply();
        for (int round = 0; round < MANY_ROUNDS; round++) {
            probe.probe();
        }

        assertThat(transitions).isEmpty();
        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(1);
    }

    @Test
    void probe_WhenTheRoleIsHealthy_PingsEveryRoundOnOneConnection() {
        startWithHeldConnection(RoomyProbeTimeouts.BOTH_PHASES);
        int pingsBeforeTheRounds = redis.answeredPings();

        for (int round = 0; round < MANY_ROUNDS; round++) {
            probe.probe();
        }

        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(1);
        Hang.until(() -> redis.answeredPings() - pingsBeforeTheRounds == MANY_ROUNDS,
                "every round's PING answered on the held connection");
    }

    @Test
    void probeAtStartup_WhenTheRoleAnswers_LeavesItsConnectionOpenForTheFirstRuntimeRound() {
        startWithHeldConnection(RoomyProbeTimeouts.BOTH_PHASES);

        redis.holdNewConnections();
        probe.probe();

        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(1);
    }

    @Test
    void probe_WhenTheServerDropsTheHeldConnection_ReconnectsWithoutOpeningTheBreaker() throws IOException {
        startWithHeldConnection(RoomyProbeTimeouts.BOTH_PHASES);
        List<CircuitBreaker.StateTransition> transitions = RoleOutages.transitionsFrom(health, ROLE);

        redis.dropClients();
        Hang.until(() -> {
            probe.probe();
            return redis.acceptedConnections() == 2;
        }, "the probe reconnecting once it has noticed the dropped connection");
        probe.probe();

        assertThat(transitions).isEmpty();
        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(2);
    }

    @Test
    void probe_WhenTheHeldConnectionIsLostAndReconnectsAreRefused_OpensAfterTwoSilentRounds() throws IOException {
        startWithHeldConnection(RoomyProbeTimeouts.BOTH_PHASES);

        redis.close();
        probe.probe();
        assertThat(health.isAvailable(ROLE)).isTrue();
        probe.probe();

        assertThat(health.isAvailable(ROLE)).isFalse();
    }

    @Test
    void probe_WhenTheHeldConnectionGoesSilent_OpensAfterTwoRoundsAndReachesTheHealedServerOnANewConnection() {
        startWithHeldConnection(RoomyProbeTimeouts.STARTUP_ONLY);

        redis.blackholeEstablished();
        redis.holdNewConnections();
        probe.probe();
        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(1);
        probe.probe();
        assertThat(health.isAvailable(ROLE)).isFalse();
        assertThat(redis.acceptedConnections()).isEqualTo(1);
        probe.probe();
        Hang.until(() -> redis.acceptedConnections() == 2, "the probe starting a new connection after the opening");
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
                        RedisEndpoints.everyRoleAt(shared.host(), shared.port()), new RedisRoleHealth(), List.of(),
                        RoomyProbeTimeouts.BOTH_PHASES)) {
            sharing.probeAtStartup();
            sharing.probe();

            assertThat(shared.acceptedConnections()).isEqualTo(RedisRole.values().length);
        }
    }

    @Test
    void probe_WhenRunAfterClose_FailsEveryRoleWithoutWaitingForAConnection() {
        startWithHeldConnection(RoomyProbeTimeouts.BOTH_PHASES);

        probe.close();
        Elapsed elapsed = Elapsed.start();
        probe.probe();

        elapsed.isWithin(Duration.ZERO);
        assertThat(redis.acceptedConnections()).isEqualTo(1);
    }

    @Test
    void close_WhenAConnectIsStillPending_ShutsItsSocketWithTheClient() throws IOException {
        startWithHeldConnection(RoomyProbeTimeouts.STARTUP_ONLY);
        redis.holdNewConnections();
        redis.dropClients();
        Hang.until(() -> {
            probe.probe();
            return redis.acceptedConnections() == 2;
        }, "the probe starting a connect once it has noticed the dropped connection");
        int pingsBeforeClose = redis.answeredPings();

        probe.close();
        redis.releaseNewConnections();

        Hang.until(() -> redis.connectionsClosedByClients() == 1 || redis.answeredPings() > pingsBeforeClose,
                "the pending connect's socket closing or finishing its handshake");
        assertThat(redis.answeredPings()).isEqualTo(pingsBeforeClose);
        assertThat(redis.connectionsClosedByClients()).isEqualTo(1);
    }

    private void startWithHeldConnection(RedisRoleProbe.Timeouts timeouts) {
        RedisConnectionConfig connections = RedisEndpoints.withRoleAt(
                RedisEndpoints.everyRoleAt(REFUSED_HOST, REFUSED_PORT), ROLE, redis.host(), redis.port());
        probe = new RedisRoleProbe(clientResources, connections, health, List.of(), timeouts);
        probe.probeAtStartup();
        assertThat(health.isAvailable(ROLE)).isTrue();
        assertThat(redis.acceptedConnections()).isEqualTo(1);
    }
}
