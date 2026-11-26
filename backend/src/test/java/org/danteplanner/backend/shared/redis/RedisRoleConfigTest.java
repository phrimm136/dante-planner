package org.danteplanner.backend.shared.redis;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.support.RedisEndpoints;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RedisRoleConfigTest {

    private static final String BREAKER_STATE_GAUGE = "resilience4j.circuitbreaker.state";
    private static final String REFUSED_HOST = "127.0.0.1";
    private static final int REFUSED_PORT = 1;

    @Mock
    private StringRedisTemplate authLocalStore;

    @Mock
    private StringRedisTemplate authStore;

    private final RedisRoleConfig config = new RedisRoleConfig();

    @Test
    void redisRoleProbeScheduler_WhenStarted_RunsOneProbeThreadAndDropsDelayedTasksOnShutdown() {
        ThreadPoolTaskScheduler scheduler = config.redisRoleProbeScheduler();
        scheduler.initialize();
        try {
            ScheduledThreadPoolExecutor executor = scheduler.getScheduledThreadPoolExecutor();

            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("redis-role-probe-");
            assertThat(executor.getRemoveOnCancelPolicy()).isTrue();
            assertThat(executor.getExecuteExistingDelayedTasksAfterShutdownPolicy()).isFalse();
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void redisRoleBreakerMetrics_WhenBound_ExportsAStateGaugeForEveryRoleBreaker() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();

        config.redisRoleBreakerMetrics(config.redisRoleHealth()).bindTo(meters);

        assertThat(RedisRole.values()).allSatisfy(role -> assertThat(
                meters.find(BREAKER_STATE_GAUGE).tag("name", role.breakerName()).gauges()).isNotEmpty());
    }

    @Test
    void redisRoleProbe_WhenItsRolesAreRefused_MovesTheBreakersOfTheHealthItWasGiven() {
        ClientResources clientResources = DefaultClientResources.create();
        RedisRoleHealth health = config.redisRoleHealth();
        try (RedisRoleProbe probe = config.redisRoleProbe(
                clientResources, RedisEndpoints.everyRoleAt(REFUSED_HOST, REFUSED_PORT), health, List.of())) {
            probe.probeAtStartup();
        } finally {
            clientResources.shutdown();
        }

        assertThat(RedisRole.values()).noneMatch(health::isAvailable);
    }

    @Test
    void authReadSources_WhenNoReadSourcesAreConfigured_ReadsAuthLocalFirst() {
        AuthReadSources sources = config.authReadSources(authLocalStore, authStore,
                new DefaultListableBeanFactory().getBeanProvider(ReadSourceProperties.class),
                config.redisRoleHealth(), new SimpleMeterRegistry());

        assertThat(sources.<StringRedisTemplate>read(store -> store, () -> null)).isSameAs(authLocalStore);
    }

    @Test
    void authWrites_WhenAuthIsAvailable_WritesOnTheAuthStore() {
        AuthWrites writes = config.authWrites(authStore, config.redisRoleHealth(), new SimpleMeterRegistry());

        assertThat(writes.<StringRedisTemplate>write(store -> store)).isSameAs(authStore);
    }
}
