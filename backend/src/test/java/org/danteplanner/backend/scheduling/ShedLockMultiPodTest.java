package org.danteplanner.backend.scheduling;

import org.danteplanner.backend.notification.service.NotificationService;
import org.danteplanner.backend.user.scheduler.UserCleanupScheduler;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import com.redis.testcontainers.RedisContainer;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves scheduled jobs fire ONCE across a multi-pod fleet via ShedLock over the auth Redis.
 *
 * <p>Two {@link LockProvider}s over the SAME Redis connection factory simulate two pods sharing
 * one lock store: the first acquisition of a lock name wins, a concurrent second is refused.
 * Both target job methods must carry {@link SchedulerLock} to bind to that shared lock.</p>
 */
@Tag("containerized")
class ShedLockMultiPodTest {

    private static final String REDIS_IMAGE = "redis:7-alpine";

    private static final RedisContainer REDIS = new RedisContainer(REDIS_IMAGE);

    private static RedisConnectionFactory connectionFactory;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        LettuceConnectionFactory f = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(REDIS.getRedisHost(), REDIS.getRedisPort()));
        f.afterPropertiesSet();
        connectionFactory = f;
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @Test
    @DisplayName("Two pods sharing one Redis lock store: first acquires the lock, concurrent second is refused")
    void lock_WhenTwoPodsContendSameLockName_OnlyFirstAcquires() {
        LockProvider podA = new RedisLockProvider(connectionFactory);
        LockProvider podB = new RedisLockProvider(connectionFactory);

        LockConfiguration cfg = new LockConfiguration(
            Instant.now(), "phase6-multipod-test", Duration.ofSeconds(30), Duration.ZERO);

        Optional<SimpleLock> lockA = podA.lock(cfg);
        Optional<SimpleLock> lockB = podB.lock(cfg);

        assertThat(lockA).as("first pod acquires the shared lock").isPresent();
        assertThat(lockB).as("concurrent second pod is refused the same lock").isEmpty();

        lockA.get().unlock();
    }

    @Test
    @DisplayName("Notification 2AM cleanup job carries @SchedulerLock")
    void cleanupOldNotifications_WhenDeclared_IsAnnotatedWithSchedulerLock() throws NoSuchMethodException {
        assertThat(NotificationService.class
            .getDeclaredMethod("cleanupOldNotifications")
            .isAnnotationPresent(SchedulerLock.class))
            .as("NotificationService.cleanupOldNotifications must be @SchedulerLock to run once across the fleet")
            .isTrue();
    }

    @Test
    @DisplayName("User 3AM cleanup job carries @SchedulerLock")
    void cleanupExpiredUsers_WhenDeclared_IsAnnotatedWithSchedulerLock() throws NoSuchMethodException {
        assertThat(UserCleanupScheduler.class
            .getDeclaredMethod("cleanupExpiredUsers")
            .isAnnotationPresent(SchedulerLock.class))
            .as("UserCleanupScheduler.cleanupExpiredUsers must be @SchedulerLock to run once across the fleet")
            .isTrue();
    }
}
