package org.danteplanner.backend.shared.gtid;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.lettuce.core.resource.ClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.freshness.MarkerStore;

/**
 * Wires the read-your-writes GTID gate wherever the routing datasource is present.
 *
 * <p>Registered only when {@code datasource.routing.enabled=true} — the same gate as
 * {@link org.danteplanner.backend.shared.config.RoutingDataSourceConfig}. The filter runs after the
 * Spring Security chain and around the MVC dispatch, pinning routing before the controller's
 * read-only transaction.</p>
 */
@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@EnableConfigurationProperties(MarkerStore.Settings.class)
public class GtidGateConfig {

    private static final String GATE_URL_PATTERN = "/api/*";

    @Bean
    public GtidReadGate gtidReadGate(ReadSourceSelector readSourceSelector, MeterRegistry meterRegistry) {
        return new GtidReadGate(readSourceSelector, meterRegistry);
    }

    /**
     * Takes no DataSource: commits are reported by {@link GtidCapturingDataSource} from the
     * connection that made them, so this holds only the per-request accumulator. The datasource
     * depends on this bean, not the other way round.
     */
    @Bean
    public GtidWriteCapture gtidWriteCapture(MeterRegistry meterRegistry) {
        return new GtidWriteCapture(meterRegistry);
    }

    /**
     * Plain JPA transaction management. Capture used to hang off a transaction-manager subclass that
     * registered an {@code afterCommit} synchronization, which had to find the transaction's
     * connection again after the fact — and found the wrong one. Interception at commit needs no
     * transaction-manager involvement at all.
     */
    @Bean
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        JpaTransactionManager transactionManager = new JpaTransactionManager(entityManagerFactory);
        transactionManager.setDataSource(dataSource);
        return transactionManager;
    }

    @Bean
    public MarkerStore markerStore(
            @Qualifier("authRedisConnectionFactory") LettuceConnectionFactory authRedisConnectionFactory,
            ClientResources redisClientResources,
            @Qualifier("stringRedisTemplate") StringRedisTemplate authStore,
            @Qualifier("authLocalStringRedisTemplate") StringRedisTemplate authLocalStore,
            RedisConnectionConfig redisConnectionConfig,
            ReadSourceProperties readSourceProperties,
            CircuitBreakerRegistry readSourceBreakerRegistry,
            MarkerStore.Settings settings,
            MeterRegistry meterRegistry) {
        MarkerStore.Source local = new MarkerStore.Source(MarkerStore.LOCAL_SOURCE, authLocalStore,
                redisConnectionConfig.getAuthLocal(),
                readSourceBreakerRegistry.circuitBreaker(MarkerStore.BREAKER_NAME_PREFIX + MarkerStore.LOCAL_SOURCE));
        MarkerStore.Source auth = new MarkerStore.Source(MarkerStore.AUTH_SOURCE, authStore,
                redisConnectionConfig.getAuth(),
                readSourceBreakerRegistry.circuitBreaker(MarkerStore.BREAKER_NAME_PREFIX + MarkerStore.AUTH_SOURCE));
        return new MarkerStore(authRedisConnectionFactory, redisClientResources,
                MarkerStore.sourceOrder(readSourceProperties.readsPrimaryFirst(), local, auth),
                settings, meterRegistry);
    }

    @Bean(MarkerStore.PROBE_SCHEDULER)
    public ThreadPoolTaskScheduler markerSourceProbeScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("marker-source-probe-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    public FilterRegistrationBean<GtidCookieFilter> gtidCookieFilterRegistration(
            GtidReadGate readGate,
            GtidWriteCapture writeCapture,
            MarkerStore markerStore,
            ProblemWriter problemWriter) {
        FilterRegistrationBean<GtidCookieFilter> registration = new FilterRegistrationBean<>(
                new GtidCookieFilter(readGate, writeCapture, markerStore, problemWriter));
        registration.addUrlPatterns(GATE_URL_PATTERN);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
