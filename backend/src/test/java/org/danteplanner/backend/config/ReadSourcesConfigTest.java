package org.danteplanner.backend.config;

import java.util.List;
import java.util.Properties;

import org.danteplanner.backend.shared.config.PoolLedger;
import org.danteplanner.backend.shared.config.ReadSourceHealthProbe;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.config.ReadSourcesConfig;
import org.danteplanner.backend.shared.config.RoutingDataSourceConfig;
import org.danteplanner.backend.shared.config.TimeoutHierarchy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadSourcesConfigTest {

    private static final String PRIMARY_URL = "jdbc:mysql://127.0.0.1:1/planner";
    private static final String REPLICA_URL = "jdbc:mysql://127.0.0.1:2/planner";
    private static final String REPLICA = "seoul-replica";
    private static final String PROBE_TIMEOUT_MS = "750";
    private static final String REPLICA_FIRST_MARKER = "replicaFirstMarker";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ReadSourcesConfig.class, ReplicaFirstMarker.class)
            .withBean(DataSourceProperties.class, ReadSourcesConfigTest::primaryProperties)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean("primaryPool", HikariDataSource.class, HikariDataSource::new)
            .withPropertyValues(
                    "datasource.routing.enabled=true",
                    "datasource.read-source-probe.timeout=" + PROBE_TIMEOUT_MS + "ms");

    @Configuration(proxyBeanMethods = false)
    static class ReplicaFirstMarker {

        @Bean(REPLICA_FIRST_MARKER)
        @Conditional(RoutingDataSourceConfig.ReadsAReplicaFirst.class)
        String replicaFirstMarker() {
            return REPLICA_FIRST_MARKER;
        }
    }

    private static DataSourceProperties primaryProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(PRIMARY_URL);
        properties.setUsername("primaryUser");
        properties.setPassword("primaryPw");
        return properties;
    }

    private ApplicationContextRunner seoulPod() {
        return runner.withPropertyValues(
                "datasource.read-sources[0].name=" + REPLICA,
                "datasource.read-sources[0].url=" + REPLICA_URL,
                "datasource.read-sources[0].username=replicaUser",
                "datasource.read-sources[0].password=replicaPw",
                "datasource.read-sources[1].name=" + ReadSourceProperties.PRIMARY);
    }

    private ApplicationContextRunner oregonPod() {
        return runner.withPropertyValues(
                "datasource.read-sources[0].name=" + ReadSourceProperties.PRIMARY,
                "datasource.read-sources[1].name=" + REPLICA,
                "datasource.read-sources[1].url=" + REPLICA_URL);
    }

    private static List<String> sourceNames(ReadSourceSelector selector) {
        return selector.sources().stream().map(ReadSourceSelector.Source::name).toList();
    }

    @Test
    void selector_WhenSeoulPod_ReadsTheReplicaFirstAndOnlyThePrimaryIsAuthoritative() {
        seoulPod().run(context -> {
            ReadSourceSelector selector = context.getBean(ReadSourceSelector.class);

            assertThat(sourceNames(selector)).containsExactly(REPLICA, ReadSourceProperties.PRIMARY);
            assertThat(selector.sources()).extracting(ReadSourceSelector.Source::authoritative)
                    .containsExactly(false, true);
            assertThat(selector.sources()).extracting(source -> source.breaker().getName())
                    .containsExactly("read-source-" + REPLICA, "read-source-primary");
        });
    }

    @Test
    void selector_WhenOregonPod_ReadsThePrimaryFirst() {
        oregonPod().run(context -> assertThat(sourceNames(context.getBean(ReadSourceSelector.class)))
                .containsExactly(ReadSourceProperties.PRIMARY, REPLICA));
    }

    @Test
    void sourcePool_WhenSeoulPodReadsTheReplicaFirst_IsTheReplicaPoolSizedFromTheLedger() {
        seoulPod().run(context -> {
            HikariDataSource pool = context.getBean(ReadSourcesConfig.poolBeanName(REPLICA), HikariDataSource.class);

            assertThat(pool.getPoolName()).isEqualTo(REPLICA);
            assertThat(pool.getJdbcUrl()).isEqualTo(REPLICA_URL);
            assertThat(pool.getUsername()).isEqualTo("replicaUser");
            assertThat(pool.getMaximumPoolSize()).isEqualTo(PoolLedger.SEOUL_REPLICA_POOL);
            assertThat(pool.getInitializationFailTimeout()).isNegative();
            assertThat(context.getBean(ReadSourceSelector.class).sources().get(0).dataSource()).isSameAs(pool);
        });
    }

    @Test
    void sourcePool_WhenOregonPodFallsBackToTheReplica_IsASmallPoolOnThePrimaryCredentials() {
        oregonPod().run(context -> {
            HikariDataSource pool = context.getBean(ReadSourcesConfig.poolBeanName(REPLICA), HikariDataSource.class);

            assertThat(pool.getMaximumPoolSize()).isEqualTo(PoolLedger.READ_FALLBACK_POOL);
            assertThat(pool.getUsername()).isEqualTo("primaryUser");
            assertThat(pool.getPassword()).isEqualTo("primaryPw");
            assertThat(pool.getInitializationFailTimeout()).isNegative();
        });
    }

    @Test
    void sourcePool_WhenSeoulPodReadsThePrimaryForAuthority_IsTheLedgerPrimaryReadPoolApartFromTheWritePool() {
        seoulPod().run(context -> {
            HikariDataSource pool = context.getBean(
                    ReadSourcesConfig.poolBeanName(ReadSourceProperties.PRIMARY), HikariDataSource.class);

            assertThat(pool).isNotSameAs(context.getBean("primaryPool"));
            assertThat(pool.getPoolName()).isEqualTo(PoolLedger.PRIMARY_READ_POOL_NAME);
            assertThat(pool.getJdbcUrl()).isEqualTo(PRIMARY_URL);
            assertThat(pool.getUsername()).isEqualTo("primaryUser");
            assertThat(pool.getPassword()).isEqualTo("primaryPw");
            assertThat(pool.getMaximumPoolSize()).isEqualTo(PoolLedger.SEOUL_PRIMARY_READ_POOL);
            assertThat(pool.getInitializationFailTimeout()).isNegative();
            assertThat(context.getBean(ReadSourceSelector.class).authority().dataSource()).isSameAs(pool);
        });
    }

    @Test
    void sourcePool_WhenOregonPodReadsThePrimaryFirst_ReadsThroughThePrimaryPool() {
        oregonPod().run(context -> {
            assertThat(context).doesNotHaveBean(ReadSourcesConfig.poolBeanName(ReadSourceProperties.PRIMARY));
            assertThat(context.getBean(ReadSourceSelector.class).authority().dataSource())
                    .isSameAs(context.getBean("primaryPool"));
        });
    }

    @Test
    void bulkheadCondition_WhenSeoulPod_Matches() {
        seoulPod().run(context -> assertThat(context).hasBean(REPLICA_FIRST_MARKER));
    }

    @Test
    void bulkheadCondition_WhenOregonPod_DoesNotMatch() {
        oregonPod().run(context -> assertThat(context).doesNotHaveBean(REPLICA_FIRST_MARKER));
    }

    @Test
    void probe_WhenBuilt_RunsOnItsOwnSingleThreadScheduler() {
        seoulPod().run(context -> {
            assertThat(context).hasSingleBean(ReadSourceHealthProbe.class);
            ThreadPoolTaskScheduler scheduler = context.getBean(
                    ReadSourcesConfig.PROBE_SCHEDULER, ThreadPoolTaskScheduler.class);
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
        });
    }

    @Test
    void probe_WhenBuilt_ConnectsToEachSourceDirectlyOutsideItsPool() {
        seoulPod().run(context -> {
            List<?> probes = (List<?>) ReflectionTestUtils.getField(
                    context.getBean(ReadSourceHealthProbe.class), "probes");
            List<SimpleDriverDataSource> directs = probes.stream()
                    .map(probe -> (ReadSourceHealthProbe.Target) ReflectionTestUtils.getField(probe, "target"))
                    .map(target -> (SimpleDriverDataSource) target.direct())
                    .toList();

            assertThat(directs).extracting(SimpleDriverDataSource::getUrl).containsExactly(REPLICA_URL, PRIMARY_URL);
            assertThat(directs).extracting(SimpleDriverDataSource::getUsername)
                    .containsExactly("replicaUser", "primaryUser");
            assertThat(directs).allSatisfy(direct -> {
                Properties driverProperties = direct.getConnectionProperties();
                assertThat(driverProperties.getProperty("connectTimeout")).isEqualTo(PROBE_TIMEOUT_MS);
                assertThat(driverProperties.getProperty(TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY))
                        .isEqualTo(PROBE_TIMEOUT_MS);
            });
        });
    }

    @Test
    void context_WhenTheListHasNoPrimary_FailsNamingTheProblem() {
        runner.withPropertyValues(
                        "datasource.read-sources[0].name=" + REPLICA,
                        "datasource.read-sources[0].url=" + REPLICA_URL)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("must hold exactly one entry named \"primary\""));
    }

    @Test
    void context_WhenRoutingDisabled_HoldsNoReadSources() {
        new ApplicationContextRunner()
                .withUserConfiguration(ReadSourcesConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReadSourceSelector.class);
                    assertThat(context).doesNotHaveBean(ReadSourcesConfig.PROBE_SCHEDULER);
                });
    }
}
