package org.danteplanner.backend.integration;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ReplicationProxyIT extends CausalHarnessSupport {

    private static final long LATENCY_MILLIS = 500;
    private static final long POLL_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
    private static final long VISIBILITY_DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(30);

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @Test
    void replicationLatency_WhenAppliedThenRemoved_DelaysThenReleasesTheReplica() throws IOException {
        assertThat(REPLICATION_PROXY.toxics().getAll()).isEmpty();
        primaryJdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS replication_proxy_probe (marker VARCHAR(36) PRIMARY KEY)");
        replicationControl.awaitCaughtUp();

        toxiproxyControl.applyReplicationLatency(LATENCY_MILLIS);
        long delayedMillis;
        try {
            delayedMillis = millisUntilVisibleOnReplica(UUID.randomUUID().toString());
        } finally {
            toxiproxyControl.removeReplicationLatency();
        }
        replicationControl.awaitCaughtUp();
        long releasedMillis = millisUntilVisibleOnReplica(UUID.randomUUID().toString());

        assertThat(delayedMillis).isGreaterThanOrEqualTo(LATENCY_MILLIS);
        assertThat(releasedMillis).isLessThan(LATENCY_MILLIS);
    }

    @Test
    void replicationProxy_WhenAnotherTestLeftLatency_StartsWithoutToxics() throws IOException {
        assertCleanThenLeaveLatency();
    }

    @Test
    void replicationProxy_WhenThisTestLeavesLatency_NextTestStartsWithoutToxics() throws IOException {
        assertCleanThenLeaveLatency();
    }

    private void assertCleanThenLeaveLatency() throws IOException {
        assertThat(REPLICATION_PROXY.toxics().getAll()).isEmpty();
        toxiproxyControl.applyReplicationLatency(LATENCY_MILLIS);
    }

    private long millisUntilVisibleOnReplica(String marker) {
        primaryJdbcTemplate.update("INSERT INTO replication_proxy_probe (marker) VALUES (?)", marker);
        long committedAt = System.nanoTime();
        while (System.nanoTime() - committedAt < VISIBILITY_DEADLINE_NANOS) {
            Integer count = replicaJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM replication_proxy_probe WHERE marker = ?", Integer.class, marker);
            if (count != null && count == 1) {
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - committedAt);
            }
            LockSupport.parkNanos(POLL_INTERVAL_NANOS);
        }
        throw new IllegalStateException("Row " + marker + " never reached the replica");
    }
}
