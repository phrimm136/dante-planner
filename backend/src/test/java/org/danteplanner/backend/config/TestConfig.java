package org.danteplanner.backend.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.test.context.TestConfiguration;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import net.javacrumbs.shedlock.core.LockProvider;

import org.danteplanner.backend.support.InMemoryProxyManager;

/**
 * Test configuration class providing beans needed for integration tests.
 */
@TestConfiguration
public class TestConfig {

    /**
     * Inert rate-limit proxy manager for the hermetic {@code test} profile: every bucket
     * always allows consumption, so rate-limited endpoints serve without a live Redis.
     *
     * <p>Guarded to the {@code test} profile because {@code it}-profile tests
     * (e.g. {@code DegradationIT}) assert the behavior of the real Redis-backed manager.
     * Rate-limit semantics themselves are covered by the containerized
     * {@code RateLimitServiceIT}.</p>
     *
     * @return a ProxyManager whose buckets never exhaust
     */
    @Bean
    @Primary
    @Profile("test")
    public ProxyManager<byte[]> testRateLimitProxyManager() {
        return new InMemoryProxyManager();
    }

    // Fleet lock arbitration is ShedLockMultiPodIT's subject, so integration contexts grant every lock.
    @Bean
    @Primary
    @Profile("it")
    public LockProvider grantedLockProvider() {
        return configuration -> Optional.of(() -> { });
    }
}
