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

@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
public class GtidGateConfig {

    private static final String GATE_URL_PATTERN = "/api/*";

    @Bean
    public GtidReadGate gtidReadGate(DataSource dataSource) {
        return new GtidReadGate(dataSource);
    }

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
    public FilterRegistrationBean<GtidCookieFilter> gtidCookieFilterRegistration(
            GtidReadGate readGate, GtidWriteCapture writeCapture) {
        FilterRegistrationBean<GtidCookieFilter> registration =
                new FilterRegistrationBean<>(new GtidCookieFilter(readGate, writeCapture));
        registration.addUrlPatterns(GATE_URL_PATTERN);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
