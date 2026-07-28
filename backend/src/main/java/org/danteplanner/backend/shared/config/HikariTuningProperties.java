package org.danteplanner.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * The subset of {@code spring.datasource.hikari.*} the hand-built routing pools honour.
 *
 * <p>Spring Boot binds that prefix only onto a {@code DataSource} it constructs itself, so pools
 * built from {@code new HikariConfig()} silently miss it.</p>
 */
@ConfigurationProperties(prefix = "spring.datasource.hikari")
@Getter
@Setter
public class HikariTuningProperties {

    private long connectionTimeout = 30_000L;
}
