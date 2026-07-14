package org.danteplanner.backend.shared.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.danteplanner.backend.shared.readpath.PrimaryReCheck;

/**
 * Builds the replica-aware routing datasource per mechanics §5/§6.
 *
 * <p>Each HikariCP pool is sized from the shared {@link PoolLedger} constants — the same ledger
 * the INV9 config assertion reads — so production and the assertion never drift. The primary pool
 * is sized per region (Seoul 10 / Oregon 15); the Seoul-local replica pool is 15.</p>
 */
@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@EnableConfigurationProperties({
        DataSourceProperties.class,
        ReplicaDataSourceProperties.class,
        BulkheadDataSourceProperties.class})
public class RoutingDataSourceConfig {

    private final DataSourceProperties primaryProperties;
    private final ReplicaDataSourceProperties replicaProperties;
    private final BulkheadDataSourceProperties bulkheadProperties;

    public RoutingDataSourceConfig(
            DataSourceProperties primaryProperties, ReplicaDataSourceProperties replicaProperties) {
        this(primaryProperties, replicaProperties, new BulkheadDataSourceProperties());
    }

    @Autowired
    public RoutingDataSourceConfig(
            DataSourceProperties primaryProperties,
            ReplicaDataSourceProperties replicaProperties,
            BulkheadDataSourceProperties bulkheadProperties) {
        this.primaryProperties = primaryProperties;
        this.replicaProperties = replicaProperties;
        this.bulkheadProperties = bulkheadProperties;
    }

    public HikariConfig buildPrimaryHikariConfig() {
        HikariConfig config = new HikariConfig();
        applyEndpoint(config, primaryProperties.getUrl(),
                primaryProperties.getUsername(), primaryProperties.getPassword());
        config.setMaximumPoolSize(
                replicaProperties.isEnabled()
                        ? PoolLedger.SEOUL_PRIMARY_POOL
                        : PoolLedger.OREGON_PRIMARY_POOL);
        return config;
    }

    public HikariConfig buildReplicaHikariConfig() {
        HikariConfig config = new HikariConfig();
        applyEndpoint(config, replicaProperties.getUrl(),
                replicaProperties.getUsername(), replicaProperties.getPassword());
        config.setMaximumPoolSize(PoolLedger.SEOUL_REPLICA_POOL);
        return config;
    }

    public HikariConfig buildBulkheadHikariConfig() {
        HikariConfig config = new HikariConfig();
        boolean ownEndpoint = StringUtils.hasText(bulkheadProperties.getUrl());
        applyEndpoint(config,
                ownEndpoint ? bulkheadProperties.getUrl() : primaryProperties.getUrl(),
                ownEndpoint ? bulkheadProperties.getUsername() : primaryProperties.getUsername(),
                ownEndpoint ? bulkheadProperties.getPassword() : primaryProperties.getPassword());
        config.setMaximumPoolSize(PoolLedger.BULKHEAD_POOL);
        return config;
    }

    private void applyEndpoint(HikariConfig config, String url, String username, String password) {
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariDataSource primary = new HikariDataSource(buildPrimaryHikariConfig());
        Map<Object, Object> targets = new HashMap<>();
        targets.put(RoutingKey.PRIMARY, primary);
        if (replicaProperties.isEnabled()) {
            HikariDataSource replica = new HikariDataSource(buildReplicaHikariConfig());
            targets.put(RoutingKey.REPLICA, replica);
            HikariDataSource bulkhead = new HikariDataSource(buildBulkheadHikariConfig());
            targets.put(RoutingKey.BULKHEAD, bulkhead);
        } else {
            targets.put(RoutingKey.REPLICA, primary);
        }
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource();
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
