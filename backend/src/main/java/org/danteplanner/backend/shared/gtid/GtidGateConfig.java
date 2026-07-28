package org.danteplanner.backend.shared.gtid;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Wires the read-your-writes GTID gate for the Seoul pods (routing datasource present).
 *
 * <p>Registered only when {@code datasource.routing.enabled=true} — the same gate as
 * {@link org.danteplanner.backend.shared.config.RoutingDataSourceConfig} — so contexts without a
 * replica never load the gate. The filter runs after the Spring Security chain and around the MVC
 * dispatch, pinning routing before the controller's read-only transaction.</p>
 */
@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
public class GtidGateConfig {

    private static final String GATE_URL_PATTERN = "/api/*";

    @Bean
    public GtidReadGate gtidReadGate(DataSource dataSource) {
        return new GtidReadGate(dataSource);
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
    public FilterRegistrationBean<GtidCookieFilter> gtidCookieFilterRegistration(
            GtidReadGate readGate, GtidWriteCapture writeCapture) {
        FilterRegistrationBean<GtidCookieFilter> registration =
                new FilterRegistrationBean<>(new GtidCookieFilter(readGate, writeCapture));
        registration.addUrlPatterns(GATE_URL_PATTERN);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
