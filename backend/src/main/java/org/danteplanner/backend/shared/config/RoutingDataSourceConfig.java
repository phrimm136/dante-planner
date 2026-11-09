package org.danteplanner.backend.shared.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.shared.gtid.GtidCapturingDataSource;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

/**
 * Boot's pool-metrics post-processor binds {@code hikaricp_*} meters onto {@code DataSource}
 * beans only, tagged with the pool name.
 */
@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@EnableConfigurationProperties({
        DataSourceProperties.class,
        ReadSourceProperties.class,
        HikariTuningProperties.class})
public class RoutingDataSourceConfig {

    private final DataSourceProperties primaryProperties;
    private final ReadSourceProperties readSourceProperties;
    private final HikariTuningProperties hikariProperties;

    public RoutingDataSourceConfig(
            DataSourceProperties primaryProperties, ReadSourceProperties readSourceProperties) {
        this(primaryProperties, readSourceProperties, new HikariTuningProperties());
    }

    @Autowired
    public RoutingDataSourceConfig(
            DataSourceProperties primaryProperties,
            ReadSourceProperties readSourceProperties,
            HikariTuningProperties hikariProperties) {
        this.primaryProperties = primaryProperties;
        this.readSourceProperties = readSourceProperties;
        this.hikariProperties = hikariProperties;
    }

    public HikariConfig buildPrimaryHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(PoolLedger.PRIMARY_POOL_NAME);
        applyEndpoint(config, primaryProperties.getUrl(),
                primaryProperties.getUsername(), primaryProperties.getPassword());
        config.setMaximumPoolSize(
                readSourceProperties.readsPrimaryFirst()
                        ? PoolLedger.OREGON_PRIMARY_POOL
                        : PoolLedger.SEOUL_PRIMARY_POOL);
        config.setConnectionTimeout(hikariProperties.getConnectionTimeout());
        return config;
    }

    static void applyEndpoint(HikariConfig config, String url, String username, String password) {
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setConnectionInitSql(TimeoutHierarchy.LOCK_WAIT_INIT_SQL);
        // Connector/J lets the driver properties win over the same key in the URL query string.
        config.addDataSourceProperty(
                TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY,
                String.valueOf(TimeoutHierarchy.JDBC_SOCKET_TIMEOUT_MS));
        StatementCache.DRIVER_PROPERTIES.forEach(config::addDataSourceProperty);
    }

    @Bean
    public HikariDataSource primaryPool() {
        return new HikariDataSource(buildPrimaryHikariConfig());
    }

    @Bean
    public UndeclaredPrimaryAccessGuard undeclaredPrimaryAccessGuard(
            MeterRegistry meterRegistry,
            @Value("${datasource.routing.undeclared-primary-fail-fast}") boolean failFast) {
        return new UndeclaredPrimaryAccessGuard(meterRegistry, failFast);
    }

    @Bean
    @Primary
    public DataSource dataSource(
            GtidWriteCapture gtidWriteCapture,
            UndeclaredPrimaryAccessGuard undeclaredGuard,
            @Qualifier("primaryPool") HikariDataSource primaryPool,
            ReadSourceSelector readSourceSelector) {
        // The committed GTID lives as session state on the physical connection.
        DataSource primary = new GtidCapturingDataSource(primaryPool, gtidWriteCapture);
        Map<Object, Object> targets = new HashMap<>();
        targets.put(RoutingKey.PRIMARY, primary);
        targets.put(RoutingKey.REPLICA, readSourceSelector);
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(undeclaredGuard);
        routing.setTargetDataSources(targets);
        routing.setAuthorityReadSource(readSourceSelector.authorityReads());
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routing);
    }
}
