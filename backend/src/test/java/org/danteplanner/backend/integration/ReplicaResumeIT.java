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
 * Phase-1 scenario test: {@code startReplica()} resumes replication so a withheld write catches up.
 *
 * <p>Pins the resume half of the replication round-trip in isolation — the complement to
 * {@link ReplicaStopIT}, which pins the halt. A baseline row is asserted visible after
 * {@code awaitCaughtUp()} to prove the link is live; replication is then stopped and a second row
 * written to the primary while it is stopped, which must stay invisible on the replica; finally
 * {@code startReplica()} + {@code awaitCaughtUp()} must make that withheld row observable on the
 * replica-qualified datasource. Catch-up is awaited through the GTID status condition, never a
 * sleep (INV4). A distinct probe table and ids avoid cross-talk with the other integration tests
 * sharing the process-wide container pair.</p>
 *
 * <p>No Redis bean is autowired, so this class's context loads independently of the
 * not-yet-built Redis beans.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ReplicaResumeIT extends CausalHarnessSupport {

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @Test
    @DisplayName("After startReplica + awaitCaughtUp, a row written while stopped becomes visible on the replica")
    void startReplica_writeWithheldWhileStopped_visibleAfterResumeAndCaughtUp() {
        primaryJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS replica_resume_probe (id INT PRIMARY KEY)");
        primaryJdbcTemplate.update("INSERT INTO replica_resume_probe (id) VALUES (201)");
        replicationControl.awaitCaughtUp();
        assertThat(countOnReplica(201)).isEqualTo(1);

        replicationControl.stopReplica();
        primaryJdbcTemplate.update("INSERT INTO replica_resume_probe (id) VALUES (202)");
        assertThat(countOnReplica(202)).isZero();

        replicationControl.startReplica();
        replicationControl.awaitCaughtUp();
        assertThat(countOnReplica(202)).isEqualTo(1);
    }

    private int countOnReplica(int id) {
        Integer count = replicaJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replica_resume_probe WHERE id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }
}
