package org.danteplanner.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * Spring Boot binds {@code spring.datasource.hikari.*} only onto a {@code DataSource} it
 * constructs itself, so pools built from {@code new HikariConfig()} silently miss it.
 */
@ConfigurationProperties(prefix = "spring.datasource.hikari")
@Validated
@Getter
@Setter
public class HikariTuningProperties {

    @Positive
    private long connectionTimeout = TimeoutHierarchy.POOL_ACQUIRE_TIMEOUT_MS;
}
