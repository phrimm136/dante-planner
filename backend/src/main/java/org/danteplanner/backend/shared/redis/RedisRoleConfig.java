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
        return new RedisRoleProbe(redisClientResources, redisConnectionConfig, redisRoleHealth, roleConnectors);
    }

    @Bean
    public AuthReadSources authReadSources(
            @Qualifier("authLocalStringRedisTemplate") StringRedisTemplate authLocalStore,
            @Qualifier("stringRedisTemplate") StringRedisTemplate authStore,
            ObjectProvider<ReadSourceProperties> readSourceProperties,
            RedisRoleHealth redisRoleHealth) {
        boolean readsPrimaryFirst = readSourceProperties.stream().anyMatch(ReadSourceProperties::readsPrimaryFirst);
        return new AuthReadSources(AuthReadSources.sourceOrder(readsPrimaryFirst,
                new AuthReadSources.Source(RedisRole.AUTH_LOCAL, authLocalStore),
                new AuthReadSources.Source(RedisRole.AUTH, authStore)), redisRoleHealth);
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
