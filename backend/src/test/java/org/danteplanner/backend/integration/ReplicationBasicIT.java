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
 * Phase-1 scenario test: basic GTID replication is wired.
 *
 * <p>Pins the narrowest replication contract — a row written to the primary becomes visible
 * through the replica-qualified datasource after {@code replicationControl.awaitCaughtUp()}.
 * This proves the primary/replica pair boots and replicates
 * ({@code CHANGE REPLICATION SOURCE TO … SOURCE_AUTO_POSITION=1}). It deliberately does NOT
 * exercise stop/start (scenarios 2 and 3) and observes the replica through the
 * replica-qualified datasource directly, never through routing (Phase 2, not yet built).</p>
 *
 * <p>No Redis bean is autowired, so this class's context loads independently of the
 * not-yet-built Redis beans.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ReplicationBasicIT extends CausalHarnessSupport {

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @Test
    @DisplayName("A row written to the primary is visible on the replica after awaitCaughtUp")
    void basicReplication_writeOnPrimary_visibleOnReplicaAfterCaughtUp() {
        primaryJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS replication_basic_probe (id INT PRIMARY KEY)");
        primaryJdbcTemplate.update("INSERT INTO replication_basic_probe (id) VALUES (1)");

        replicationControl.awaitCaughtUp();

        Integer count = replicaJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_basic_probe WHERE id = ?", Integer.class, 1);
        assertThat(count).isEqualTo(1);
    }
}
