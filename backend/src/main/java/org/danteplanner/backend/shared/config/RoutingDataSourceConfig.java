package org.danteplanner.backend.shared.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.util.StringUtils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.shared.gtid.GtidCapturingDataSource;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.danteplanner.backend.shared.readpath.PrimaryReCheck;

/**
 * Boot's pool-metrics post-processor binds {@code hikaricp_*} meters onto {@code DataSource}
 * beans only, tagged with the pool name.
 */
@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@EnableConfigurationProperties({
        DataSourceProperties.class,
        ReadSourceProperties.class,
        BulkheadDataSourceProperties.class,
        HikariTuningProperties.class})
public class RoutingDataSourceConfig {

    private final DataSourceProperties primaryProperties;
    private final ReadSourceProperties readSourceProperties;
    private final BulkheadDataSourceProperties bulkheadProperties;
    private final HikariTuningProperties hikariProperties;

    public RoutingDataSourceConfig(
            DataSourceProperties primaryProperties, ReadSourceProperties readSourceProperties) {
        this(primaryProperties, readSourceProperties,
                new BulkheadDataSourceProperties(), new HikariTuningProperties());
    }

    @Autowired
    public RoutingDataSourceConfig(
            DataSourceProperties primaryProperties,
            ReadSourceProperties readSourceProperties,
            BulkheadDataSourceProperties bulkheadProperties,
            HikariTuningProperties hikariProperties) {
        this.primaryProperties = primaryProperties;
        this.readSourceProperties = readSourceProperties;
        this.bulkheadProperties = bulkheadProperties;
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

    public HikariConfig buildBulkheadHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(PoolLedger.BULKHEAD_POOL_NAME);
        boolean ownEndpoint = StringUtils.hasText(bulkheadProperties.getUrl());
        applyEndpoint(config,
                ownEndpoint ? bulkheadProperties.getUrl() : primaryProperties.getUrl(),
                ownEndpoint ? bulkheadProperties.getUsername() : primaryProperties.getUsername(),
                ownEndpoint ? bulkheadProperties.getPassword() : primaryProperties.getPassword());
        config.setMaximumPoolSize(PoolLedger.BULKHEAD_POOL);
        config.setConnectionTimeout(bulkheadProperties.getConnectionTimeout());
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
    @Conditional(ReadsAReplicaFirst.class)
    public HikariDataSource bulkheadPool() {
        return new HikariDataSource(buildBulkheadHikariConfig());
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
            @Qualifier("bulkheadPool") ObjectProvider<HikariDataSource> bulkheadPool,
            ReadSourceSelector readSourceSelector) {
        // The committed GTID lives as session state on the physical connection.
        DataSource primary = new GtidCapturingDataSource(primaryPool, gtidWriteCapture);
        Map<Object, Object> targets = new HashMap<>();
        targets.put(RoutingKey.PRIMARY, primary);
        targets.put(RoutingKey.REPLICA, readSourceSelector);
        bulkheadPool.ifAvailable(bulkhead -> targets.put(RoutingKey.BULKHEAD, bulkhead));
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(undeclaredGuard);
        routing.setTargetDataSources(targets);
        routing.setAuthorityReadSource(readSourceSelector.authorityReads());
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routing);
    }

    @Bean
    public PrimaryReCheck primaryReCheck(
            MeterRegistry meterRegistry,
            ContentTombstoneStore tombstoneStore,
            ReadSourceSelector readSourceSelector) {
        return new PrimaryReCheck(meterRegistry, tombstoneStore,
                ChosenReadSource::servedByAuthority,
                readSourceSelector::isAuthorityHealthy,
                readSourceProperties.readsPrimaryFirst() ? RoutingKey.PRIMARY : RoutingKey.BULKHEAD);
    }

    public static class ReadsAReplicaFirst implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            List<ReadSourceProperties.Source> sources = ReadSourcesConfig.boundSources(context.getEnvironment());
            return !sources.isEmpty() && !sources.get(0).isPrimary();
        }
    }
}
