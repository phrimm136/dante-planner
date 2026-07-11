package org.danteplanner.backend.shared.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Enables ShedLock so fleet-duplicated {@code @Scheduled} jobs fire ONCE across pods.
 *
 * <p>The lock store is the durable auth Redis (Oregon primary), where token rotation and
 * blacklist state already live — never the per-region ephemeral rate-limit Redis. Every pod
 * shares this one lock store, so the first pod to acquire a job's lock runs it and concurrent
 * pods are refused.</p>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(
            @Qualifier("authRedisConnectionFactory") RedisConnectionFactory authRedisConnectionFactory) {
        return new RedisLockProvider(authRedisConnectionFactory);
    }
}
