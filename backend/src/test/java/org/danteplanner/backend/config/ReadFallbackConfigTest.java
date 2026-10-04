package org.danteplanner.backend.config;

import java.util.Properties;

import org.danteplanner.backend.shared.config.FallbackReplicaProperties;
import org.danteplanner.backend.shared.config.HikariTuningProperties;
import org.danteplanner.backend.shared.config.PrimaryHealthProbe;
import org.danteplanner.backend.shared.config.ReadFallbackConfig;
import org.danteplanner.backend.shared.config.TimeoutHierarchy;

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
import static org.mockito.Mockito.mock;

class ReadFallbackConfigTest {

    private static final String PRIMARY_URL = "jdbc:mysql://127.0.0.1:1/planner";
    private static final String FALLBACK_URL = "jdbc:mysql://127.0.0.1:2/planner";
    private static final String PROBE_TIMEOUT_MS = "750";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ReadFallbackConfig.class)
            .withBean(DataSourceProperties.class, ReadFallbackConfigTest::primaryProperties)
            .withBean(HikariTuningProperties.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean("primaryPool", HikariDataSource.class, () -> mock(HikariDataSource.class));

    private static DataSourceProperties primaryProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(PRIMARY_URL);
        properties.setUsername("primaryUser");
        properties.setPassword("primaryPw");
        return properties;
    }

    private ApplicationContextRunner oregonPod() {
        return runner.withPropertyValues(
                "datasource.routing.enabled=true",
                "datasource.replica.enabled=false",
                FallbackReplicaProperties.URL_PROPERTY + "=" + FALLBACK_URL,
                "datasource.fallback-replica.probe.timeout=" + PROBE_TIMEOUT_MS + "ms");
    }

    @Test
    void context_WhenFallbackReplicaUrlUnset_HoldsNoProbeAndNoProbeScheduler() {
        runner.withPropertyValues("datasource.routing.enabled=true", "datasource.replica.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PrimaryHealthProbe.class);
                    assertThat(context).doesNotHaveBean(ReadFallbackConfig.PRIMARY_PROBE_SCHEDULER);
                });
    }

    @Test
    void context_WhenOregonPodHasAFallbackReplica_HoldsTheProbeOnItsOwnSingleThreadScheduler() {
        oregonPod().run(context -> {
            assertThat(context).hasSingleBean(PrimaryHealthProbe.class);
            ThreadPoolTaskScheduler scheduler = context.getBean(
                    ReadFallbackConfig.PRIMARY_PROBE_SCHEDULER, ThreadPoolTaskScheduler.class);
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
        });
    }

    @Test
    void primaryHealthProbe_WhenBuilt_ConnectsDirectlyToThePrimaryOutsideThePool() {
        oregonPod().run(context -> {
            Object primaryDirect = ReflectionTestUtils.getField(
                    context.getBean(PrimaryHealthProbe.class), "primaryDirect");

            assertThat(primaryDirect)
                    .isInstanceOf(SimpleDriverDataSource.class)
                    .isNotSameAs(context.getBean("primaryPool"));
            SimpleDriverDataSource dataSource = (SimpleDriverDataSource) primaryDirect;
            assertThat(dataSource.getUrl()).isEqualTo(PRIMARY_URL);
            assertThat(dataSource.getUsername()).isEqualTo("primaryUser");
            Properties driverProperties = dataSource.getConnectionProperties();
            assertThat(driverProperties.getProperty("connectTimeout")).isEqualTo(PROBE_TIMEOUT_MS);
            assertThat(driverProperties.getProperty(TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY))
                    .isEqualTo(PROBE_TIMEOUT_MS);
        });
    }
}
