package org.danteplanner.backend.shared.config;

import java.util.Locale;
import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.mysql.cj.jdbc.Driver;
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
    public static final String PRIMARY_PROBE_SCHEDULER = "primaryProbeScheduler";

    private static final String CONNECT_TIMEOUT_PROPERTY = "connectTimeout";

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

    public SimpleDriverDataSource buildPrimaryProbeDataSource() {
        String timeoutMs = String.valueOf(fallbackProperties.getProbe().getTimeout().toMillis());
        Properties driverProperties = new Properties();
        driverProperties.setProperty(CONNECT_TIMEOUT_PROPERTY, timeoutMs);
        driverProperties.setProperty(TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY, timeoutMs);
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(Driver.class);
        dataSource.setUrl(primaryProperties.getUrl());
        dataSource.setUsername(primaryProperties.getUsername());
        dataSource.setPassword(primaryProperties.getPassword());
        dataSource.setConnectionProperties(driverProperties);
        return dataSource;
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

    @Bean(PRIMARY_PROBE_SCHEDULER)
    public ThreadPoolTaskScheduler primaryProbeScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("primary-probe-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    public PrimaryHealthProbe primaryHealthProbe(CircuitBreaker primaryReadBreaker) {
        return new PrimaryHealthProbe(
                buildPrimaryProbeDataSource(), primaryReadBreaker, fallbackProperties.getProbe());
    }
}
