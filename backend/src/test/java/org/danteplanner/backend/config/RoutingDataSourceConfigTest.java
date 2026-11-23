package org.danteplanner.backend.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.danteplanner.backend.shared.config.PoolLedger;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.config.RoutingDataSourceConfig;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The routing datasource config sizes its HikariCP pools
 * from the {@link PoolLedger} constants per region, reading the same ledger production reads rather
 * than copies. Exercises only the connection-free builder methods and the target wiring.
 */
class RoutingDataSourceConfigTest {

    private static final String REPLICA = "seoul-replica";

    private static DataSourceProperties primaryProperties() {
        DataSourceProperties props = new DataSourceProperties();
        props.setUrl("jdbc:mysql://primary:3306/planner");
        props.setUsername("primaryUser");
        props.setPassword("primaryPw");
        return props;
    }

    private static ReadSourceProperties readSources(String... names) {
        return new ReadSourceProperties(
                Arrays.stream(names).map(name -> new ReadSourceProperties.Source(name,
                        name.equals(ReadSourceProperties.PRIMARY) ? "" : "jdbc:mysql://replica:3306/planner",
                        "", "")).toList(),
                ReadSourceProperties.Breaker.defaults(), ReadSourceProperties.Probe.defaults());
    }

    private static ReadSourceSelector selector() {
        return new ReadSourceSelector(List.of(new ReadSourceSelector.Source(
                ReadSourceProperties.PRIMARY, true, new HikariDataSource(), CircuitBreaker.ofDefaults("test"))));
    }

    private static AbstractRoutingDataSource routing(
            RoutingDataSourceConfig config, ReadSourceSelector selector) {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DataSource dataSource = config.dataSource(
                new GtidWriteCapture(meterRegistry),
                new UndeclaredPrimaryAccessGuard(meterRegistry, false),
                new HikariDataSource(),
                selector);
        LazyConnectionDataSourceProxy lazy = (LazyConnectionDataSourceProxy) dataSource;
        return (AbstractRoutingDataSource) lazy.getTargetDataSource();
    }

    private static Map<Object, DataSource> resolvedTargets(
            RoutingDataSourceConfig config, ReadSourceSelector selector) {
        return routing(config, selector).getResolvedDataSources();
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
        }

        @Test
        void dataSource_WhenBuilt_RoutesReadsToTheSelectorAndHoldsOnlyThePrimaryAndReplicaTargets() {
            ReadSourceSelector selector = selector();

            Map<Object, DataSource> targets = resolvedTargets(config, selector);

            assertThat(targets).containsOnlyKeys(RoutingKey.PRIMARY, RoutingKey.REPLICA);
            assertThat(targets.get(RoutingKey.REPLICA)).isSameAs(selector);
            assertThat(targets.get(RoutingKey.PRIMARY)).isNotSameAs(selector);
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
    }
}
