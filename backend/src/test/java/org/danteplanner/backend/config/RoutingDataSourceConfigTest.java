package org.danteplanner.backend.config;

import java.util.Map;

import javax.sql.DataSource;

import org.danteplanner.backend.shared.config.FallbackReplicaProperties;
import org.danteplanner.backend.shared.config.HikariTuningProperties;
import org.danteplanner.backend.shared.config.PoolLedger;
import org.danteplanner.backend.shared.config.PrimaryReadFallbackDataSource;
import org.danteplanner.backend.shared.config.ReadFallbackConfig;
import org.danteplanner.backend.shared.config.ReplicaDataSourceProperties;
import org.danteplanner.backend.shared.config.RoutingDataSourceConfig;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Scenario test for requirements §6 / INV9 — the routing datasource config sizes its HikariCP pools
 * from the {@link PoolLedger} constants per region, reading the same ledger production reads rather
 * than copies. Exercises only the connection-free builder methods, never {@code dataSource()}.
 */
class RoutingDataSourceConfigTest {

    private static DataSourceProperties primaryProperties() {
        DataSourceProperties props = new DataSourceProperties();
        props.setUrl("jdbc:mysql://primary:3306/planner");
        props.setUsername("primaryUser");
        props.setPassword("primaryPw");
        return props;
    }

    private static ReplicaDataSourceProperties replicaProperties(boolean enabled) {
        ReplicaDataSourceProperties props = new ReplicaDataSourceProperties();
        props.setEnabled(enabled);
        props.setUrl("jdbc:mysql://replica:3306/planner");
        props.setUsername("replicaUser");
        props.setPassword("replicaPw");
        return props;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        when(provider.getObject()).thenReturn(bean);
        return provider;
    }

    private static Map<Object, DataSource> resolvedTargets(
            RoutingDataSourceConfig config, HikariDataSource fallbackReplicaPool) {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DataSource dataSource = config.dataSource(
                new GtidWriteCapture(meterRegistry),
                new UndeclaredPrimaryAccessGuard(meterRegistry, false),
                mock(HikariDataSource.class),
                provider(null),
                provider(null),
                provider(fallbackReplicaPool),
                provider(fallbackReplicaPool == null ? null : CircuitBreaker.ofDefaults("test")));
        LazyConnectionDataSourceProxy lazy = (LazyConnectionDataSourceProxy) dataSource;
        return ((AbstractRoutingDataSource) lazy.getTargetDataSource()).getResolvedDataSources();
    }

    @Nested
    @DisplayName("Seoul pod (replica enabled)")
    class SeoulPod {

        private final RoutingDataSourceConfig config =
                new RoutingDataSourceConfig(primaryProperties(), replicaProperties(true));

        @Test
        void buildPrimaryHikariConfig_WhenReplicaEnabled_UsesSeoulPrimaryPool() {
            HikariConfig hikari = config.buildPrimaryHikariConfig();

            assertThat(hikari.getMaximumPoolSize()).isEqualTo(PoolLedger.SEOUL_PRIMARY_POOL);
        }

        @Test
        void buildReplicaHikariConfig_WhenReplicaEnabled_UsesSeoulReplicaPool() {
            HikariConfig hikari = config.buildReplicaHikariConfig();

            assertThat(hikari.getMaximumPoolSize()).isEqualTo(PoolLedger.SEOUL_REPLICA_POOL);
        }

        @Test
        void buildHikariConfig_WhenBuilt_NamesEachPoolFromTheLedger() {
            assertThat(config.buildPrimaryHikariConfig().getPoolName())
                    .isEqualTo(PoolLedger.PRIMARY_POOL_NAME);
            assertThat(config.buildReplicaHikariConfig().getPoolName())
                    .isEqualTo(PoolLedger.REPLICA_POOL_NAME);
            assertThat(config.buildBulkheadHikariConfig().getPoolName())
                    .isEqualTo(PoolLedger.BULKHEAD_POOL_NAME);
        }
    }

    @Nested
    @DisplayName("Oregon pod (replica disabled)")
    class OregonPod {

        private final RoutingDataSourceConfig config =
                new RoutingDataSourceConfig(primaryProperties(), replicaProperties(false));

        @Test
        void buildPrimaryHikariConfig_WhenReplicaDisabled_UsesOregonPrimaryPool() {
            HikariConfig hikari = config.buildPrimaryHikariConfig();

            assertThat(hikari.getMaximumPoolSize()).isEqualTo(PoolLedger.OREGON_PRIMARY_POOL);
        }

        @Test
        void dataSource_WhenNoFallbackReplicaPool_ReadsTheSamePrimaryTargetAsWrites() {
            Map<Object, DataSource> targets = resolvedTargets(config, null);

            assertThat(targets.get(RoutingKey.REPLICA)).isSameAs(targets.get(RoutingKey.PRIMARY));
            assertThat(targets).doesNotContainKey(RoutingKey.BULKHEAD);
        }

        @Test
        void dataSource_WhenFallbackReplicaPoolExists_ReadsThroughTheBreakerAndWritesDoNot() {
            Map<Object, DataSource> targets = resolvedTargets(config, mock(HikariDataSource.class));

            assertThat(targets.get(RoutingKey.REPLICA)).isInstanceOf(PrimaryReadFallbackDataSource.class);
            assertThat(targets.get(RoutingKey.PRIMARY)).isNotInstanceOf(PrimaryReadFallbackDataSource.class);
        }

        @Test
        void buildFallbackReplicaHikariConfig_WhenBuilt_IsASmallPoolOnThePrimaryCredentials() {
            FallbackReplicaProperties fallback = new FallbackReplicaProperties();
            fallback.setUrl("jdbc:mysql://seoul-replica:3306/planner");

            HikariConfig hikari = new ReadFallbackConfig(
                    primaryProperties(), new HikariTuningProperties(), fallback)
                    .buildFallbackReplicaHikariConfig();

            assertThat(hikari.getJdbcUrl()).isEqualTo(fallback.getUrl());
            assertThat(hikari.getUsername()).isEqualTo(primaryProperties().getUsername());
            assertThat(hikari.getPassword()).isEqualTo(primaryProperties().getPassword());
            assertThat(hikari.getPoolName()).isEqualTo(FallbackReplicaProperties.POOL_NAME);
            assertThat(hikari.getMaximumPoolSize()).isEqualTo(FallbackReplicaProperties.POOL_SIZE);
        }

        @Test
        void buildFallbackReplicaHikariConfig_WhenBuilt_StartsWithoutReachingTheFallbackReplica() {
            FallbackReplicaProperties fallback = new FallbackReplicaProperties();
            fallback.setUrl("jdbc:mysql://seoul-replica:3306/planner");

            HikariConfig hikari = new ReadFallbackConfig(
                    primaryProperties(), new HikariTuningProperties(), fallback)
                    .buildFallbackReplicaHikariConfig();

            assertThat(hikari.getInitializationFailTimeout()).isNegative();
        }
    }
}
