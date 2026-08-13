package org.danteplanner.backend.shared.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.util.StringUtils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.shared.gtid.GtidCapturingDataSource;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.danteplanner.backend.shared.readpath.PrimaryReCheck;

/**
 * Builds the replica-aware routing datasource per mechanics §5/§6.
 *
 * <p>Each HikariCP pool is sized from the shared {@link PoolLedger} constants — the same ledger
 * the INV9 config assertion reads — so production and the assertion never drift. The primary pool
 * is sized per region (Seoul 10 / Oregon 15); the Seoul-local replica pool is 15.</p>
 *
 * <p>The pools are beans rather than locals so Boot's pool-metrics post-processor can reach them:
 * it binds {@code hikaricp_*} meters onto {@code DataSource} beans only, tagged with the pool
 * name.</p>
 */
@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@EnableConfigurationProperties({
        DataSourceProperties.class,
        ReplicaDataSourceProperties.class,
        BulkheadDataSourceProperties.class,
        HikariTuningProperties.class})
public class RoutingDataSourceConfig {

    private final DataSourceProperties primaryProperties;
    private final ReplicaDataSourceProperties replicaProperties;
    private final BulkheadDataSourceProperties bulkheadProperties;
    private final HikariTuningProperties hikariProperties;

    public RoutingDataSourceConfig(
            DataSourceProperties primaryProperties, ReplicaDataSourceProperties replicaProperties) {
        this(primaryProperties, replicaProperties,
                new BulkheadDataSourceProperties(), new HikariTuningProperties());
    }

    @Autowired
    public RoutingDataSourceConfig(
            DataSourceProperties primaryProperties,
            ReplicaDataSourceProperties replicaProperties,
            BulkheadDataSourceProperties bulkheadProperties,
            HikariTuningProperties hikariProperties) {
        this.primaryProperties = primaryProperties;
        this.replicaProperties = replicaProperties;
        this.bulkheadProperties = bulkheadProperties;
        this.hikariProperties = hikariProperties;
    }

    public HikariConfig buildPrimaryHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(PoolLedger.PRIMARY_POOL_NAME);
        applyEndpoint(config, primaryProperties.getUrl(),
                primaryProperties.getUsername(), primaryProperties.getPassword());
        config.setMaximumPoolSize(
                replicaProperties.isEnabled()
                        ? PoolLedger.SEOUL_PRIMARY_POOL
                        : PoolLedger.OREGON_PRIMARY_POOL);
        config.setConnectionTimeout(hikariProperties.getConnectionTimeout());
        return config;
    }

    public HikariConfig buildReplicaHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(PoolLedger.REPLICA_POOL_NAME);
        applyEndpoint(config, replicaProperties.getUrl(),
                replicaProperties.getUsername(), replicaProperties.getPassword());
        config.setMaximumPoolSize(PoolLedger.SEOUL_REPLICA_POOL);
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

    private void applyEndpoint(HikariConfig config, String url, String username, String password) {
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
    @ConditionalOnProperty(name = "datasource.replica.enabled", havingValue = "true")
    public HikariDataSource replicaPool() {
        return new HikariDataSource(buildReplicaHikariConfig());
    }

    @Bean
    @ConditionalOnProperty(name = "datasource.replica.enabled", havingValue = "true")
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
            @Qualifier("replicaPool") ObjectProvider<HikariDataSource> replicaPool,
            @Qualifier("bulkheadPool") ObjectProvider<HikariDataSource> bulkheadPool) {
        // Wrapped BELOW the routing and lazy proxies: the committed GTID lives as session state on
        // the physical connection, and this is the only layer where "the connection that committed"
        // is held rather than looked up. The replica pool takes no writes, so it stays bare.
        DataSource primary = new GtidCapturingDataSource(primaryPool, gtidWriteCapture);
        Map<Object, Object> targets = new HashMap<>();
        targets.put(RoutingKey.PRIMARY, primary);
        if (replicaProperties.isEnabled()) {
            targets.put(RoutingKey.REPLICA, replicaPool.getObject());
            targets.put(RoutingKey.BULKHEAD, bulkheadPool.getObject());
        } else {
            targets.put(RoutingKey.REPLICA, primary);
        }
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(undeclaredGuard);
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routing);
    }

    @Bean
    @ConditionalOnProperty(name = "datasource.replica.enabled", havingValue = "true")
    public PrimaryReCheck primaryReCheck(MeterRegistry meterRegistry, ContentTombstoneStore tombstoneStore) {
        return new PrimaryReCheck(meterRegistry, tombstoneStore);
    }
}
