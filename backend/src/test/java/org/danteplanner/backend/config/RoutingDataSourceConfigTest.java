package org.danteplanner.backend.config;

import java.time.Duration;
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
import org.danteplanner.backend.shared.failure.OnUndeclared;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The routing datasource config sizes its HikariCP pools
 * from the {@link PoolLedger} constants per region, reading the same ledger production reads rather
 * than copies.
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
                ReadSourceProperties.PRIMARY, ReadSourceSelector.Authority.PRIMARY, new HikariDataSource(), CircuitBreaker.ofDefaults("test"))));
    }

    private static AbstractRoutingDataSource routing(
            RoutingDataSourceConfig config, ReadSourceSelector selector) {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DataSource dataSource = config.dataSource(
                new GtidWriteCapture(meterRegistry),
                new UndeclaredPrimaryAccessGuard(meterRegistry, OnUndeclared.COUNT),
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

    @Nested
    class UndeclaredPrimaryPolicy {

        private static final String ON_UNDECLARED = "datasource.routing.on-undeclared-primary";

        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        private ApplicationContextRunner bound(String onUndeclared) {
            return new ApplicationContextRunner()
                    .withInitializer(context -> {
                        context.getBeanFactory()
                                .setConversionService(ApplicationConversionService.getSharedInstance());
                        context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
                    })
                    .withUserConfiguration(RoutingDataSourceConfig.class)
                    .withBean(LazyInitializationExcludeFilter.class,
                            () -> LazyInitializationExcludeFilter.forBeanTypes(UndeclaredPrimaryAccessGuard.class))
                    .withBean(MeterRegistry.class, () -> meterRegistry)
                    .withPropertyValues(
                            "datasource.routing.enabled=true",
                            "datasource.read-sources[0].name=" + ReadSourceProperties.PRIMARY,
                            ON_UNDECLARED + "=" + onUndeclared);
        }

        private UndeclaredPrimaryAccessGuard armed(ApplicationContext context) {
            UndeclaredPrimaryAccessGuard guard = context.getBean(UndeclaredPrimaryAccessGuard.class);
            guard.onApplicationEvent(
                    new ApplicationReadyEvent(new SpringApplication(), new String[0], null, Duration.ZERO));
            return guard;
        }

        @Test
        void onUndeclaredPrimary_WhenThePropertyIsFail_Throws() {
            bound("fail").run(context -> assertThatThrownBy(armed(context)::reportUndeclared)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("PRIMARY connection acquired outside a transaction: declare the caller with "
                            + "@Transactional(readOnly = true) so the read can route to the replica"));
        }

        @Test
        void onUndeclaredPrimary_WhenThePropertyIsCount_Counts() {
            bound("count").run(context -> {
                armed(context).reportUndeclared();

                assertThat(meterRegistry.counter("datasource.primary.undeclared").count()).isEqualTo(1.0);
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "true", "yes", "refuse"})
        void onUndeclaredPrimary_WhenThePropertyNamesNoPolicy_FailsStartup(String value) {
            bound(value).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("undeclaredPrimaryAccessGuard");
            });
        }
    }
}
