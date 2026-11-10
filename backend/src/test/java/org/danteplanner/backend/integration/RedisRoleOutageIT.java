package org.danteplanner.backend.integration;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.lettuce.core.resource.ClientResources;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RedisRoleProbe;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.RealClockBounds;
import org.danteplanner.backend.support.RedisEndpoints;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class RedisRoleOutageIT extends CausalHarnessSupport {

    private static final String PROBE_THREAD = "redis-role-probe-1";

    private static final Duration OPEN_BOUND = RedisRoleProbe.PROBE_INTERVAL.plus(RedisRoleProbe.PROBE_TIMEOUT)
            .multipliedBy(RedisRoleProbe.FAILURE_THRESHOLD)
            .plus(RealClockBounds.SCHEDULING_SLACK);

    private static final Duration SERIAL_STARTUP_PROBING =
            RedisRoleProbe.STARTUP_PROBE_TIMEOUT.multipliedBy(RedisRole.values().length);

    @Autowired
    private RedisRoleHealth roleHealth;

    @Autowired
    private ClientResources redisClientResources;

    @Test
    void authLocalRole_WhenItsRouteBlackholesThenHeals_OpensWithinTheProbeBoundClosesAgainAndLeavesAuthClosed()
            throws IOException {
        Hang.until(() -> roleHealth.isAvailable(RedisRole.AUTH_LOCAL) && roleHealth.isAvailable(RedisRole.AUTH),
                "auth and auth-local answering their probes before the cut");
        try (LogCapture capture = LogCapture.onThread(PROBE_THREAD, RedisRoleProbe.class)) {
            long cut = System.nanoTime();
            blackholeAuthLocal();
            try {
                Hang.until(() -> !roleHealth.isAvailable(RedisRole.AUTH_LOCAL), "the probe opening redis-auth-local");
                assertThat(Duration.ofNanos(System.nanoTime() - cut)).isLessThan(OPEN_BOUND);
                assertThat(roleHealth.isAvailable(RedisRole.AUTH)).isTrue();
            } finally {
                healAuthLocal();
            }
            Hang.until(() -> roleHealth.isAvailable(RedisRole.AUTH_LOCAL), "the probe closing redis-auth-local");

            assertThat(transitionsOf(capture.events(), RedisRole.AUTH_LOCAL)).isNotEmpty();
            assertThat(transitionsOf(capture.events(), RedisRole.AUTH)).isEmpty();
            assertThat(roleHealth.isAvailable(RedisRole.AUTH)).isTrue();
        }
    }

    @Test
    void probeAtStartup_WhenEveryRoleIsBlackholed_ReturnsWithinOneStartupTimeoutWithEveryRoleUnavailable()
            throws IOException {
        RedisRoleHealth blackholed = new RedisRoleHealth();
        blackholeAuthLocal();
        try (RedisRoleProbe probe = new RedisRoleProbe(redisClientResources,
                RedisEndpoints.everyRoleAt(TOXIPROXY.getHost(), authLocalProxyPort()), blackholed)) {
            long start = System.nanoTime();
            probe.probeAtStartup();

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(SERIAL_STARTUP_PROBING);
        } finally {
            healAuthLocal();
        }
        assertThat(RedisRole.values()).noneMatch(blackholed::isAvailable);
    }

    private static List<ILoggingEvent> transitionsOf(List<ILoggingEvent> events, RedisRole role) {
        return events.stream()
                .filter(event -> Arrays.asList(event.getArgumentArray()).contains(role.breakerName()))
                .toList();
    }
}
