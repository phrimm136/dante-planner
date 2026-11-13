package org.danteplanner.backend.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.test.context.TestConfiguration;

import net.javacrumbs.shedlock.core.LockProvider;

/**
 * Test configuration class providing beans needed for integration tests.
 */
@TestConfiguration
public class TestConfig {

    // Fleet lock arbitration is ShedLockMultiPodIT's subject, so integration contexts grant every lock.
    @Bean
    @Primary
    @Profile("it")
    public LockProvider grantedLockProvider() {
        return configuration -> Optional.of(() -> { });
    }
}
