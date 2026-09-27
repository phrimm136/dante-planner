package org.danteplanner.backend.shared.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@ConditionalOnProperty(name = "datasource.replica.enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(name = FallbackReplicaProperties.URL_PROPERTY)
@EnableConfigurationProperties(FallbackReplicaProperties.class)
public class ReadFallbackConfig {

    public static final String PRIMARY_READ_BREAKER = "primary-read";
    public static final String BREAKER_TRANSITIONS = "datasource.primary.read.breaker.transitions";

    private static final long START_WITHOUT_CONNECTING = -1L;

    private final DataSourceProperties primaryProperties;
    private final HikariTuningProperties hikariProperties;
    private final FallbackReplicaProperties fallbackProperties;

    public ReadFallbackConfig(
            DataSourceProperties primaryProperties,
            HikariTuningProperties hikariProperties,
            FallbackReplicaProperties fallbackProperties) {
        this.primaryProperties = primaryProperties;
        this.hikariProperties = hikariProperties;
        this.fallbackProperties = fallbackProperties;
    }

    public HikariConfig buildFallbackReplicaHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(FallbackReplicaProperties.POOL_NAME);
        RoutingDataSourceConfig.applyEndpoint(config, fallbackProperties.getUrl(),
                primaryProperties.getUsername(), primaryProperties.getPassword());
        config.setMaximumPoolSize(FallbackReplicaProperties.POOL_SIZE);
        config.setConnectionTimeout(hikariProperties.getConnectionTimeout());
        config.setInitializationFailTimeout(START_WITHOUT_CONNECTING);
        return config;
    }

    @Bean
    public HikariDataSource fallbackReplicaPool() {
        return new HikariDataSource(buildFallbackReplicaHikariConfig());
    }

    @Bean
    public CircuitBreakerRegistry primaryReadBreakerRegistry() {
        return CircuitBreakerRegistry.of(fallbackProperties.getBreaker().circuitBreakerConfig());
    }

    @Bean
    public TaggedCircuitBreakerMetrics primaryReadBreakerMetrics(CircuitBreakerRegistry primaryReadBreakerRegistry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(primaryReadBreakerRegistry);
    }

    @Bean
    public CircuitBreaker primaryReadBreaker(
            CircuitBreakerRegistry primaryReadBreakerRegistry, MeterRegistry meterRegistry) {
        CircuitBreaker breaker = primaryReadBreakerRegistry.circuitBreaker(PRIMARY_READ_BREAKER);
        breaker.getEventPublisher().onStateTransition(event -> meterRegistry.counter(
                BREAKER_TRANSITIONS,
                "from", event.getStateTransition().getFromState().name().toLowerCase(Locale.ROOT),
                "to", event.getStateTransition().getToState().name().toLowerCase(Locale.ROOT))
                .increment());
        return breaker;
    }
}
