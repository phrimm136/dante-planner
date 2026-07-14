package org.danteplanner.backend.shared.gtid;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

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

    @Bean
    public GtidWriteCapture gtidWriteCapture(DataSource dataSource) {
        return new GtidWriteCapture(dataSource);
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
