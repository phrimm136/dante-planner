package org.danteplanner.backend.shared.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.lettuce.core.resource.ClientResources;

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
            RedisRoleHealth redisRoleHealth) {
        return new RedisRoleProbe(redisClientResources, redisConnectionConfig, redisRoleHealth);
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
