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
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

import io.lettuce.core.resource.ClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.freshness.MarkerStore;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;

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
            ReadSourceProperties readSourceProperties,
            RedisRoleHealth redisRoleHealth,
            MarkerStore.Settings settings,
            MeterRegistry meterRegistry) {
        MarkerStore.Source local = new MarkerStore.Source(RedisRole.AUTH_LOCAL, authLocalStore);
        MarkerStore.Source auth = new MarkerStore.Source(RedisRole.AUTH, authStore);
        return new MarkerStore(authRedisConnectionFactory, redisClientResources,
                MarkerStore.sourceOrder(readSourceProperties.readsPrimaryFirst(), local, auth),
                redisRoleHealth, settings, meterRegistry);
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
