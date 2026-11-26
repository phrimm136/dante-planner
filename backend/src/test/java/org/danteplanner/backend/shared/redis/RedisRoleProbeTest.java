package org.danteplanner.backend.shared.redis;

import java.util.List;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import org.springframework.scheduling.annotation.Scheduled;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.shared.config.RedisConnectionConfig.Endpoints;
import org.danteplanner.backend.shared.config.RedisConnectionConfig.Endpoints.Endpoint;
import org.danteplanner.backend.support.Elapsed;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.PongRedis;
import org.danteplanner.backend.support.RedisEndpoints;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRoleProbeTest {

    private static final String REFUSED_HOST = "127.0.0.1";
    private static final int REFUSED_PORT = 1;
    private static final int REPEATED_FAILURES = 5;
    private static final int ACCEPT_BACKLOG = 16;
    private static final String PASSWORD = "role-secret";
    private static final String NOT_PONG = "OK";

    private final ClientResources clientResources = DefaultClientResources.create();
    private final RedisRoleHealth health = new RedisRoleHealth();
    private final RedisRoleProbe probe = new RedisRoleProbe(
            clientResources, RedisEndpoints.everyRoleAt(REFUSED_HOST, REFUSED_PORT), health, List.of(),
            RedisRoleProbe.Timeouts.PRODUCTION);

    @AfterEach
    void tearDown() {
        probe.close();
        clientResources.shutdown();
    }

    @Test
    void probeAtStartup_WhenEveryRoleIsRefused_MakesEveryRoleUnavailableAtOnce() {
        probe.probeAtStartup();

        assertThat(RedisRole.values()).noneMatch(health::isAvailable);
    }

    @Test
    void probeAtStartup_WhenEveryRoleAcceptsButNeverAnswers_WaitsOneStartupTimeoutForAllRolesTogether()
            throws IOException {
        try (ServerSocket neverAnswers = neverAnswering()) {
            RedisRoleHealth hanging = new RedisRoleHealth();
            try (RedisRoleProbe hangingProbe =
                    probeOver(neverAnswers, hanging, RedisRoleProbe.Timeouts.PRODUCTION)) {
                Elapsed elapsed = Elapsed.start();
                hangingProbe.probeAtStartup();

                elapsed.isAtLeast(RedisRoleProbe.STARTUP_PROBE_TIMEOUT);
                elapsed.isWithin(RedisRoleProbe.STARTUP_PROBE_TIMEOUT);
            }
            assertThat(RedisRole.values()).noneMatch(hanging::isAvailable);
        }
    }

    @Test
    void probe_WhenEveryRoleAcceptsButNeverAnswers_WaitsOneRuntimeTimeoutNotTheStartupTimeout() throws IOException {
        try (ServerSocket neverAnswers = neverAnswering()) {
            try (RedisRoleProbe hangingProbe =
                    probeOver(neverAnswers, new RedisRoleHealth(), RoomyProbeTimeouts.STARTUP_ONLY)) {
                Elapsed elapsed = Elapsed.start();
                hangingProbe.probe();

                elapsed.isAtLeast(RedisRoleProbe.PROBE_TIMEOUT);
                elapsed.isWithin(RedisRoleProbe.PROBE_TIMEOUT);
            }
        }
    }

    @Test
    void probeAtStartup_WhenTheProbeThreadIsInterruptedWhileWaiting_CountsEveryRoleAsSilentAndKeepsTheInterrupt()
            throws IOException {
        try (ServerSocket neverAnswers = neverAnswering()) {
            RedisRoleHealth interrupted = new RedisRoleHealth();
            try (RedisRoleProbe interruptedProbe =
                    probeOver(neverAnswers, interrupted, RedisRoleProbe.Timeouts.PRODUCTION)) {
                Thread.currentThread().interrupt();
                interruptedProbe.probeAtStartup();

                assertThat(Thread.interrupted()).isTrue();
            }
            assertThat(RedisRole.values()).noneMatch(interrupted::isAvailable);
        }
    }

    private static ServerSocket neverAnswering() throws IOException {
        return new ServerSocket(0, ACCEPT_BACKLOG, InetAddress.getLoopbackAddress());
    }

    private RedisRoleProbe probeOver(ServerSocket endpoint, RedisRoleHealth roleHealth,
            RedisRoleProbe.Timeouts timeouts) {
        return new RedisRoleProbe(clientResources, RedisEndpoints.everyRoleAt(
                endpoint.getInetAddress().getHostAddress(), endpoint.getLocalPort()), roleHealth, List.of(),
                timeouts);
    }

    @Test
    void probe_WhenARoleFailsOnce_KeepsItAvailable() {
        probe.probe();

        assertThat(RedisRole.values()).allMatch(health::isAvailable);
    }

    @Test
    void probe_WhenARoleFailsTwiceInARow_MakesItUnavailable() {
        probe.probe();
        probe.probe();

        assertThat(RedisRole.values()).noneMatch(health::isAvailable);
    }

    @Test
    void probe_WhenRolesKeepFailingAfterStartup_LogsOneOpeningPerRole() {
        try (LogCapture capture = LogCapture.onThisThread(RedisRoleProbe.class)) {
            probe.probeAtStartup();
            for (int check = 0; check < REPEATED_FAILURES; check++) {
                probe.probe();
            }

            assertThat(capture.at(Level.INFO)).hasSize(RedisRole.values().length);
            assertThat(capture.events()).hasSize(RedisRole.values().length);
        }
    }

    @Test
    void probeAtStartup_WhenEveryRoleRequiresItsConfiguredPassword_AuthenticatesAndKeepsEveryRoleAvailable()
            throws IOException {
        try (PongRedis redis = new PongRedis()) {
            redis.requirePassword(PASSWORD);
            Endpoint endpoint = Endpoint.at(redis.host(), redis.port());
            Endpoint passworded = new Endpoint(endpoint.host(), endpoint.port(), endpoint.bucketTtlSeconds(), PASSWORD);
            RedisRoleHealth authenticated = new RedisRoleHealth();
            try (RedisRoleProbe passwordedProbe = new RedisRoleProbe(clientResources, new RedisConnectionConfig(
                    new Endpoints(passworded, passworded, passworded, passworded)), authenticated, List.of(),
                    RoomyProbeTimeouts.BOTH_PHASES)) {
                passwordedProbe.probeAtStartup();
            }

            assertThat(RedisRole.values()).allMatch(authenticated::isAvailable);
        }
    }

    @Test
    void probeAtStartup_WhenEveryRoleAnswersPingWithSomethingOtherThanPong_MakesEveryRoleUnavailable()
            throws IOException {
        try (PongRedis redis = new PongRedis()) {
            redis.answerPingsWith(NOT_PONG);
            RedisRoleHealth misanswered = new RedisRoleHealth();
            try (RedisRoleProbe misansweredProbe = new RedisRoleProbe(clientResources,
                    RedisEndpoints.everyRoleAt(redis.host(), redis.port()), misanswered, List.of(),
                    RoomyProbeTimeouts.BOTH_PHASES)) {
                misansweredProbe.probeAtStartup();
            }

            assertThat(RedisRole.values()).noneMatch(misanswered::isAvailable);
        }
    }

    @Test
    void clientOptions_WhenBuilt_NeverReconnectsOnItsOwnAndBoundsTheConnectByTheStartupTimeout() {
        ClientOptions options = probe.clientOptions();

        assertThat(options.isAutoReconnect()).isFalse();
        assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(RedisRoleProbe.STARTUP_PROBE_TIMEOUT);
    }

    @Test
    void breakerName_WhenReadForEachRole_IsRedisFollowedByTheRole() {
        assertThat(RedisRole.values()).extracting(RedisRole::label)
                .containsExactly("auth", "auth-local", "rate-limit", "sse-local");
        assertThat(RedisRole.values()).extracting(RedisRole::breakerName)
                .containsExactly("redis-auth", "redis-auth-local", "redis-rate-limit", "redis-sse-local");
    }

    @Test
    void probe_WhenScheduled_RunsOnTheRoleProbeSchedulerWithoutAClusterLock() throws NoSuchMethodException {
        Scheduled scheduled = RedisRoleProbe.class.getMethod("probe").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.scheduler()).isEqualTo(RedisRoleConfig.PROBE_SCHEDULER);
        assertThat(RedisRoleProbe.class.getMethod("probe").getAnnotation(SchedulerLock.class)).isNull();
    }
}
