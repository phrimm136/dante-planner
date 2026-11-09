package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.config.PoolLedger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot binds {@code hikaricp_*} meters onto {@code DataSource} beans only, tagged with each pool's
 * name, so every routing pool has to be a bean to appear at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class MetricsRegistrationIT extends SharedRoutingSupport {

    private static final String CONNECTIONS_MAX = "hikaricp.connections.max";
    private static final String POOL_TAG = "pool";

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void connectionsMax_WhenBooted_IsExposedForExactlyTheRoutingPools() {
        assertThat(meterRegistry.find(CONNECTIONS_MAX).gauges())
                .extracting(gauge -> gauge.getId().getTag(POOL_TAG))
                .containsExactlyInAnyOrder(PoolLedger.PRIMARY_POOL_NAME,
                        OregonContext.REPLICA_SOURCE,
                        PoolLedger.PRIMARY_READ_POOL_NAME);
    }

    @Test
    void connectionsMax_WhenTaggedPrimary_ReportsTheLedgerSize() {
        assertThat(meterRegistry.get(CONNECTIONS_MAX)
                .tags(Tags.of(POOL_TAG, PoolLedger.PRIMARY_POOL_NAME))
                .gauge()
                .value())
                .isEqualTo(PoolLedger.SEOUL_PRIMARY_POOL);
    }
}
