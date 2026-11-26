package org.danteplanner.backend.shared.redis;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.lettuce.core.resource.ClientResources;
import io.micrometer.core.instrument.MeterRegistry;

import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;

@Configuration
public class RedisRoleConfig {

    public static final String PROBE_SCHEDULER = "redisRoleProbeScheduler";

    @Bean
    public RedisRoleHealth redisRoleHealth() {
        return new RedisRoleHealth();
    }

    @Bean
    public TaggedCircuitBreakerMetrics redisRoleBreakerMetrics(RedisRoleHealth redisRoleHealth) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(redisRoleHealth.registry());
    }

    @Bean
    public RedisRoleProbe redisRoleProbe(
            ClientResources redisClientResources,
            RedisConnectionConfig redisConnectionConfig,
            RedisRoleHealth redisRoleHealth,
            List<RoleConnector> roleConnectors) {
        return new RedisRoleProbe(redisClientResources, redisConnectionConfig, redisRoleHealth, roleConnectors,
                RedisRoleProbe.Timeouts.PRODUCTION);
    }

    @Bean
    public AuthReadSources authReadSources(
            @Qualifier("authLocalStringRedisTemplate") StringRedisTemplate authLocalStore,
            @Qualifier("stringRedisTemplate") StringRedisTemplate authStore,
            ObjectProvider<ReadSourceProperties> readSourceProperties,
            RedisRoleHealth redisRoleHealth,
            MeterRegistry meterRegistry) {
        ReadSourceProperties.ReadOrder readOrder = readSourceProperties.stream()
                .map(ReadSourceProperties::readOrder)
                .findFirst()
                .orElse(ReadSourceProperties.ReadOrder.REPLICA_FIRST);
        return new AuthReadSources(readOrder.stores(
                new RoleGated<>(RedisRole.AUTH_LOCAL, authLocalStore, redisRoleHealth, meterRegistry),
                new RoleGated<>(RedisRole.AUTH, authStore, redisRoleHealth, meterRegistry)));
    }

    @Bean
    public AuthWrites authWrites(
            @Qualifier("stringRedisTemplate") StringRedisTemplate authStore,
            RedisRoleHealth redisRoleHealth,
            MeterRegistry meterRegistry) {
        return new AuthWrites(new RoleGated<>(RedisRole.AUTH, authStore, redisRoleHealth, meterRegistry));
    }

    @Bean(PROBE_SCHEDULER)
    public ThreadPoolTaskScheduler redisRoleProbeScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("redis-role-probe-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }
}
