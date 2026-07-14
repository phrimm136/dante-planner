package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-1 scenario test: {@code stopReplica()} actually halts replication.
 *
 * <p>Pins the halting contract in isolation — once {@code replicationControl.stopReplica()}
 * has run, a row subsequently written to the primary provably cannot reach the replica, so it
 * stays invisible on the replica-qualified datasource with no wait needed (the row can never
 * catch up while replication is stopped). Resume + re-catch-up is scenario 4, deliberately not
 * exercised here. A baseline row asserted visible after {@code awaitCaughtUp()} first proves the
 * replica link is live before it is stopped. A distinct probe table and ids avoid cross-talk with
 * the other integration tests sharing the process-wide container pair.</p>
 *
 * <p>No Redis bean is autowired, so this class's context loads independently of the
 * not-yet-built Redis beans.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ReplicaStopIT extends CausalHarnessSupport {

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @Test
    @DisplayName("After stopReplica, a row written to the primary is not visible on the replica")
    void stopReplica_writeAfterStop_notVisibleOnReplica() {
        primaryJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS replica_stop_probe (id INT PRIMARY KEY)");
        primaryJdbcTemplate.update("INSERT INTO replica_stop_probe (id) VALUES (101)");
        replicationControl.awaitCaughtUp();
        assertThat(countOnReplica(101)).isEqualTo(1);

        replicationControl.stopReplica();

        primaryJdbcTemplate.update("INSERT INTO replica_stop_probe (id) VALUES (102)");
        assertThat(countOnReplica(102)).isZero();
    }

    private int countOnReplica(int id) {
        Integer count = replicaJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replica_stop_probe WHERE id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }
}
