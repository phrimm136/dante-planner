package org.danteplanner.backend.shared.freshness;

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
import org.danteplanner.backend.shared.gtid.GtidCapturingDataSource;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;

/**
 * <p>Registered only when {@code datasource.routing.enabled=true} — the same gate as
 * {@link org.danteplanner.backend.shared.config.RoutingDataSourceConfig}. The filter runs after the
 * Spring Security chain and around the MVC dispatch.</p>
 */
@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@EnableConfigurationProperties({MarkerStore.Settings.class, FreshnessReadSettings.class})
public class FreshnessConfig {

    private static final String STAMP_URL_PATTERN = "/api/*";

    /**
     * Takes no DataSource: commits are reported by {@link GtidCapturingDataSource} from the
     * connection that made them, so this holds only the per-request accumulator. The datasource
     * depends on this bean, not the other way round.
     */
    @Bean
    public GtidWriteCapture gtidWriteCapture(MeterRegistry meterRegistry) {
        return new GtidWriteCapture(meterRegistry);
    }

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
    public MarkerStamp markerStamp(GtidWriteCapture writeCapture, MarkerStore markerStore,
            MarkerStore.Settings settings, MeterRegistry meterRegistry) {
        return new MarkerStamp(writeCapture, markerStore, meterRegistry, settings.onUndeclared());
    }

    @Bean
    public DispatchStamp dispatchStamp(GtidWriteCapture writeCapture, MarkerStamp markerStamp) {
        return new MarkerDispatchStamp(writeCapture, markerStamp);
    }

    @Bean
    public FreshnessReadGuard freshnessReadGuard(MarkerStore markerStore, FreshnessReadSettings settings,
            MeterRegistry meterRegistry, ReadSourceSelector readSourceSelector) {
        return new FreshnessReadGuard(markerStore::read, ReplicaGtidWait::isApplied, settings, meterRegistry,
                readSourceSelector);
    }

    @Bean
    public FreshnessReadAspect freshnessReadAspect(FreshnessReadGuard freshnessReadGuard) {
        return new FreshnessReadAspect(freshnessReadGuard);
    }

    @Bean
    public FilterRegistrationBean<FreshnessStampFilter> freshnessStampFilterRegistration(
            GtidWriteCapture writeCapture, MarkerStamp markerStamp) {
        FilterRegistrationBean<FreshnessStampFilter> registration =
                new FilterRegistrationBean<>(new FreshnessStampFilter(writeCapture, markerStamp));
        registration.addUrlPatterns(STAMP_URL_PATTERN);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
