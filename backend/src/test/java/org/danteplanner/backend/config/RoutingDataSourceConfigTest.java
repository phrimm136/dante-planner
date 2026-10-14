package org.danteplanner.backend.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.danteplanner.backend.shared.config.PoolLedger;
import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.config.RoutingDataSourceConfig;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The routing datasource config sizes its HikariCP pools
 * from the {@link PoolLedger} constants per region, reading the same ledger production reads rather
 * than copies. Exercises only the connection-free builder methods and the target wiring.
 */
@ExtendWith(MockitoExtension.class)
class RoutingDataSourceConfigTest {

    private static final String REPLICA = "seoul-replica";

    @Mock
    private DataSource primaryReadPool;

    @Mock
    private Connection primaryReadConnection;

    private static DataSourceProperties primaryProperties() {
        DataSourceProperties props = new DataSourceProperties();
        props.setUrl("jdbc:mysql://primary:3306/planner");
        props.setUsername("primaryUser");
        props.setPassword("primaryPw");
        return props;
    }

    private static ReadSourceProperties readSources(String... names) {
        ReadSourceProperties props = new ReadSourceProperties();
        props.setReadSources(Arrays.stream(names).map(name -> {
            ReadSourceProperties.Source source = new ReadSourceProperties.Source();
            source.setName(name);
            if (!source.isPrimary()) {
                source.setUrl("jdbc:mysql://replica:3306/planner");
            }
            return source;
        }).toList());
        return props;
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (bean != null) {
            beans.addBean(type.getName(), bean);
        }
        return beans.getBeanProvider(type);
    }

    private static ReadSourceSelector selector() {
        return new ReadSourceSelector(List.of(new ReadSourceSelector.Source(
                ReadSourceProperties.PRIMARY, true, new HikariDataSource(), CircuitBreaker.ofDefaults("test"))));
    }

    private static AbstractRoutingDataSource routing(
            RoutingDataSourceConfig config, ReadSourceSelector selector, HikariDataSource bulkheadPool) {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DataSource dataSource = config.dataSource(
                new GtidWriteCapture(meterRegistry),
                new UndeclaredPrimaryAccessGuard(meterRegistry, false),
                new HikariDataSource(),
                provider(HikariDataSource.class, bulkheadPool),
                selector);
        LazyConnectionDataSourceProxy lazy = (LazyConnectionDataSourceProxy) dataSource;
        return (AbstractRoutingDataSource) lazy.getTargetDataSource();
    }

    private static Map<Object, DataSource> resolvedTargets(
            RoutingDataSourceConfig config, ReadSourceSelector selector, HikariDataSource bulkheadPool) {
        return routing(config, selector, bulkheadPool).getResolvedDataSources();
    }

    @Nested
    class SeoulPodReadingTheReplicaFirst {

        private final RoutingDataSourceConfig config =
                new RoutingDataSourceConfig(primaryProperties(), readSources(REPLICA, ReadSourceProperties.PRIMARY));

        @Test
        void buildPrimaryHikariConfig_WhenTheReplicaReadsFirst_UsesSeoulPrimaryPool() {
            HikariConfig hikari = config.buildPrimaryHikariConfig();

            assertThat(hikari.getMaximumPoolSize()).isEqualTo(PoolLedger.SEOUL_PRIMARY_POOL);
        }

        @Test
        void buildHikariConfig_WhenBuilt_NamesEachPoolFromTheLedger() {
            assertThat(config.buildPrimaryHikariConfig().getPoolName())
                    .isEqualTo(PoolLedger.PRIMARY_POOL_NAME);
            assertThat(config.buildBulkheadHikariConfig().getPoolName())
                    .isEqualTo(PoolLedger.BULKHEAD_POOL_NAME);
        }

        @Test
        void dataSource_WhenABulkheadPoolExists_RoutesReadsToTheSelectorAndReChecksToTheBulkhead() {
            ReadSourceSelector selector = selector();
            HikariDataSource bulkhead = new HikariDataSource();

            Map<Object, DataSource> targets = resolvedTargets(config, selector, bulkhead);

            assertThat(targets.get(RoutingKey.REPLICA)).isSameAs(selector);
            assertThat(targets.get(RoutingKey.BULKHEAD)).isSameAs(bulkhead);
            assertThat(targets.get(RoutingKey.PRIMARY)).isNotSameAs(selector);
        }

        @Test
        void dataSource_WhenAReadOnlyTransactionIsPinnedToPrimary_LendsFromTheSelectorsAuthority()
                throws SQLException {
            when(primaryReadPool.getConnection()).thenReturn(primaryReadConnection);
            ReadSourceSelector selector = new ReadSourceSelector(List.of(
                    new ReadSourceSelector.Source(REPLICA, false, new HikariDataSource(),
                            CircuitBreaker.ofDefaults(REPLICA)),
                    new ReadSourceSelector.Source(ReadSourceProperties.PRIMARY, true, primaryReadPool,
                            CircuitBreaker.ofDefaults(ReadSourceProperties.PRIMARY))));
            AbstractRoutingDataSource routing = routing(config, selector, new HikariDataSource());
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
            try {
                assertThat(routing.getConnection()).isSameAs(primaryReadConnection);
            } finally {
                ReadOnlyRoutingDataSource.clear();
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            }
        }
    }

    @Nested
    class OregonPodReadingThePrimaryFirst {

        private final RoutingDataSourceConfig config =
                new RoutingDataSourceConfig(primaryProperties(), readSources(ReadSourceProperties.PRIMARY, REPLICA));

        @Test
        void buildPrimaryHikariConfig_WhenThePrimaryReadsFirst_UsesOregonPrimaryPool() {
            HikariConfig hikari = config.buildPrimaryHikariConfig();

            assertThat(hikari.getMaximumPoolSize()).isEqualTo(PoolLedger.OREGON_PRIMARY_POOL);
        }

        @Test
        void dataSource_WhenNoBulkheadPoolExists_RoutesReadsToTheSelectorAndHoldsNoBulkheadTarget() {
            ReadSourceSelector selector = selector();

            Map<Object, DataSource> targets = resolvedTargets(config, selector, null);

            assertThat(targets.get(RoutingKey.REPLICA)).isSameAs(selector);
            assertThat(targets).doesNotContainKey(RoutingKey.BULKHEAD);
        }
    }
}
